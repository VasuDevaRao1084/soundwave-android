package com.soundwave.app.data

data class Playlist(
    val id: String,
    val name: String,
    val songs: MutableList<Song> = mutableListOf(),
    val isPrivate: Boolean = false,
    // Set only on playlists imported from a friend. Lets us later ask "does
    // the friend's original have songs I don't have yet?" without turning
    // this into a fully live-synced playlist (deliberately a snapshot copy,
    // per the roadmap) — `importedSongIds` is the snapshot at import time,
    // diffed against the friend's current playlist on demand.
    val sourceFriendId: String? = null,
    val sourcePlaylistId: String? = null,
    val importedSongIds: List<String> = emptyList()
)

data class SavedAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val thumbnail: String?,
    val query: String // used to re-fetch songs for this album from Saavn
)

data class UserProfile(
    val id: String,
    val email: String?,
    val name: String?,
    val avatarUrl: String?
)

data class FriendProfile(
    val id: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?
)

data class FriendRequestItem(
    val requestId: String,
    val otherUser: FriendProfile,
    val status: String, // pending | accepted | declined
    val isIncoming: Boolean // true if the OTHER user sent it to us
)

/** One friend's recent-listening snapshot for the Activity feed. `songs` is
 * newest-first (same ordering as recently_played is stored), so songs[0] is
 * "what they're playing / just played". */
data class FriendActivity(
    val friend: FriendProfile,
    val songs: List<Song>
)
