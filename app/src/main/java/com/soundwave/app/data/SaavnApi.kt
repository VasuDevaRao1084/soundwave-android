package com.soundwave.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val thumbnail: String?,
    val durationSec: Int,
    val streamUrl: String?
)

/**
 * Client for the free, hosted JioSaavn API at saavn.sumit.co.
 * No API key needed. Based on the open-source sumitkolhe/jiosaavn-api project.
 * (saavn.dev — the more commonly referenced instance of this same project —
 * was blocked on this device's network, so this alternate domain is used instead.)
 * If this domain ever becomes unreachable too, this project is deployable to
 * your own Cloudflare Worker/Vercel instance as a drop-in replacement.
 */
object SaavnApi {
    private const val BASE = "https://saavn.sumit.co/api"
    private val client = OkHttpClient()

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

    suspend fun search(query: String): List<Song> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val json = getJson("$BASE/search/songs?query=$encoded&limit=25")
        val results = json.optJSONObject("data")?.optJSONArray("results") ?: JSONArray()
        return rankResults(parseSongsWithLanguage(results), query)
    }

    // These phrases in the ARTIST NAME strongly indicate a cover/tribute artist,
    // not the original. We only penalize on artist name, not album name, because
    // legitimate English albums often contain words like "acoustic", "remix", etc.
    // as part of the real release (e.g. "Acoustic Sessions", official remixes).
    private val coverArtistIndicators = listOf(
        "tribute", "karaoke", "made famous by", "in the style of",
        "originally performed", "covered by", "as made famous"
    )

    // These in the album name are strong signals of a fake/cover product.
    // Kept separate and narrower than artist indicators.
    private val coverAlbumIndicators = listOf(
        "tribute to", "karaoke", "made famous by", "in the style of",
        "originally performed", "cover versions of"
    )

    private fun rankResults(songs: List<Pair<Song, String>>, query: String): List<Song> {
        val queryLower = query.trim().lowercase()
        return songs.sortedWith(
            compareByDescending { (song, language) ->
                val titleLower = song.title.lowercase()
                val albumLower = song.album?.lowercase() ?: ""
                val artistLower = song.artist.lowercase()
                var score = 0

                // Title match scoring
                if (titleLower == queryLower) score += 100
                else if (titleLower.startsWith(queryLower)) score += 50
                else if (titleLower.contains(queryLower)) score += 20

                // Boost English-language songs — critical for finding western artists
                // on JioSaavn which is primarily an Indian music platform
                if (language == "english") score += 40

                // Only penalize when there's a strong signal it's a cover product
                val looksLikeCoverArtist = coverArtistIndicators.any { artistLower.contains(it) }
                val looksLikeCoverAlbum = coverAlbumIndicators.any { albumLower.contains(it) }
                if (looksLikeCoverArtist) score -= 60
                if (looksLikeCoverAlbum) score -= 40

                score
            }
        ).map { it.first }
    }

    suspend fun getSongById(id: String): Song? {
        val json = getJson("$BASE/songs/$id")
        val results = json.optJSONArray("data") ?: return null
        val list = parseSongs(results)
        return list.firstOrNull()
    }

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

    // Parses songs AND captures the language field for ranking purposes.
    // Used by search() so English songs can be boosted over Indian covers.
    private fun parseSongsWithLanguage(arr: JSONArray): List<Pair<Song, String>> {
        val list = mutableListOf<Pair<Song, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val song = parseSongObject(o)
            val language = o.optString("language", "").lowercase()
            list.add(Pair(song, language))
        }
        return list
    }

    // Used for album song lists and getSongById — no language ranking needed there.
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
            val names = mutableListOf<String>()
            for (j in 0 until primaryArtists.length()) {
                names.add(primaryArtists.optJSONObject(j)?.optString("name") ?: "")
            }
            names.joinToString(", ")
        } else o.optString("primaryArtists", "Unknown Artist")
        val album = o.optJSONObject("album")?.optString("name")
        val imageArr = o.optJSONArray("image")
        val thumbnail = if (imageArr != null && imageArr.length() > 0) {
            imageArr.optJSONObject(imageArr.length() - 1)?.optString("url")
        } else null
        val duration = o.optInt("duration", 0)
        val downloadArr = o.optJSONArray("downloadUrl")
        val streamUrl = if (downloadArr != null && downloadArr.length() > 0) {
            // Pick the highest quality available (last entry is typically best, e.g. 320kbps)
            downloadArr.optJSONObject(downloadArr.length() - 1)?.optString("url")
        } else null
        return Song(id, title, artist, album, thumbnail, duration, streamUrl)
    }

    private fun htmlDecode(s: String): String =
        s.replace("&amp;", "&").replace("&#039;", "'").replace("&quot;", "\"")
}
