package com.soundwave.app.data

data class Playlist(
    val id: String,
    val name: String,
    val songs: MutableList<Song> = mutableListOf()
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
