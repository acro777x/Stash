package com.eurtlabs.stash.data.lyrics

import android.util.Log
import android.util.LruCache
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
        durationMs: Long = 0L
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSearchTerm(trackTitle)
        val cleanArtist = cleanArtistTerm(artistName)
        val cacheKey = "${cleanTitle.lowercase()}_${cleanArtist.lowercase()}"

        // Check 0ms in-memory cache
        lyricsMemoryCache.get(cacheKey)?.let { cached ->
            Log.d(TAG, "Instant 0ms Lyrics Cache Hit: $cacheKey")
            return@withContext cached
        }

        // Tier 1: LRCLIB exact match
        val lrclibResult = fetchFromLrclib(cleanTitle, cleanArtist, durationMs)
        if (lrclibResult != null && (lrclibResult.syncedLyrics.isNotEmpty() || !lrclibResult.plainLyrics.isNullOrBlank())) {
            lyricsMemoryCache.put(cacheKey, lrclibResult)
            return@withContext lrclibResult
        }

        // Tier 2: LRCLIB fuzzy query search
        val lrclibSearchResult = searchLrclib(cleanTitle, cleanArtist)
        if (lrclibSearchResult != null && (lrclibSearchResult.syncedLyrics.isNotEmpty() || !lrclibSearchResult.plainLyrics.isNullOrBlank())) {
            lyricsMemoryCache.put(cacheKey, lrclibSearchResult)
            return@withContext lrclibSearchResult
        }

        // Tier 3: Netease Cloud Music Open Synchronized Lyrics
        val neteaseResult = fetchFromNetease(cleanTitle, cleanArtist)
        if (neteaseResult != null && (neteaseResult.syncedLyrics.isNotEmpty() || !neteaseResult.plainLyrics.isNullOrBlank())) {
            lyricsMemoryCache.put(cacheKey, neteaseResult)
            return@withContext neteaseResult
        }

        // Tier 4: BetterLyrics fallback
        val betterLyricsResult = fetchFromBetterLyrics(cleanTitle, cleanArtist)
        if (betterLyricsResult != null) {
            lyricsMemoryCache.put(cacheKey, betterLyricsResult)
            return@withContext betterLyricsResult
        }

        // Tier 5: Title-only search fallback (if artist was noisy/unknown)
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

    private fun cleanSearchTerm(input: String): String {
        return input
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(Official Music Video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Official Video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Official Audio.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Visualizer.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Visualiser.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Audio.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Lyric Video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Lyrics.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Remastered.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Live.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(4K.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(HD.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Explicit.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\|.*"), "")
            .replace(Regex("ft\\..*|feat\\..*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun cleanArtistTerm(input: String): String {
        return input
            .replace(" - Topic", "", ignoreCase = true)
            .replace("VEVO", "", ignoreCase = true)
            .substringBefore(",")
            .substringBefore("&")
            .trim()
    }
}