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

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val thumbnail: String?,
    val durationSec: Int,
    val streamUrl: String?,
    val source: String = "saavn" // "saavn" or "youtube"
)

object SaavnApi {
    private const val BASE = "https://saavn.sumit.co/api"
    private const val YTM_BASE = "https://music.youtube.com/youtubei/v1"
    private const val YT_BASE = "https://www.youtube.com/youtubei/v1"
    private const val YT_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-KOAS-EXPU"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── HTTP helpers ───────────────────────────────────────────────────────────

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

    private suspend fun postJson(url: String, body: JSONObject): JSONObject = suspendCancellableCoroutine { cont ->
        val reqBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(reqBody)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .header("Origin", "https://music.youtube.com")
            .build()
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

    // ── Search ─────────────────────────────────────────────────────────────────

    suspend fun search(query: String): List<Song> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = getJson("$BASE/search/songs?query=$encoded&limit=20")
        val results = json.optJSONObject("data")?.optJSONArray("results") ?: JSONArray()
        val songs = rankResults(parseSongsWithLanguage(results), query)

        // If all results are instrumentals/covers with no proper English songs,
        // fall back to YouTube Music for this query
        val hasGoodResult = songs.any { it.language == "english" && !it.looksLikeCover }
        return if (!hasGoodResult && songs.all { it.language == "instrumental" || it.looksLikeCover }) {
            val ytSongs = searchYoutube(query)
            if (ytSongs.isNotEmpty()) ytSongs else songs.map { it.song }
        } else {
            songs.map { it.song }
        }
    }

    private data class RankedSong(val song: Song, val language: String, val looksLikeCover: Boolean)

    private val coverArtistIndicators = listOf(
        "tribute", "karaoke", "made famous by", "in the style of",
        "originally performed", "covered by", "as made famous"
    )
    private val coverAlbumIndicators = listOf(
        "tribute to", "karaoke", "made famous by", "in the style of",
        "originally performed", "cover versions of"
    )

    private fun rankResults(songs: List<RankedSong>, query: String): List<RankedSong> {
        val queryLower = query.trim().lowercase()
        return songs.sortedByDescending { (song, language, looksLikeCover) ->
            val titleLower = song.title.lowercase()
            var score = 0
            if (titleLower == queryLower) score += 100
            else if (titleLower.startsWith(queryLower)) score += 50
            else if (titleLower.contains(queryLower)) score += 20
            if (language == "english") score += 40
            if (looksLikeCover) score -= 60
            score
        }
    }

    // ── JioSaavn parsers ───────────────────────────────────────────────────────

