package com.soundwave.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit

/**
 * Audio source: Piped API (https://github.com/TeamPiped/Piped)
 * A free, open-source YouTube frontend with a public REST API.
 * No API key needed. Returns YouTube search results and audio stream URLs
 * that ExoPlayer can play directly — background playback works because
 * ExoPlayer holds the audio, not a web iframe.
 *
 * Primary instance: pipedapi.kavin.rocks
 * If this goes down, any public Piped instance works as a drop-in replacement.
 * List of instances: https://github.com/TeamPiped/Piped/wiki/Instances
 */
object YoutubeApi {
    private const val BASE = "https://pipedapi.kavin.rocks"

    // Longer timeout than default — Piped instances can be slow occasionally
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun getJson(url: String): JSONObject = suspendCancellableCoroutine { cont ->
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val body = response.body?.string() ?: "{}"
                    if (cont.isActive) cont.resume(JSONObject(body))
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

    private suspend fun getJsonArray(url: String): JSONArray = suspendCancellableCoroutine { cont ->
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val body = response.body?.string() ?: "[]"
                    if (cont.isActive) cont.resume(JSONArray(body))
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

    // ── Search ────────────────────────────────────────────────────────────────

    suspend fun search(query: String): List<Song> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // filter=music_songs restricts results to music tracks only (not videos, playlists, etc.)
        val json = getJson("$BASE/search?q=$encoded&filter=music_songs")
        val items = json.optJSONArray("items") ?: JSONArray()
        val songs = mutableListOf<Song>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val song = parseSearchItem(o) ?: continue
            songs.add(song)
        }
        return songs
    }

    private fun parseSearchItem(o: JSONObject): Song? {
        val url = o.optString("url", "") // e.g. "/watch?v=VIDEO_ID"
        val videoId = extractVideoId(url) ?: return null
        val title = o.optString("title", "")
        val uploaderName = o.optString("uploaderName", "Unknown Artist")
        val duration = o.optInt("duration", 0)
        val thumbnail = o.optString("thumbnail", null)

        return Song(
            id = videoId,
            title = title,
            artist = uploaderName,
            album = null,
            thumbnail = thumbnail,
            durationSec = duration,
            streamUrl = null // fetched fresh right before playing via getStreamUrl()
        )
    }

    // ── Stream URL ────────────────────────────────────────────────────────────

    /**
     * Fetches a fresh, playable audio stream URL for a given YouTube video ID.
     * Called right before every play — Piped's stream URLs expire quickly so
     * we never cache them. Returns the best audio-only stream (highest bitrate).
     */
    suspend fun getStreamUrl(videoId: String): String? {
        return try {
            val json = getJson("$BASE/streams/$videoId")
            val audioStreams = json.optJSONArray("audioStreams") ?: return null

            // Pick best quality audio-only stream
            // Piped returns streams with quality like "128 kbps", "160 kbps" etc.
            var bestUrl: String? = null
            var bestBitrate = -1

            for (i in 0 until audioStreams.length()) {
                val stream = audioStreams.optJSONObject(i) ?: continue
                val mimeType = stream.optString("mimeType", "")
                // Prefer m4a/mp4 audio (most compatible with ExoPlayer)
                // but accept webm/opus too
                if (!mimeType.contains("audio")) continue
                val bitrate = stream.optInt("bitrate", 0)
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate
                    bestUrl = stream.optString("url", null)
                }
            }
            bestUrl
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets fresh song metadata + stream URL by video ID.
     * Used by AppViewModel.playSong() to refresh before playback.
     */
    suspend fun getSongById(videoId: String): Song? {
        return try {
            val json = getJson("$BASE/streams/$videoId")
            val title = json.optString("title", "")
            val uploader = json.optString("uploader", "Unknown Artist")
            val duration = json.optInt("duration", 0)
            val thumbnail = json.optString("thumbnailUrl", null)

            val streamUrl = run {
                val audioStreams = json.optJSONArray("audioStreams") ?: return null
                var bestUrl: String? = null
                var bestBitrate = -1
                for (i in 0 until audioStreams.length()) {
                    val stream = audioStreams.optJSONObject(i) ?: continue
                    if (!stream.optString("mimeType", "").contains("audio")) continue
                    val bitrate = stream.optInt("bitrate", 0)
                    if (bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        bestUrl = stream.optString("url", null)
                    }
                }
                bestUrl
            }

            Song(
                id = videoId,
                title = title,
                artist = uploader,
                album = null,
                thumbnail = thumbnail,
                durationSec = duration,
                streamUrl = streamUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Album / Playlist search ───────────────────────────────────────────────

    data class AlbumResult(val id: String, val name: String, val artist: String, val thumbnail: String?)

    /**
     * Searches for YouTube Music playlists (albums/collections).
     * filter=music_playlists returns official album playlists from YouTube Music.
     */
    suspend fun searchAlbums(query: String): List<AlbumResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = getJson("$BASE/search?q=$encoded&filter=music_playlists")
        val items = json.optJSONArray("items") ?: JSONArray()
        val results = mutableListOf<AlbumResult>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val url = o.optString("url", "")
            val playlistId = extractPlaylistId(url) ?: continue
            val name = o.optString("name", "")
            val uploaderName = o.optString("uploaderName", "")
            val thumbnail = o.optString("thumbnail", null)
            results.add(AlbumResult(playlistId, name, uploaderName, thumbnail))
        }
        return results
    }

    /**
     * Fetches all songs from a YouTube Music playlist (used as "album").
     */
    suspend fun getAlbumSongs(playlistId: String): List<Song> {
        val json = getJson("$BASE/playlists/$playlistId")
        val relatedStreams = json.optJSONArray("relatedStreams") ?: JSONArray()
        val songs = mutableListOf<Song>()
        for (i in 0 until relatedStreams.length()) {
            val o = relatedStreams.optJSONObject(i) ?: continue
            val song = parseSearchItem(o) ?: continue
            songs.add(song)
        }
        return songs
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun extractVideoId(url: String): String? {
        // url is like "/watch?v=dQw4w9WgXcQ"
        return url.substringAfter("v=").takeIf { it.isNotBlank() }?.substringBefore("&")
    }

    private fun extractPlaylistId(url: String): String? {
        // url is like "/playlist?list=PLxxx"
        return url.substringAfter("list=").takeIf { it.isNotBlank() }?.substringBefore("&")
    }
}
