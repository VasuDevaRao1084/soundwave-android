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

    suspend fun sendFriendRequest(senderId: String, receiverId: String): Pair<Boolean, String?> = suspendCancellableCoroutine { cont ->
        val payload = JSONObject().put("sender_id", senderId).put("receiver_id", receiverId).put("status", "pending")
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/friend_requests").post(body)
        ).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(false to e.message)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    if (cont.isActive) cont.resume(true to null)
                } else {
                    val detail = "HTTP ${response.code}: ${response.body?.string()?.take(200)}"
                    if (cont.isActive) cont.resume(false to detail)
                }
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
        // Resolve every row's "other user" profile with ONE batched query
        // (id=in.(...)) instead of one network round-trip per row — avoids
        // serial N+1 lookups that made the Friends screen feel slow to load.
        val otherIds = rawRows.map { row ->
            val senderId = row.optString("sender_id")
            val receiverId = row.optString("receiver_id")
            if (receiverId == userId) senderId else receiverId
        }.distinct()
        val profilesById = getProfilesByIds(otherIds).associateBy { it.id }

        rawRows.mapNotNull { row ->
            val senderId = row.optString("sender_id")
            val receiverId = row.optString("receiver_id")
            val isIncoming = receiverId == userId
            val otherId = if (isIncoming) senderId else receiverId
            val otherProfile = profilesById[otherId] ?: return@mapNotNull null
            FriendRequestItem(
                requestId = row.optString("id"),
                otherUser = otherProfile,
                status = row.optString("status"),
                isIncoming = isIncoming
            )
        }
    }

    /** Batched version of getProfileById — one query for multiple user IDs. */
    suspend fun getProfilesByIds(userIds: List<String>): List<FriendProfile> {
        if (userIds.isEmpty()) return emptyList()
        return suspendCancellableCoroutine { cont ->
            val idList = userIds.joinToString(",")
            val req = authHeader(
                Request.Builder().url("$SUPABASE_URL/rest/v1/profiles?id=in.($idList)&select=*")
            ).build()
            client.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (cont.isActive) cont.resume(emptyList())
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val arr = JSONArray(response.body?.string() ?: "[]")
                        val list = mutableListOf<FriendProfile>()
                        for (i in 0 until arr.length()) list.add(parseProfile(arr.getJSONObject(i)))
                        if (cont.isActive) cont.resume(list)
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(emptyList())
                    }
                }
            })
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

    /** Batched fetch of several friends' recently-played lists in one call
     * (avoids the N+1 pattern already fixed elsewhere for friend requests).
     * Returns a map of userId -> recently_played JSONArray. */
    suspend fun getFriendsActivity(friendUserIds: List<String>): Map<String, JSONArray> = suspendCancellableCoroutine { cont ->
        if (friendUserIds.isEmpty()) {
            cont.resume(emptyMap()); return@suspendCancellableCoroutine
        }
        val ids = friendUserIds.joinToString(",")
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/user_data?id=in.($ids)&select=id,recently_played")
        ).build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(emptyMap())
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val arr = JSONArray(response.body?.string() ?: "[]")
                    val map = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val id = o.optString("id")
                        val played = o.optJSONArray("recently_played") ?: JSONArray()
                        id to played
                    }.toMap()
                    if (cont.isActive) cont.resume(map)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(emptyMap())
                }
            }
        })
    }

    /** Uploads this device's FCM token so friend-request push notifications
     * (sent from a Supabase Edge Function) can reach this user. Safe to call
     * repeatedly — last write wins, which is exactly what we want on token
     * refresh. */
    suspend fun updateFcmToken(userId: String, token: String): Boolean = suspendCancellableCoroutine { cont ->
        val payload = JSONObject().put("fcm_token", token)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = authHeader(
            Request.Builder().url("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId").patch(body)
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

    /** Uploads avatar bytes to Storage under userId/avatar.jpg, returns the public URL. */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray): String? = suspendCancellableCoroutine { cont ->
        val body = bytes.toRequestBody("image/jpeg".toMediaType())
        val req = Request.Builder()
            .url("$SUPABASE_URL/storage/v1/object/avatars/$userId/avatar.jpg")
            .addHeader("apikey", ANON_KEY)
            .addHeader("Authorization", "Bearer ${accessToken ?: ANON_KEY}")
            .addHeader("x-upsert", "true")
            .put(body)
            .build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    // Cache-bust with a timestamp so the client doesn't serve a
                    // stale cached image for this same path after a re-upload.
                    val url = "$SUPABASE_URL/storage/v1/object/public/avatars/$userId/avatar.jpg?t=${System.currentTimeMillis()}"
                    if (cont.isActive) cont.resume(url)
                } else {
                    if (cont.isActive) cont.resume(null)
                }
            }
        })
    }
}
