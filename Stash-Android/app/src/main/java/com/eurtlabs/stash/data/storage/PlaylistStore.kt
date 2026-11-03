package com.eurtlabs.stash.data.storage

import android.content.Context
import android.util.Log
import com.eurtlabs.stash.data.model.Platform
import com.eurtlabs.stash.data.model.TrackInfo
import com.eurtlabs.stash.data.model.UserPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent, privacy-first local storage for Liked Songs and Custom Playlists.
 * Stores data safely to resilient JSON on physical storage so user playlists are never lost on update/reinstall.
 */
object PlaylistStore {

    private const val TAG = "PlaylistStore"
    private const val PLAYLIST_FILE_NAME = "stash_playlists_v1.json"

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    private val _likedTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val likedTracks: StateFlow<List<TrackInfo>> = _likedTracks.asStateFlow()

    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: StateFlow<Set<String>> = _likedTrackIds.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val userPlaylists: StateFlow<List<UserPlaylist>> = _userPlaylists.asStateFlow()

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        loadFromDisk()
    }

    fun isLiked(trackId: String): Boolean {
        return _likedTrackIds.value.contains(trackId)
    }

    fun toggleLike(track: TrackInfo): Boolean {
        val currentLiked = _likedTracks.value.toMutableList()
        val existingIndex = currentLiked.indexOfFirst { it.id == track.id }
        val nowLiked: Boolean

        if (existingIndex >= 0) {
            currentLiked.removeAt(existingIndex)
            nowLiked = false
        } else {
            currentLiked.add(0, track)
            nowLiked = true
        }

        _likedTracks.value = currentLiked
        _likedTrackIds.value = currentLiked.map { it.id }.toSet()
        saveToDisk()
        return nowLiked
    }

    fun createPlaylist(name: String, description: String = ""): UserPlaylist {
        val newPlaylist = UserPlaylist(
            name = name.trim(),
            description = description.trim(),
            tracks = emptyList()
        )
        val list = _userPlaylists.value.toMutableList()
        list.add(0, newPlaylist)
        _userPlaylists.value = list
        saveToDisk()
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        val list = _userPlaylists.value.toMutableList()
        list.removeAll { it.id == playlistId }
        _userPlaylists.value = list
        saveToDisk()
    }

    fun addTrackToPlaylist(playlistId: String, track: TrackInfo) {
        val list = _userPlaylists.value.toMutableList()
        val index = list.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            val playlist = list[index]
            if (playlist.tracks.none { it.id == track.id }) {
                val updatedTracks = listOf(track) + playlist.tracks
                val coverArt = playlist.coverArtUrl ?: track.albumArtUrl
                list[index] = playlist.copy(tracks = updatedTracks, coverArtUrl = coverArt)
                _userPlaylists.value = list
                saveToDisk()
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val list = _userPlaylists.value.toMutableList()
        val index = list.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            val playlist = list[index]
            val updatedTracks = playlist.tracks.filterNot { it.id == trackId }
            list[index] = playlist.copy(tracks = updatedTracks)
            _userPlaylists.value = list
            saveToDisk()
        }
    }

    private fun getStorageFile(): File? {
        val ctx = appContext ?: return null
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(dir, PLAYLIST_FILE_NAME)
    }

    private fun saveToDisk() {
        scope.launch {
            mutex.withLock {
                try {
                    val file = getStorageFile() ?: return@withLock
                    val root = JSONObject()

                    // Liked Tracks
                    val likedArray = JSONArray()
                    _likedTracks.value.forEach { t ->
                        likedArray.put(trackToJson(t))
                    }
                    root.put("liked_tracks", likedArray)

                    // Custom Playlists
                    val playlistsArray = JSONArray()
                    _userPlaylists.value.forEach { p ->
                        val pObj = JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("description", p.description)
                            put("coverArtUrl", p.coverArtUrl ?: "")
                            put("createdAt", p.createdAt)
                            val tArray = JSONArray()
                            p.tracks.forEach { t -> tArray.put(trackToJson(t)) }
                            put("tracks", tArray)
                        }
                        playlistsArray.put(pObj)
                    }
                    root.put("playlists", playlistsArray)

                    file.writeText(root.toString(2), Charsets.UTF_8)
                    Log.d(TAG, "Playlists and Liked Songs saved safely to disk.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save playlists to disk", e)
                }
            }
        }
    }

    private fun loadFromDisk() {
        scope.launch {
            mutex.withLock {
                try {
                    val file = getStorageFile() ?: return@withLock
                    if (!file.exists()) return@withLock

                    val content = file.readText(Charsets.UTF_8)
                    if (content.isBlank()) return@withLock

                    val root = JSONObject(content)

                    // Liked tracks
                    val likedArray = root.optJSONArray("liked_tracks") ?: JSONArray()
                    val loadedLiked = mutableListOf<TrackInfo>()
                    for (i in 0 until likedArray.length()) {
                        val obj = likedArray.getJSONObject(i)
                        jsonToTrack(obj)?.let { loadedLiked.add(it) }
                    }
                    _likedTracks.value = loadedLiked
                    _likedTrackIds.value = loadedLiked.map { it.id }.toSet()

                    // Playlists
                    val playlistsArray = root.optJSONArray("playlists") ?: JSONArray()
                    val loadedPlaylists = mutableListOf<UserPlaylist>()
                    for (i in 0 until playlistsArray.length()) {
                        val pObj = playlistsArray.getJSONObject(i)
                        val id = pObj.optString("id", "")
                        val name = pObj.optString("name", "Untitled")
                        val description = pObj.optString("description", "")
                        val coverArtUrl = pObj.optString("coverArtUrl", "").takeIf { it.isNotBlank() }
                        val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())

                        val tArray = pObj.optJSONArray("tracks") ?: JSONArray()
                        val pTracks = mutableListOf<TrackInfo>()
                        for (j in 0 until tArray.length()) {
                            val tObj = tArray.getJSONObject(j)
                            jsonToTrack(tObj)?.let { pTracks.add(it) }
                        }

                        loadedPlaylists.add(
                            UserPlaylist(
                                id = id,
                                name = name,
                                description = description,
                                coverArtUrl = coverArtUrl,
                                tracks = pTracks,
                                createdAt = createdAt
                            )
                        )
                    }
                    _userPlaylists.value = loadedPlaylists
                    Log.d(TAG, "Loaded ${loadedLiked.size} liked tracks and ${loadedPlaylists.size} playlists.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load playlists from disk", e)
                }
            }
        }
    }

    private fun trackToJson(track: TrackInfo): JSONObject {
        return JSONObject().apply {
            put("id", track.id)
            put("title", track.title)
            val artistsArray = JSONArray()
            track.artists.forEach { artistsArray.put(it) }
            put("artists", artistsArray)
            put("album", track.album ?: "")
            put("durationMs", track.durationMs)
            put("albumArtUrl", track.albumArtUrl ?: "")
            put("sourceUrl", track.sourceUrl)
            put("youtubeUrl", track.youtubeUrl ?: "")
            put("releaseYear", track.releaseYear ?: "")
            put("genre", track.genre ?: "")
            put("safeFileName", track.safeFileName)
            put("playlistName", track.playlistName ?: "")
        }
    }

    private fun jsonToTrack(obj: JSONObject): TrackInfo? {
        return try {
            val id = obj.optString("id")
            val title = obj.optString("title")
            val artistsArray = obj.optJSONArray("artists") ?: JSONArray()
            val artists = mutableListOf<String>()
            for (i in 0 until artistsArray.length()) {
                artists.add(artistsArray.getString(i))
            }
            TrackInfo(
                id = id,
                title = title,
                artists = artists.ifEmpty { listOf("Unknown Artist") },
                album = obj.optString("album", "").takeIf { it.isNotBlank() },
                durationMs = obj.optLong("durationMs", 0L),
                albumArtUrl = obj.optString("albumArtUrl", "").takeIf { it.isNotBlank() },
                source = Platform.YOUTUBE,
                sourceUrl = obj.optString("sourceUrl", "https://www.youtube.com/watch?v=$id"),
                youtubeUrl = obj.optString("youtubeUrl", "https://www.youtube.com/watch?v=$id"),
                releaseYear = obj.optString("releaseYear", "").takeIf { it.isNotBlank() },
                genre = obj.optString("genre", "").takeIf { it.isNotBlank() },
                safeFileName = obj.optString("safeFileName", "$title.mp3"),
                playlistName = obj.optString("playlistName", "").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }
}
