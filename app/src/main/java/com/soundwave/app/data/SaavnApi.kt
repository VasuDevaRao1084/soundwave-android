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
    private const val JIOSAAVN_DIRECT_BASE = "https://www.jiosaavn.com/api.php"
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
        // JioSaavn Direct (their own servers) is now the ONLY saavn source —
        // saavn.sumit.co (a third-party Cloudflare Worker wrapper) has been
        // removed entirely. That wrapper is what hit Cloudflare's rate-limit
        // ("Error 1027") and took search down for everyone; talking to
        // JioSaavn's own backend directly removes that single point of failure.
        val ranked = try { searchJioSaavnDirectRanked(query) } catch (e: Exception) { emptyList() }
        val songs = rankResults(ranked, query)

        val hasGoodResult = songs.any { it.language != "instrumental" && !it.looksLikeCover }
        if (songs.isEmpty() || (!hasGoodResult && songs.all { it.language == "instrumental" || it.looksLikeCover })) {
            val ytSongs = try { searchYoutube(query) } catch (e: Exception) { emptyList() }
            if (ytSongs.isNotEmpty()) return ytSongs
        }
        return songs.map { it.song }
    }

    private data class RankedSong(val song: Song, val language: String, val looksLikeCover: Boolean, val playCount: Long)

    // Checked against title, artist, AND album — a cover/remix is very often
    // labeled right in the song title itself ("Song Name (Cover)"), which the
    // old version never checked at all (only artist/album).
    private val coverIndicators = listOf(
        "tribute", "karaoke", "made famous by", "in the style of",
        "originally performed", "covered by", "as made famous",
        "cover version", "cover)", "(cover", "- cover", "unplugged",
        "acoustic version", "acoustic cover", "remix)", "(remix", "- remix",
        "8d audio", "slowed", "reverb", "lofi", "lo-fi", "mashup",
        "dj mix", "ringtone", "bgm version"
    )

    private fun rankResults(songs: List<RankedSong>, query: String): List<RankedSong> {
        val queryLower = query.trim().lowercase()
        return songs.sortedByDescending { (song, _, looksLikeCover, playCount) ->
            val titleLower = song.title.lowercase()
            var score = 0
            if (titleLower == queryLower) score += 100
            else if (titleLower.startsWith(queryLower)) score += 50
            else if (titleLower.contains(queryLower)) score += 20

            // Popularity signal: helps pick the real original over an obscure
            // cover/re-upload when both happen to have the exact same title
            // and text alone can't tell them apart. Log-scaled so one viral
            // hit doesn't completely dominate, but a clearly more-played
            // track still wins.
            if (playCount > 0) score += (Math.log10(playCount.toDouble()) * 8).toInt()

            // Covers/remixes/karaoke/lofi edits sink well below a plain match
            // regardless of popularity — being a popular remix shouldn't beat
            // the actual original song.
            if (looksLikeCover) score -= 80

            score
        }
    }

    // ── JioSaavn Direct (official website backend) ────────────────────────────
    // Hits JioSaavn's own servers directly — the same backend and DES media-URL
    // decryption scheme the official JioSaavn Android app itself uses. No
    // third-party wrapper/proxy involved, so no independent rate limits to
    // worry about beyond JioSaavn's own service.

    /** Raw per-song JSON from song.getDetails, keyed by song ID. */
    private suspend fun fetchDirectSongData(songId: String): JSONObject? {
        val url = "$JIOSAAVN_DIRECT_BASE?__call=song.getDetails&cc=in&_marker=0&_format=json&pids=$songId"
        val json = getJson(url)
        return json.optJSONObject(songId)
    }

    /** Shared parser for song.getDetails' per-song shape — used by search, getSongById, and album songs alike. */
    private fun buildSongFromDirectData(songId: String, data: JSONObject): Song? {
        val title = htmlDecode(data.optString("song", ""))
        val artist = htmlDecode(data.optString("primary_artists", data.optString("singers", "Unknown Artist")))
        val album = htmlDecode(data.optString("album", ""))
        val image = data.optString("image", "").replace("150x150", "500x500")
        val durationSec = data.optString("duration", "0").toIntOrNull() ?: 0

        val encryptedUrl = data.optString("encrypted_media_url", "").takeIf { it.isNotBlank() } ?: return null
        var streamUrl = try { decryptJioSaavnUrl(encryptedUrl) } catch (e: Exception) { null } ?: return null

        // Use 320kbps if available, otherwise the decrypted URL's default quality
        if (data.optString("320kbps") != "true") {
            streamUrl = streamUrl.replace("_320.mp4", "_160.mp4")
        }

        return Song(songId, title, artist, album.ifBlank { null }, image, durationSec, streamUrl, "saavn")
    }

    private suspend fun searchJioSaavnDirectRanked(query: String): List<RankedSong> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$JIOSAAVN_DIRECT_BASE?__call=autocomplete.get&_format=json&_marker=0&cc=in&includeMetaTags=1&query=$encoded"
        val json = getJson(url)
        val songResults = json.optJSONObject("songs")?.optJSONArray("data") ?: JSONArray()
        val ids = (0 until songResults.length()).mapNotNull { i ->
            songResults.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
        }

        // Autocomplete only returns basic info — fetch full details (language,
        // playCount, stream URL) for each candidate. Done in parallel so search
        // doesn't feel slow with ~10 sequential network round trips.
        return kotlinx.coroutines.coroutineScope {
            ids.map { id ->
                kotlinx.coroutines.async {
                    try {
                        val data = fetchDirectSongData(id) ?: return@async null
                        val song = buildSongFromDirectData(id, data) ?: return@async null
                        val language = data.optString("language", "").lowercase()
                        val playCount = data.optLong("play_count", 0)
                        val titleLower = song.title.lowercase()
                        val artistLower = song.artist.lowercase()
                        val albumLower = song.album?.lowercase() ?: ""
                        val looksLikeCover = coverIndicators.any {
                            artistLower.contains(it) || albumLower.contains(it) || titleLower.contains(it)
                        }
                        RankedSong(song, language, looksLikeCover, playCount)
                    } catch (e: Exception) { null }
                }
            }.mapNotNull { it.await() }
        }
    }

    suspend fun getSongById(id: String): Song? {
        // YouTube songs use "yt_" prefix — re-fetch via YouTube
        if (id.startsWith("yt_")) {
            return getYoutubeSongById(id.removePrefix("yt_"))
        }
        // Older saved songs may still carry a "direct_" prefix from when this
        // was a secondary source — strip it. Either way it's the same
        // underlying JioSaavn catalog ID, so this works for songs saved
        // before this change too.
        val songId = id.removePrefix("direct_")
        val data = fetchDirectSongData(songId) ?: return null
        return buildSongFromDirectData(songId, data)
    }

    // ── Album ──────────────────────────────────────────────────────────────────

    data class AlbumResult(val id: String, val name: String, val artist: String, val thumbnail: String?)

    suspend fun searchAlbums(query: String): List<AlbumResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$JIOSAAVN_DIRECT_BASE?__call=autocomplete.get&_format=json&_marker=0&cc=in&includeMetaTags=1&query=$encoded"
        val json = getJson(url)
        val results = json.optJSONObject("albums")?.optJSONArray("data") ?: JSONArray()
        val list = mutableListOf<AlbumResult>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = htmlDecode(o.optString("title", o.optString("name", "")))
            val artist = htmlDecode(o.optString("description", o.optString("subtitle", o.optString("primary_artists", ""))))
            val thumbnail = o.optString("image", "").replace("150x150", "500x500").takeIf { it.isNotBlank() }
            list.add(AlbumResult(id, name, artist, thumbnail))
        }
        return list
    }

    suspend fun getAlbumSongs(albumId: String): List<Song> {
        val url = "$JIOSAAVN_DIRECT_BASE?__call=content.getAlbumDetails&albumid=$albumId&_format=json&_marker=0"
        val json = getJson(url)
        val results = json.optJSONArray("songs") ?: JSONArray()
        val list = mutableListOf<Song>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val songId = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            buildSongFromDirectData(songId, o)?.let { list.add(it) }
        }
        return list
    }

    /**
     * Decrypts JioSaavn's encrypted_media_url field.
     * Algorithm: DES/ECB/PKCS5Padding with the fixed key "38346591"
     * (well-documented, used by every JioSaavn reverse-engineering project
     * including the official app's own DRM-free media URL scheme).
     */
    private fun decryptJioSaavnUrl(encryptedUrl: String): String {
        val keyBytes = "38346591".toByteArray(Charsets.UTF_8)
        val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "DES")
        val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec)
        val encryptedBytes = android.util.Base64.decode(encryptedUrl.trim(), android.util.Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }


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
                // IOS client returns plain unencrypted stream URLs in adaptiveFormats.
                // ANDROID client sometimes returns signatureCipher (encrypted) URLs
                // which we can't decrypt without a JS engine. IOS never does this.
                put("clientName", "IOS")
                put("clientVersion", "19.29.1")
                put("deviceMake", "Apple")
                put("deviceModel", "iPhone16,2")
                put("osName", "iPhone")
                put("osVersion", "17.5.1.21F90")
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

            // Check playability first
            val status = json.optJSONObject("playabilityStatus")?.optString("status")
            if (status == "ERROR" || status == "LOGIN_REQUIRED" || status == "UNPLAYABLE") return null

            val videoDetails = json.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title") ?: ""
            val author = videoDetails?.optString("author") ?: "Unknown Artist"
            val durationSec = videoDetails?.optString("lengthSeconds")?.toIntOrNull() ?: 0
            val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnail = thumbnails?.let { it.optJSONObject(it.length()-1)?.optString("url") }

            val streamingData = json.optJSONObject("streamingData")

            // IOS client returns plain URLs in adaptiveFormats (audio-only, best quality)
            // Also check regular formats as fallback (contain both audio+video but still playable)
            val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
            val regularFormats = streamingData?.optJSONArray("formats")

            var bestUrl: String? = null
            var bestBitrate = -1

            // Prefer adaptive audio-only streams
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                    val mimeType = fmt.optString("mimeType", "")
                    if (!mimeType.startsWith("audio/")) continue
                    // Skip if URL is missing (signatureCipher = encrypted, can't use)
                    val url = fmt.optString("url", "").takeIf { it.isNotBlank() } ?: continue
                    val bitrate = fmt.optInt("bitrate", 0)
                    if (bitrate > bestBitrate) { bestBitrate = bitrate; bestUrl = url }
                }
            }

            // Fallback: regular formats (audio+video stream, ExoPlayer handles this fine)
            if (bestUrl == null && regularFormats != null) {
                for (i in 0 until regularFormats.length()) {
                    val fmt = regularFormats.optJSONObject(i) ?: continue
                    val url = fmt.optString("url", "").takeIf { it.isNotBlank() } ?: continue
                    val bitrate = fmt.optInt("bitrate", 0)
                    if (bitrate > bestBitrate) { bestBitrate = bitrate; bestUrl = url }
                }
            }

            if (bestUrl == null) return null

            Song("yt_$videoId", title, author, null, thumbnail, durationSec, bestUrl, "youtube")
        } catch (e: Exception) { null }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun htmlDecode(s: String): String =
        s.replace("&amp;", "&").replace("&#039;", "'").replace("&quot;", "\"")
}
