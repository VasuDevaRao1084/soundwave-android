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

/**
 * Talks to the SAME Supabase backend used by the SoundWave web app
 * (warm-beignet-2d6ca0.netlify.app). Liked songs / playlists / albums
 * sync seamlessly between the web app and this native app.
 */
object SupabaseClient {
    private const val SUPABASE_URL = "https://lrbqkncklbgaqveosrxf.supabase.co"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxyYnFrbmNrbGJnYXF2ZW9zcnhmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODEzMzI5OTcsImV4cCI6MjA5NjkwODk5N30.svU20G9Nuu77nf40r70kAF0dSOZXv84NtUjKmv15l4g"

    private val client = OkHttpClient()
    var accessToken: String? = null  // set after Google OAuth sign-in

    private fun authHeader(builder: Request.Builder): Request.Builder {
        builder.addHeader("apikey", ANON_KEY)
        builder.addHeader("Authorization", "Bearer ${accessToken ?: ANON_KEY}")
        builder.addHeader("Content-Type", "application/json")
        return builder
    }

    suspend fun getUserData(userId: String): JSONObject? = suspendCancellableCoroutine { cont ->
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/user_data?id=eq.$userId&select=*")
        ).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val body = response.body?.string() ?: "[]"
                    val arr = JSONArray(body)
                    if (cont.isActive) cont.resume(if (arr.length() > 0) arr.getJSONObject(0) else null)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

    /**
     * Matches the exact schema used by the web app's user_data table:
     * liked_songs, playlists, albums, recently_played, queue — all JSON columns.
     * Passing null for a field leaves that column untouched (partial update).
     */
    suspend fun saveUserData(
        userId: String,
        likedSongs: JSONArray? = null,
        playlists: JSONArray? = null,
        albums: JSONArray? = null,
        recentlyPlayed: JSONArray? = null,
        queue: JSONArray? = null
    ): Boolean = suspendCancellableCoroutine { cont ->
        val payload = JSONObject().put("id", userId)
        likedSongs?.let { payload.put("liked_songs", it) }
        playlists?.let { payload.put("playlists", it) }
        albums?.let { payload.put("albums", it) }
        recentlyPlayed?.let { payload.put("recently_played", it) }
        queue?.let { payload.put("queue", it) }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = authHeader(
            Request.Builder()
                .url("$SUPABASE_URL/rest/v1/user_data")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
        ).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(false)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (cont.isActive) cont.resume(response.isSuccessful)
            }
        })
    }

    suspend fun trackPlay(userId: String, song: Song): Boolean = suspendCancellableCoroutine { cont ->
        val payload = JSONObject()
            .put("user_id", userId)
            .put("song_id", song.id)
            .put("song_title", song.title)
            .put("song_artist", song.artist)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/play_history").post(body)
        ).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(false)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (cont.isActive) cont.resume(response.isSuccessful)
            }
        })
    }
}
