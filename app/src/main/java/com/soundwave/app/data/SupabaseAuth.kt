package com.soundwave.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Exchanges a Google ID token (from native Google Sign-In) for a real
 * Supabase session, so the native app shares the exact same user account
 * (and therefore the same liked songs / playlists) as the web app.
 */
object SupabaseAuth {
    private const val SUPABASE_URL = "https://lrbqkncklbgaqveosrxf.supabase.co"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxyYnFrbmNrbGJnYXF2ZW9zcnhmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODEzMzI5OTcsImV4cCI6MjA5NjkwODk5N30.svU20G9Nuu77nf40r70kAF0dSOZXv84NtUjKmv15l4g"

    private val client = OkHttpClient()

    /** Returns the Supabase user id (UUID) on success, or null on failure. */
    suspend fun signInWithGoogleIdToken(idToken: String): String? = suspendCancellableCoroutine { cont ->
        val payload = JSONObject().put("provider", "google").put("id_token", idToken)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$SUPABASE_URL/auth/v1/token?grant_type=id_token")
            .addHeader("apikey", ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val text = response.body?.string() ?: "{}"
                    val json = JSONObject(text)
                    val accessToken = json.optString("access_token").ifBlank { null }
                    val userId = json.optJSONObject("user")?.optString("id")
                    if (accessToken != null) SupabaseClient.accessToken = accessToken
                    if (cont.isActive) cont.resume(userId)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        })
    }
}
