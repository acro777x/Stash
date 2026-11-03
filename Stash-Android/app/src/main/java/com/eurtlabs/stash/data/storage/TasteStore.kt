package com.eurtlabs.stash.data.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TasteStore {
    private const val PREFS_NAME = "stash_user_taste"
    private const val KEY_LAST_VIDEO_ID = "last_video_id"
    private const val KEY_LAST_TITLE = "last_title"
    private const val KEY_LAST_ARTIST = "last_artist"
    private const val KEY_DETECTED_MOOD = "detected_mood"

    private var prefs: SharedPreferences? = null

    private val _lastVideoId = MutableStateFlow<String?>(null)
    val lastVideoId: StateFlow<String?> = _lastVideoId.asStateFlow()

    private val _lastArtist = MutableStateFlow<String?>(null)
    val lastArtist: StateFlow<String?> = _lastArtist.asStateFlow()

    private val _lastTitle = MutableStateFlow<String?>(null)
    val lastTitle: StateFlow<String?> = _lastTitle.asStateFlow()

    private val _detectedMood = MutableStateFlow("For You")
    val detectedMood: StateFlow<String> = _detectedMood.asStateFlow()

    fun initialize(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _lastVideoId.value = p.getString(KEY_LAST_VIDEO_ID, null)
        _lastTitle.value = p.getString(KEY_LAST_TITLE, null)
        _lastArtist.value = p.getString(KEY_LAST_ARTIST, null)
        _detectedMood.value = p.getString(KEY_DETECTED_MOOD, "For You") ?: "For You"
    }

    fun recordPlay(videoId: String, title: String, artist: String) {
        val mood = inferMood(title, artist)
        _lastVideoId.value = videoId
        _lastTitle.value = title
        _lastArtist.value = artist
        _detectedMood.value = mood

        prefs?.edit()?.apply {
            putString(KEY_LAST_VIDEO_ID, videoId)
            putString(KEY_LAST_TITLE, title)
            putString(KEY_LAST_ARTIST, artist)
            putString(KEY_DETECTED_MOOD, mood)
            apply()
        }
    }

    private fun inferMood(title: String, artist: String): String {
        val lowerText = "$title $artist".lowercase()

        // Haryanvi Regional
        val haryanviKeywords = listOf("dhanda", "nyoliwala", "masoom", "gulzaar", "chhaniwala", "haryanvi", "desirock", "bintu pabra", "sumit parta", "renuka", "panwar", "amit saini", "sapna choudhary", "zigane", "gaddafi", "raavan")
        if (haryanviKeywords.any { lowerText.contains(it) }) return "Haryanvi Hits"

        // Romantic Hindi / Bollywood Love
        val romanticKeywords = listOf("arijit", "jubin", "atif", "shreya", "ghoshal", "love", "romantic", "dil", "tum hi", "ishq", "sanam", "raabta", "humsafar", "kasam", "mohabbat", "deewana", "dard", "saajan")
        if (romanticKeywords.any { lowerText.contains(it) }) return "Romantic Hindi"

        // Punjabi Hip-hop / Beats
        val punjabiKeywords = listOf("karan aujla", "shubh", "sidhu", "moose", "ap dhillon", "diljit", "dosanjh", "amrit maan", "punjabi", "cheema", "jass manak", "hardy sandhu", "b praak", "jaani", "honey 3.0")
        if (punjabiKeywords.any { lowerText.contains(it) }) return "Punjabi Beats"

        // Bollywood Hits
        val bollywoodKeywords = listOf("bollywood", "badshah", "honey singh", "pritam", "neha kakkar", "sonu nigam", "alka yagnik", "kumar sanu", "udit narayan", "t-series", "armaan malik")
        if (bollywoodKeywords.any { lowerText.contains(it) }) return "Bollywood Hits"

        // Hollywood / Global Pop & Rap
        val hollywoodKeywords = listOf("weeknd", "taylor swift", "drake", "billie eilish", "ed sheeran", "bruno mars", "dua lipa", "post malone", "eminem", "travis scott", "kendrick", "justin bieber", "rihanna", "coldplay", "imagine dragons")
        if (hollywoodKeywords.any { lowerText.contains(it) }) return "Hollywood Pop"

        // Lo-Fi & Chill
        val chillKeywords = listOf("lofi", "lo-fi", "chill", "ambient", "sleep", "acoustic", "rain")
        if (chillKeywords.any { lowerText.contains(it) }) return "Chill & Lo-Fi"

        return "For You"
    }
}
