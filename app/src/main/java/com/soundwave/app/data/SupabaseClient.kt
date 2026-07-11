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

    // ── Friends ──────────────────────────────────────────────────────────────
    // Registers/updates a searchable public-safe profile row for this account —
    // separate from auth.users, since we never want to expose that table for
    // search. Called after every sign-in so the profile stays current.
    suspend fun upsertProfile(userId: String, email: String, displayName: String?, avatarUrl: String?): Boolean =
        suspendCancellableCoroutine { cont ->
            val payload = JSONObject()
                .put("id", userId)
                .put("email", email)
                .apply {
                    displayName?.let { put("username", it) }
                    avatarUrl?.let { put("avatar_url", it) }
                }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val req = authHeader(
                Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/profiles")
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

    private fun parseProfile(o: JSONObject): FriendProfile = FriendProfile(
        id = o.optString("id"),
        email = o.optString("email"),
        displayName = o.optString("username").ifBlank { null },
        avatarUrl = o.optString("avatar_url").ifBlank { null }
    )

    // Exact-match only, deliberately — never a partial/browsable search, so
    // no one can enumerate other users' emails by searching common letters.
    suspend fun findUserByEmail(email: String): FriendProfile? = suspendCancellableCoroutine { cont ->
        val encoded = java.net.URLEncoder.encode(email.trim().lowercase(), "UTF-8")
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/profiles?email=eq.$encoded&select=*")
        ).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val arr = JSONArray(response.body?.string() ?: "[]")
                    if (cont.isActive) cont.resume(if (arr.length() > 0) parseProfile(arr.getJSONObject(0)) else null)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        })
    }

    suspend fun sendFriendRequest(senderId: String, receiverId: String): Boolean = suspendCancellableCoroutine { cont ->
        val payload = JSONObject().put("sender_id", senderId).put("receiver_id", receiverId).put("status", "pending")
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/friend_requests").post(body)
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

    suspend fun respondToFriendRequest(requestId: String, accept: Boolean): Boolean = suspendCancellableCoroutine { cont ->
        val payload = JSONObject().put("status", if (accept) "accepted" else "declined")
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/friend_requests?id=eq.$requestId").patch(body)
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

    // Pulls every request involving this user (both directions), then resolves
    // the OTHER person's profile with a second lookup — simpler and more
    // robust than relying on PostgREST's embedded-resource join syntax.
    suspend fun getFriendRequests(userId: String): List<FriendRequestItem> = suspendCancellableCoroutine<List<JSONObject>> { cont ->
        val req = authHeader(
            Request.Builder().url(
                "$SUPABASE_URL/rest/v1/friend_requests?or=(sender_id.eq.$userId,receiver_id.eq.$userId)&select=*"
            )
        ).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(emptyList())
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val arr = JSONArray(response.body?.string() ?: "[]")
                    val rows = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) rows.add(arr.getJSONObject(i))
                    if (cont.isActive) cont.resume(rows.map { it })
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
        })
    }.let { rawRows ->
        // Resolve each row's "other user" profile.
        rawRows.mapNotNull { row ->
            val senderId = row.optString("sender_id")
            val receiverId = row.optString("receiver_id")
            val isIncoming = receiverId == userId
            val otherId = if (isIncoming) senderId else receiverId
            val otherProfile = getProfileById(otherId) ?: return@mapNotNull null
            FriendRequestItem(
                requestId = row.optString("id"),
                otherUser = otherProfile,
                status = row.optString("status"),
                isIncoming = isIncoming
            )
        }
    }

    suspend fun getProfileById(userId: String): FriendProfile? = suspendCancellableCoroutine { cont ->
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId&select=*")
        ).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val arr = JSONArray(response.body?.string() ?: "[]")
                    if (cont.isActive) cont.resume(if (arr.length() > 0) parseProfile(arr.getJSONObject(0)) else null)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        })
    }

    /** Read-only fetch of a friend's playlists JSON, for copy-based sharing. */
    suspend fun getFriendPlaylists(friendUserId: String): JSONArray? = suspendCancellableCoroutine { cont ->
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/user_data?id=eq.$friendUserId&select=playlists")
        ).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val arr = JSONArray(response.body?.string() ?: "[]")
                    if (cont.isActive) cont.resume(
                        if (arr.length() > 0) arr.getJSONObject(0).optJSONArray("playlists") else null
                    )
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        })
    }
}