    private fun parseSongsWithLanguage(arr: JSONArray): List<RankedSong> {
        val list = mutableListOf<RankedSong>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val song = parseSongObject(o)
            val language = o.optString("language", "").lowercase()
            val albumLower = song.album?.lowercase() ?: ""
            val artistLower = song.artist.lowercase()
            val looksLikeCover = coverArtistIndicators.any { artistLower.contains(it) } ||
                coverAlbumIndicators.any { albumLower.contains(it) }
            list.add(RankedSong(song, language, looksLikeCover))
        }
        return list
    }

    private fun parseSongs(arr: JSONArray): List<Song> {
        val list = mutableListOf<Song>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(parseSongObject(o))
        }
        return list
    }

    private fun parseSongObject(o: JSONObject): Song {
        val id = o.optString("id")
        val title = htmlDecode(o.optString("name", o.optString("title", "")))
        val artistsObj = o.optJSONObject("artists")
        val primaryArtists = artistsObj?.optJSONArray("primary")
        val artist = if (primaryArtists != null && primaryArtists.length() > 0) {
            (0 until primaryArtists.length()).joinToString(", ") {
                primaryArtists.optJSONObject(it)?.optString("name") ?: ""
            }
        } else o.optString("primaryArtists", "Unknown Artist")
        val album = o.optJSONObject("album")?.optString("name")
        val imageArr = o.optJSONArray("image")
        val thumbnail = if (imageArr != null && imageArr.length() > 0)
            imageArr.optJSONObject(imageArr.length() - 1)?.optString("url") else null
        val duration = o.optInt("duration", 0)
        val downloadArr = o.optJSONArray("downloadUrl")
        val streamUrl = if (downloadArr != null && downloadArr.length() > 0)
            downloadArr.optJSONObject(downloadArr.length() - 1)?.optString("url") else null
        return Song(id, title, artist, album, thumbnail, duration, streamUrl, "saavn")
    }

    suspend fun getSongById(id: String): Song? {
        // YouTube songs use "yt_" prefix — re-fetch via YouTube
        if (id.startsWith("yt_")) {
            return getYoutubeSongById(id.removePrefix("yt_"))
        }
        val json = getJson("$BASE/songs/$id")
        val results = json.optJSONArray("data") ?: return null
        return parseSongs(results).firstOrNull()
    }

    // ── Album ──────────────────────────────────────────────────────────────────

    data class AlbumResult(val id: String, val name: String, val artist: String, val thumbnail: String?)

    suspend fun searchAlbums(query: String): List<AlbumResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = getJson("$BASE/search/albums?query=$encoded&limit=15")
        val results = json.optJSONObject("data")?.optJSONArray("results") ?: JSONArray()
        val list = mutableListOf<AlbumResult>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val artistNames = o.optJSONObject("artists")?.optJSONArray("primary")?.let { arr ->
                (0 until arr.length()).joinToString(", ") { j -> arr.optJSONObject(j)?.optString("name") ?: "" }
            } ?: o.optString("primaryArtists", "")
            val images = o.optJSONArray("image")
            val thumb = if (images != null && images.length() > 0) images.optJSONObject(images.length()-1)?.optString("url") else null
            list.add(AlbumResult(o.optString("id"), htmlDecode(o.optString("name")), artistNames, thumb))
        }
        return list
    }

    suspend fun getAlbumSongs(albumId: String): List<Song> {
        val json = getJson("$BASE/albums?id=$albumId")
        val results = json.optJSONObject("data")?.optJSONArray("songs") ?: JSONArray()
        return parseSongs(results)
    }

    // ── YouTube Music fallback ─────────────────────────────────────────────────
    // Used ONLY when JioSaavn returns zero proper results (all instrumentals/covers).
    // Uses YouTube's own InnerTube API — no third-party servers.

    private fun ytMusicContext() = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20240101.01.00")
                put("hl", "en")
            })
        })
    }

    private fun ytAndroidContext() = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID")
                put("clientVersion", "19.29.37")
                put("androidSdkVersion", 30)
                put("hl", "en")
                put("gl", "IN")
            })
        })
    }

    private suspend fun searchYoutube(query: String): List<Song> {
        return try {
            val body = ytMusicContext().apply {
                put("query", query)
                put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D") // songs filter
            }
            val json = postJson("$YTM_BASE/search?key=$YT_KEY", body)
            parseYoutubeSearch(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseYoutubeSearch(json: JSONObject): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val tabs = json.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return songs
            val contents = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return songs
            for (i in 0 until contents.length()) {
                val shelf = contents.optJSONObject(i)?.optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue
                for (j in 0 until items.length()) {
                    val renderer = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val song = parseYoutubeMusicItem(renderer) ?: continue
                    songs.add(song)
                }
            }
        } catch (_: Exception) {}
        return songs
    }

    private fun parseYoutubeMusicItem(renderer: JSONObject): Song? {
        val videoId = renderer
            .optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() } ?: return null

        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
        val title = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")
            ?.optJSONObject(0)?.optString("text") ?: ""
        val artist = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")
            ?.optJSONObject(0)?.optString("text") ?: "Unknown Artist"

        val thumbnails = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnail = thumbnails?.let { it.optJSONObject(it.length() - 1)?.optString("url") }

        val durationText = renderer.optJSONArray("fixedColumns")
            ?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")
            ?.optJSONObject(0)?.optString("text") ?: "0:00"
        val parts = durationText.split(":").mapNotNull { it.toIntOrNull() }
        val duration = when (parts.size) { 2 -> parts[0]*60+parts[1]; 3 -> parts[0]*3600+parts[1]*60+parts[2]; else -> 0 }

        // Prefix ID with "yt_" so getSongById knows to use YouTube player endpoint
        return Song("yt_$videoId", title, artist, null, thumbnail, duration, null, "youtube")
    }

    private suspend fun getYoutubeSongById(videoId: String): Song? {
        return try {
            val body = ytAndroidContext().apply { put("videoId", videoId) }
            val json = postJson("$YT_BASE/player?key=$YT_KEY", body)
            val videoDetails = json.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title") ?: ""
            val author = videoDetails?.optString("author") ?: "Unknown Artist"
            val durationSec = videoDetails?.optString("lengthSeconds")?.toIntOrNull() ?: 0
            val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnail = thumbnails?.let { it.optJSONObject(it.length()-1)?.optString("url") }

            val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
            var bestUrl: String? = null
            var bestBitrate = -1
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i) ?: continue
                    if (!fmt.optString("mimeType","").startsWith("audio/")) continue
                    val bitrate = fmt.optInt("bitrate", 0)
                    val url = fmt.optString("url","").takeIf { it.isNotBlank() } ?: continue
                    if (bitrate > bestBitrate) { bestBitrate = bitrate; bestUrl = url }
                }
            }
            Song("yt_$videoId", title, author, null, thumbnail, durationSec, bestUrl, "youtube")
        } catch (e: Exception) { null }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun htmlDecode(s: String): String =
        s.replace("&amp;", "&").replace("&#039;", "'").replace("&quot;", "\"")
}
