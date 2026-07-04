package com.soundwave.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit

/**
 * Audio source: YouTube InnerTube API — the same internal API the official
 * YouTube and YouTube Music apps use. No API key required. Talks directly
 * to Google, so no third-party servers that can go down.
 *
 * Search  → POST https://music.youtube.com/youtubei/v1/search  (YouTube Music catalog)
 * Streams → POST https://www.youtube.com/youtubei/v1/player    (ANDROID client → direct audio URLs)
 *
 * Reference: used by SimpMusic, BlackHole, NewPipe and every other serious
 * open-source Android music app.
 */
object YoutubeApi {

    private const val YTM_BASE   = "https://music.youtube.com/youtubei/v1"
    private const val YT_BASE    = "https://www.youtube.com/youtubei/v1"
    private const val API_KEY    = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-KOAS-EXPU" // public, same key YT Music app uses
    private const val JSON_MIME  = "application/json; charset=utf-8"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── Context bodies ─────────────────────────────────────────────────────────

    /** YouTube Music web context — used for search so we get music-specific results */
    private fun ytMusicContext() = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20240101.01.00")
                put("hl", "en")
            })
        })
    }

    /**
     * Android client context — critical for player endpoint.
     * YouTube returns unencrypted, directly-playable stream URLs only for
     * the ANDROID client. Other clients return cipher-encrypted URLs that
     * need JS descrambling (which we can't run natively on Android without
     * embedding a JS engine).
     */
    private fun androidClientContext() = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID")
                put("clientVersion", "19.29.37")
                put("androidSdkVersion", 30)
                put("hl", "en")
                put("gl", "IN")  // India — ensures regional content is accessible
            })
        })
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────────

    private suspend fun post(url: String, body: JSONObject): JSONObject =
        suspendCancellableCoroutine { cont ->
            val reqBody = body.toString().toRequestBody(JSON_MIME.toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(reqBody)
                .header("Content-Type", JSON_MIME)
                .header("User-Agent", "Mozilla/5.0")
                .header("Origin", "https://music.youtube.com")
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val text = response.body?.string() ?: "{}"
                        if (cont.isActive) cont.resume(JSONObject(text))
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            })
        }

    // ── Search ─────────────────────────────────────────────────────────────────

    suspend fun search(query: String): List<Song> {
        val body = ytMusicContext().apply {
            put("query", query)
            put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D") // filter: songs only
        }
        val json = post("$YTM_BASE/search?key=$API_KEY", body)
        return parseSearchResults(json)
    }

    private fun parseSearchResults(json: JSONObject): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            // Navigate: contents > tabbedSearchResultsRenderer > tabs[0] > tabRenderer
            //           > content > sectionListRenderer > contents[]
            //           > musicShelfRenderer > contents[]
            //           > musicResponsiveListItemRenderer
            val tabs = json
                .optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return songs

            val contents = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return songs

            for (i in 0 until contents.length()) {
                val shelf = contents.optJSONObject(i)
                    ?.optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    val renderer = items.optJSONObject(j)
                        ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val song = parseMusicItem(renderer) ?: continue
                    songs.add(song)
                }
            }
        } catch (_: Exception) {}
        return songs
    }

    private fun parseMusicItem(renderer: JSONObject): Song? {
        // Video ID is inside the overlay play button
        val videoId = renderer
            .optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() } ?: return null

        // flexColumns hold title and artist text runs
        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null

        val title = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text") ?: ""

        // Second column: artist name is the first run
        val artist = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text") ?: "Unknown Artist"

        // Thumbnail: pick highest resolution
        val thumbnails = renderer
            .optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val thumbnail = thumbnails?.let {
            it.optJSONObject(it.length() - 1)?.optString("url")
        }

        // Duration from fixed columns
        val fixedColumns = renderer.optJSONArray("fixedColumns")
        val durationText = fixedColumns?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text") ?: "0:00"
        val durationSec = parseDuration(durationText)

        return Song(
            id = videoId,
            title = title,
            artist = artist,
            album = null,
            thumbnail = thumbnail,
            durationSec = durationSec,
            streamUrl = null // fetched fresh right before play
        )
    }

    // ── Stream URL ─────────────────────────────────────────────────────────────

    /**
     * Gets a fresh, directly-playable audio stream URL for a YouTube video.
     * Uses the ANDROID client — this is the key trick that makes YouTube return
     * unencrypted URLs that ExoPlayer can play without any JS deciphering.
     */
    suspend fun getSongById(videoId: String): Song? {
        return try {
            val body = androidClientContext().apply { put("videoId", videoId) }
            val json = post("$YT_BASE/player?key=$API_KEY", body)

            val videoDetails = json.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title") ?: ""
            val author = videoDetails?.optString("author") ?: "Unknown Artist"
            val durationSec = videoDetails?.optString("lengthSeconds")?.toIntOrNull() ?: 0
            val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnail = thumbnails?.let {
                it.optJSONObject(it.length() - 1)?.optString("url")
            }

            val streamUrl = getBestAudioUrl(json)

            Song(
                id = videoId,
                title = title,
                artist = author,
                album = null,
                thumbnail = thumbnail,
                durationSec = durationSec,
                streamUrl = streamUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getBestAudioUrl(playerJson: JSONObject): String? {
        val formats = playerJson
            .optJSONObject("streamingData")
            ?.optJSONArray("adaptiveFormats") ?: return null

        var bestUrl: String? = null
        var bestBitrate = -1

        for (i in 0 until formats.length()) {
            val fmt = formats.optJSONObject(i) ?: continue
            val mimeType = fmt.optString("mimeType", "")
            // Only audio tracks; prefer m4a (audio/mp4) for ExoPlayer compatibility
            if (!mimeType.startsWith("audio/")) continue
            val bitrate = fmt.optInt("bitrate", 0)
            val url = fmt.optString("url", "").takeIf { it.isNotBlank() } ?: continue
            if (bitrate > bestBitrate) {
                bestBitrate = bitrate
                bestUrl = url
            }
        }
        return bestUrl
    }

    // ── Album / Playlist ────────────────────────────────────────────────────────

    data class AlbumResult(val id: String, val name: String, val artist: String, val thumbnail: String?)

    suspend fun searchAlbums(query: String): List<AlbumResult> {
        val body = ytMusicContext().apply {
            put("query", query)
            put("params", "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D") // filter: albums only
        }
        val json = post("$YTM_BASE/search?key=$API_KEY", body)
        return parseAlbumResults(json)
    }

    private fun parseAlbumResults(json: JSONObject): List<AlbumResult> {
        val results = mutableListOf<AlbumResult>()
        try {
            val tabs = json
                .optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return results

            val contents = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return results

            for (i in 0 until contents.length()) {
                val shelf = contents.optJSONObject(i)
                    ?.optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    val renderer = items.optJSONObject(j)
                        ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue

                    val browseId = renderer
                        .optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        ?.takeIf { it.isNotBlank() } ?: continue

                    val flexColumns = renderer.optJSONArray("flexColumns") ?: continue
                    val name = flexColumns.optJSONObject(0)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text") ?: continue

                    val artist = flexColumns.optJSONObject(1)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text") ?: ""

                    val thumbnails = renderer
                        .optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    val thumbnail = thumbnails?.let {
                        it.optJSONObject(it.length() - 1)?.optString("url")
                    }

                    results.add(AlbumResult(browseId, name, artist, thumbnail))
                }
            }
        } catch (_: Exception) {}
        return results
    }

    suspend fun getAlbumSongs(browseId: String): List<Song> {
        val body = ytMusicContext().apply { put("browseId", browseId) }
        val json = post("$YTM_BASE/browse?key=$API_KEY", body)
        return parseAlbumSongs(json)
    }

    private fun parseAlbumSongs(json: JSONObject): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val contents = json
                .optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("musicShelfRenderer")
                ?.optJSONArray("contents") ?: return songs

            for (i in 0 until contents.length()) {
                val renderer = contents.optJSONObject(i)
                    ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val song = parseMusicItem(renderer) ?: continue
                songs.add(song)
            }
        } catch (_: Exception) {}
        return songs
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun parseDuration(text: String): Int {
        // text like "3:45" or "1:02:34"
        val parts = text.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }
}
