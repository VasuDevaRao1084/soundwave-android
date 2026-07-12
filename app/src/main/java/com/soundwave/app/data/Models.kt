package com.soundwave.app.data

data class Playlist(
    val id: String,
    val name: String,
    val songs: MutableList<Song> = mutableListOf(),
    val isPrivate: Boolean = false
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
