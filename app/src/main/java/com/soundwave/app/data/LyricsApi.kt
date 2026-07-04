package com.soundwave.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class LyricLine(val timeSec: Float, val text: String)
data class LyricsResult(val synced: List<LyricLine>?, val plain: String?, val instrumental: Boolean)

/**
 * Free, no-key lyrics API. Same source the web app uses, ported to Kotlin.
 */
object LyricsApi {
    private val client = OkHttpClient()
    private val cache = HashMap<String, LyricsResult?>()

    suspend fun fetch(title: String, artist: String, durationSec: Int? = null): LyricsResult? {
        val cacheKey = "$title|$artist"
        if (cache.containsKey(cacheKey)) return cache[cacheKey]

        val getUrl = buildString {
            append("https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}")
            if (durationSec != null) append("&duration=$durationSec")
        }

        val direct = fetchJson(getUrl)
        if (direct != null) {
            val result = parse(direct)
            cache[cacheKey] = result
            return result
        }

        val searchUrl = "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
        val searchBody = fetchRaw(searchUrl)
        if (searchBody != null) {
            try {
                val arr = JSONArray(searchBody)
                if (arr.length() > 0) {
                    val result = parse(arr.getJSONObject(0))
                    cache[cacheKey] = result
                    return result
                }
            } catch (e: Exception) { /* fall through to null */ }
        }

        cache[cacheKey] = null
        return null
    }

    private fun parse(o: JSONObject): LyricsResult {
        val synced = o.optString("syncedLyrics", "").takeIf { it.isNotBlank() }?.let(::parseLRC)
        val plain = o.optString("plainLyrics", "").takeIf { it.isNotBlank() }
        val instrumental = o.optBoolean("instrumental", false)
        return LyricsResult(synced, plain, instrumental)
    }

    private fun parseLRC(lrc: String): List<LyricLine> {
        val regex = Regex("""\[(\d+):(\d+)\.(\d+)\](.*)""")
        return lrc.lines().mapNotNull { line ->
            val m = regex.find(line) ?: return@mapNotNull null
            val (min, sec, hundredths, text) = m.destructured
            val time = min.toFloat() * 60 + sec.toFloat() + hundredths.toFloat() / 100f
            LyricLine(time, text.trim())
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private suspend fun fetchRaw(url: String): String? = suspendCancellableCoroutine { cont ->
        val req = Request.Builder().url(url).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = if (response.isSuccessful) response.body?.string() else null
                if (cont.isActive) cont.resume(body)
            }
        })
    }

    private suspend fun fetchJson(url: String): JSONObject? {
        val body = fetchRaw(url) ?: return null
        return try { JSONObject(body) } catch (e: Exception) { null }
    }
}
