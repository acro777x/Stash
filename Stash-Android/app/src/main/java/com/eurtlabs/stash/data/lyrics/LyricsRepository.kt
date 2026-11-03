package com.eurtlabs.stash.data.lyrics

import android.util.Log
import android.util.LruCache
import com.eurtlabs.stash.data.downloader.InnerTubeMusicRepository
import com.eurtlabs.stash.data.model.LyricLine
import com.eurtlabs.stash.data.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object LyricsRepository {

    private const val TAG = "LyricsRepository"

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(4, 30, TimeUnit.SECONDS))
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    // 100-entry in-memory cache for instant 0ms lyric lookups
    private val lyricsMemoryCache = LruCache<String, LyricsResult>(100)

    private val LRC_TIMESTAMP_PATTERN = Pattern.compile("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")

    suspend fun fetchLyrics(
        trackTitle: String,
        artistName: String,
        durationMs: Long = 0L,
        videoId: String? = null
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSearchTerm(trackTitle)
        val cleanArtist = cleanArtistTerm(artistName)
        val cacheKey = "${cleanTitle.lowercase()}_${cleanArtist.lowercase()}"

        // Check 0ms in-memory cache first before any network roundtrips
        lyricsMemoryCache.get(cacheKey)?.let { cached ->
            Log.d(TAG, "Instant 0ms Lyrics Cache Hit: $cacheKey")
            return@withContext cached
        }

        // Tier 0: Official YouTube Music Lyrics directly from InnerTube browse endpoint
        if (!videoId.isNullOrBlank()) {
            try {
                val ytLyrics = InnerTubeMusicRepository.getYouTubeMusicLyrics(videoId)
                if (!ytLyrics.isNullOrBlank()) {
                    val lrclib = fetchFromLrclib(cleanTitle, cleanArtist, durationMs)
                    val result = LyricsResult(
                        trackName = trackTitle,
                        artistName = artistName,
                        plainLyrics = ytLyrics,
                        syncedLyrics = lrclib?.syncedLyrics ?: emptyList(),
                        source = if (lrclib?.syncedLyrics?.isNotEmpty() == true) "Synchronized • Official" else "YouTube Music (Official)"
                    )
                    lyricsMemoryCache.put(cacheKey, result)
                    return@withContext result
                }
            } catch (e: Exception) {
                // Fallback to following tiers
            }
        }

        // Tier 1: LRCLIB exact match
        val lrclibResult = fetchFromLrclib(cleanTitle, cleanArtist, durationMs)
        if (lrclibResult != null && (lrclibResult.syncedLyrics.isNotEmpty() || !lrclibResult.plainLyrics.isNullOrBlank())) {
            lyricsMemoryCache.put(cacheKey, lrclibResult)
            return@withContext lrclibResult
        }

        // Tier 2: KuGou Synced Lyrics (Ported from Fiend / ViMusic)
        val kugouResult = fetchFromKuGou(cleanTitle, cleanArtist, durationMs)
        if (kugouResult != null && (kugouResult.syncedLyrics.isNotEmpty() || !kugouResult.plainLyrics.isNullOrBlank())) {
            lyricsMemoryCache.put(cacheKey, kugouResult)
            return@withContext kugouResult
        }

        // Tier 3: LRCLIB fuzzy query search
        val lrclibSearchResult = searchLrclib(cleanTitle, cleanArtist)
        if (lrclibSearchResult != null && (lrclibSearchResult.syncedLyrics.isNotEmpty() || !lrclibSearchResult.plainLyrics.isNullOrBlank())) {
            lyricsMemoryCache.put(cacheKey, lrclibSearchResult)
            return@withContext lrclibSearchResult
        }

        // Tier 4: Netease Cloud Music Open Synchronized Lyrics
        val neteaseResult = fetchFromNetease(cleanTitle, cleanArtist)
        if (neteaseResult != null && (neteaseResult.syncedLyrics.isNotEmpty() || !neteaseResult.plainLyrics.isNullOrBlank())) {
            lyricsMemoryCache.put(cacheKey, neteaseResult)
            return@withContext neteaseResult
        }

        // Tier 5: BetterLyrics fallback
        val betterLyricsResult = fetchFromBetterLyrics(cleanTitle, cleanArtist)
        if (betterLyricsResult != null) {
            lyricsMemoryCache.put(cacheKey, betterLyricsResult)
            return@withContext betterLyricsResult
        }

        // Tier 6: Title-only search fallback (if artist was noisy/unknown)
        if (cleanArtist.isNotBlank() && cleanArtist != "Unknown") {
            val titleOnlyResult = searchLrclib(cleanTitle, "")
            if (titleOnlyResult != null && (titleOnlyResult.syncedLyrics.isNotEmpty() || !titleOnlyResult.plainLyrics.isNullOrBlank())) {
                lyricsMemoryCache.put(cacheKey, titleOnlyResult)
                return@withContext titleOnlyResult
            }
        }

        return@withContext null
    }

    private fun fetchFromLrclib(title: String, artist: String, durationMs: Long): LyricsResult? {
        return try {
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val durationSec = if (durationMs > 0) "&duration=${durationMs / 1000}" else ""
            val url = "https://lrclib.net/api/get?track_name=$encTitle&artist_name=$encArtist$durationSec"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "StashMusic/2.1.0 (Android; Open-Source Media App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)

                val syncedLrc = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
                val plainLrc = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }

                val parsedLines = syncedLrc?.let { parseLrc(it) } ?: emptyList()

                LyricsResult(
                    trackName = json.optString("trackName", title),
                    artistName = json.optString("artistName", artist),
                    plainLyrics = plainLrc ?: syncedLrc,
                    syncedLyrics = parsedLines,
                    source = "LRCLIB (Open Database)"
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "LRCLIB get failed: ${e.message}")
            null
        }
    }

    private fun searchLrclib(title: String, artist: String): LyricsResult? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = "https://lrclib.net/api/search?q=$query"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "StashMusic/2.1.0 (Android; Open-Source Media App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val array = JSONArray(body)
                if (array.length() == 0) return null

                // Pick first result with synced lyrics or plain lyrics
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val syncedLrc = obj.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
                    val plainLrc = obj.optString("plainLyrics", "").takeIf { it.isNotBlank() }

                    if (syncedLrc != null || plainLrc != null) {
                        val parsedLines = syncedLrc?.let { parseLrc(it) } ?: emptyList()
                        return LyricsResult(
                            trackName = obj.optString("trackName", title),
                            artistName = obj.optString("artistName", artist),
                            plainLyrics = plainLrc ?: syncedLrc,
                            syncedLyrics = parsedLines,
                            source = "LRCLIB Search"
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "LRCLIB search failed: ${e.message}")
            null
        }
    }

    /**
     * Netease Cloud Music Open Synchronized Lyrics API (Privacy-Safe Public Search).
     */
    private fun fetchFromNetease(title: String, artist: String): LyricsResult? {
        return try {
            val queryText = if (artist.isNotBlank()) "$title $artist" else title
            val encQuery = URLEncoder.encode(queryText, "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get/web?csrf_token=&hlpretag=&hlposttag=&s=$encQuery&type=1&offset=0&total=true&limit=1"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://music.163.com")
                .build()

            val songId = client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val searchBody = resp.body?.string() ?: return null
                val root = JSONObject(searchBody)
                val resultObj = root.optJSONObject("result") ?: return null
                val songs = resultObj.optJSONArray("songs") ?: return null
                if (songs.length() == 0) return null
                songs.getJSONObject(0).optLong("id", 0L)
            }

            if (songId <= 0L) return null

            val lyricUrl = "https://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1"
            val lyricReq = Request.Builder()
                .url(lyricUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://music.163.com")
                .build()

            client.newCall(lyricReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val lyricBody = resp.body?.string() ?: return null
                val root = JSONObject(lyricBody)
                val lrcObj = root.optJSONObject("lrc") ?: return null
                val rawLyric = lrcObj.optString("lyric", "").trim()
                if (rawLyric.isBlank()) return null

                val parsedLines = parseLrc(rawLyric)
                if (parsedLines.isNotEmpty()) {
                    LyricsResult(
                        trackName = title,
                        artistName = artist,
                        plainLyrics = parsedLines.joinToString("\n") { it.text },
                        syncedLyrics = parsedLines,
                        source = "Netease Cloud Lyrics"
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Netease lyrics fetch failed: ${e.message}")
            null
        }
    }

    private fun fetchFromBetterLyrics(title: String, artist: String): LyricsResult? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = "https://lyrics-api.boidu.dev/getLyrics?title=$query"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "StashMusic/2.1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)

                val linesArray = json.optJSONArray("lines")
                if (linesArray != null && linesArray.length() > 0) {
                    val parsed = mutableListOf<LyricLine>()
                    for (i in 0 until linesArray.length()) {
                        val lineObj = linesArray.getJSONObject(i)
                        val timeMs = lineObj.optLong("startTimeMs", 0L)
                        val text = lineObj.optString("words", "").trim()
                        if (text.isNotBlank()) {
                            parsed.add(LyricLine(timeMs, text))
                        }
                    }
                    if (parsed.isNotEmpty()) {
                        return LyricsResult(
                            trackName = title,
                            artistName = artist,
                            plainLyrics = parsed.joinToString("\n") { it.text },
                            syncedLyrics = parsed.sortedBy { it.timestampMs },
                            source = "BetterLyrics"
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "BetterLyrics fetch failed: ${e.message}")
            null
        }
    }

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val rawLines = lrcContent.lines()

        for (raw in rawLines) {
            val trimmed = raw.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[offset:")) {
                continue
            }

            val matcher = LRC_TIMESTAMP_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msGroup = matcher.group(3)
                val ms = when (msGroup?.length) {
                    1 -> msGroup.toLongOrNull()?.times(100) ?: 0L
                    2 -> msGroup.toLongOrNull()?.times(10) ?: 0L
                    3 -> msGroup.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timestampMs = (min * 60 * 1000) + (sec * 1000) + ms
                val text = matcher.group(4)?.trim() ?: ""

                if (text.isNotBlank()) {
                    lines.add(LyricLine(timestampMs, text))
                }
            }
        }

        return lines.sortedBy { it.timestampMs }
    }

    fun saveLrcCompanionFile(audioFilePath: String, rawLyrics: String) {
        try {
            val audioFile = File(audioFilePath)
            if (audioFile.exists()) {
                val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                lrcFile.writeText(rawLyrics, Charsets.UTF_8)
                Log.d(TAG, "Saved companion LRC file to: ${lrcFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write .lrc companion file: ${e.message}", e)
        }
    }

    /**
     * KuGou Lyrics Provider (Ported from Fiend / ViMusic)
     * Massive catalogue for Indian, Asian, and international synchronized timecodes.
     */
    private fun fetchFromKuGou(title: String, artist: String, durationMs: Long): LyricsResult? {
        return try {
            val query = "$title - $artist"
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=$encQuery"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "KuGouMusic/9108 (Android)")
                .build()

            var songHash: String? = null
            client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return null
                val info = data.optJSONArray("info") ?: return null
                val targetDurationSec = (durationMs / 1000).toInt()

                for (i in 0 until info.length()) {
                    val item = info.getJSONObject(i)
                    val duration = item.optInt("duration", 0)
                    val hash = item.optString("hash", "")
                    if (hash.isNotBlank()) {
                        if (targetDurationSec <= 0 || Math.abs(duration - targetDurationSec) <= 12) {
                            songHash = hash
                            break
                        }
                    }
                }
                if (songHash == null && info.length() > 0) {
                    songHash = info.getJSONObject(0).optString("hash", "")
                }
            }

            if (songHash.isNullOrBlank()) return null

            // Search lyrics by hash
            val lrcSearchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$songHash"
            val lrcSearchReq = Request.Builder().url(lrcSearchUrl).build()

            var lyricId: Long = 0
            var accessKey: String? = null

            client.newCall(lrcSearchReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates") ?: return null
                if (candidates.length() > 0) {
                    val cand = candidates.getJSONObject(0)
                    lyricId = cand.optLong("id", 0L)
                    accessKey = cand.optString("accesskey", "")
                }
            }

            if (lyricId == 0L || accessKey.isNullOrBlank()) return null

            // Download lyrics
            val downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$lyricId&accesskey=$accessKey&fmt=lrc&charset=utf8"
            val downloadReq = Request.Builder().url(downloadUrl).build()

            var lrcContent: String? = null
            client.newCall(downloadReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val base64Content = json.optString("content", "")
                if (base64Content.isNotBlank()) {
                    val decodedBytes = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
                    lrcContent = String(decodedBytes, Charsets.UTF_8)
                }
            }

            val lrc = lrcContent ?: return null
            val parsedLines = parseLrc(lrc)
            if (parsedLines.isEmpty() && lrc.isBlank()) return null

            LyricsResult(
                trackName = title,
                artistName = artist,
                plainLyrics = lrc.lines().filter { !it.startsWith("[") }.joinToString("\n").ifBlank { lrc },
                syncedLyrics = parsedLines,
                source = "KuGou (Synchronized)"
            )
        } catch (e: Exception) {
            Log.d(TAG, "KuGou lyrics fetch error: ${e.message}")
            null
        }
    }

    private val titleCleanupPatterns = listOf(
        Regex("""\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\s*【.*?】"""),
        Regex("""\s*\|.*$"""),
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
    )

    private val artistSeparators = listOf(" & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ")

    private fun cleanSearchTerm(input: String): String {
        var cleaned = input.trim()
        for (pattern in titleCleanupPatterns) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.trim()
    }

    private fun cleanArtistTerm(input: String): String {
        var cleaned = input
            .replace(" - Topic", "", ignoreCase = true)
            .replace("VEVO", "", ignoreCase = true)
            .trim()
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(Regex(Regex.escape(separator), RegexOption.IGNORE_CASE), limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }
}