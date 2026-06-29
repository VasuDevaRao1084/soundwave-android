package com.soundwave.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundwave.app.data.*
import com.soundwave.app.data.YoutubeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class RepeatMode { OFF, ALL, ONE }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Application = app

    // ── Auth ──────────────────────────────────────────────────────────
    private val prefs = appContext.getSharedPreferences("soundwave_auth", Application.MODE_PRIVATE)

    private val _user = MutableStateFlow<UserProfile?>(restoreUser())
    val user: StateFlow<UserProfile?> = _user.asStateFlow()

    init {
        // If a session was restored from disk, load their library immediately
        _user.value?.let { loadUserData(it.id) }
    }

    private fun restoreUser(): UserProfile? {
        val id = prefs.getString("user_id", null) ?: return null
        return UserProfile(
            id = id,
            email = prefs.getString("user_email", null),
            name = prefs.getString("user_name", null),
            avatarUrl = prefs.getString("user_avatar", null)
        )
    }

    private fun persistUser(profile: UserProfile?) {
        prefs.edit().apply {
            if (profile == null) {
                clear()
            } else {
                putString("user_id", profile.id)
                putString("user_email", profile.email)
                putString("user_name", profile.name)
                putString("user_avatar", profile.avatarUrl)
            }
        }.apply()
    }

    // ── Library ───────────────────────────────────────────────────────
    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _savedAlbums = MutableStateFlow<List<SavedAlbum>>(emptyList())
    val savedAlbums: StateFlow<List<SavedAlbum>> = _savedAlbums.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    // ── Search ────────────────────────────────────────────────────────
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // ── Playback state (mirrors PlaybackService) ─────────────────────
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()
    private val _repeat = MutableStateFlow(RepeatMode.OFF)
    val repeat: StateFlow<RepeatMode> = _repeat.asStateFlow()
    private val _progress = MutableStateFlow(0f) // seconds
    val progress: StateFlow<Float> = _progress.asStateFlow()
    private val _duration = MutableStateFlow(0f)
    val duration: StateFlow<Float> = _duration.asStateFlow()
    private val _sleepTimerMins = MutableStateFlow<Int?>(null)
    val sleepTimerMins: StateFlow<Int?> = _sleepTimerMins.asStateFlow()
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    private val _downloadedIds = MutableStateFlow<Set<String>>(restoreDownloads())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    fun clearPlaybackError() { _playbackError.value = null }

    // Bridge to the real MediaController, set by MainActivity once connected
    var controllerBridge: ControllerBridge? = null
    interface ControllerBridge {
        fun play(song: Song)
        fun togglePlayPause()
        fun seekTo(seconds: Float)
        fun pause()
        fun resume()
    }

    fun setUser(profile: UserProfile?) {
        _user.value = profile
        persistUser(profile)
        if (profile != null) loadUserData(profile.id)
    }

    fun signOut() {
        _user.value = null
        persistUser(null)
        _likedSongs.value = emptyList()
        _playlists.value = emptyList()
        _savedAlbums.value = emptyList()
        _recentlyPlayed.value = emptyList()
    }

    fun isLiked(song: Song) = _likedSongs.value.any { it.id == song.id }

    fun toggleLike(song: Song) {
        val cur = _likedSongs.value
        _likedSongs.value = if (cur.any { it.id == song.id }) cur.filterNot { it.id == song.id } else listOf(song) + cur
        syncToCloud()
    }

    fun createPlaylist(name: String) {
        _playlists.value = _playlists.value + Playlist(id = System.currentTimeMillis().toString(), name = name)
        syncToCloud()
    }

    fun addToPlaylist(playlistId: String, song: Song) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId && it.songs.none { s -> s.id == song.id }) {
                Playlist(it.id, it.name, (it.songs + song).toMutableList())
            } else it
        }
        syncToCloud()
    }

    fun removeFromPlaylist(playlistId: String, songId: String) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) {
                Playlist(it.id, it.name, it.songs.filterNot { s -> s.id == songId }.toMutableList())
            } else it
        }
        syncToCloud()
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filterNot { it.id == playlistId }
        syncToCloud()
    }

    fun saveAlbum(album: SavedAlbum) {
        if (_savedAlbums.value.none { it.id == album.id }) {
            _savedAlbums.value = _savedAlbums.value + album
            syncToCloud()
        }
    }

    fun removeAlbum(albumId: String) {
        _savedAlbums.value = _savedAlbums.value.filterNot { it.id == albumId }
        syncToCloud()
    }

    fun search(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        _isSearching.value = true
        viewModelScope.launch {
            try {
                _searchResults.value = YoutubeApi.search(query)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun playSong(song: Song, fromQueue: List<Song>? = null) {
        _currentSong.value = song
        _isPlaying.value = true
        if (fromQueue != null) {
            _queue.value = fromQueue
            _queueIndex.value = fromQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        }
        addToRecentlyPlayed(song)
        _user.value?.let { u -> viewModelScope.launch { SupabaseClient.trackPlay(u.id, song) } }

        // JioSaavn's direct audio URLs are signed/time-limited and expire —
        // a URL saved from search results days ago (e.g. from liked songs,
        // playlists, or recently played) will likely be dead by now. Always
        // refetch a fresh one by song ID right before actually playing,
        // regardless of where the song object came from.
        viewModelScope.launch {
            val fresh = try { YoutubeApi.getSongById(song.id) } catch (e: Exception) { null }
            var playable = fresh?.takeIf { it.streamUrl != null }

            // ID lookup can fail if the video was removed or ID changed.
            // Fall back to re-searching by title + artist to find a playable match.
            if (playable == null) {
                playable = try {
                    YoutubeApi.search("${song.title} ${song.artist}")
                        .firstOrNull()?.let { result ->
                            // We have the video ID from search, now get its stream URL
                            val streamUrl = YoutubeApi.getStreamUrl(result.id)
                            if (streamUrl != null) result.copy(streamUrl = streamUrl) else null
                        }
                } catch (e: Exception) { null }
            }

            if (playable?.streamUrl != null) {
                controllerBridge?.play(playable)
            } else {
                _playbackError.value = "Couldn't find a playable version of \"${song.title}\""
                _isPlaying.value = false
            }
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
        controllerBridge?.togglePlayPause()
    }

    /** Called when the real player's playing state changes for any reason
     * (notification controls, headset buttons, etc.) — keeps our state truthful. */
    fun syncPlayingState(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun next() {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = if (_shuffle.value) q.indices.random() else (_queueIndex.value + 1)
        if (nextIdx >= q.size) {
            if (_repeat.value == RepeatMode.ALL) playSong(q[0], q) else { _isPlaying.value = false }
            return
        }
        playSong(q[nextIdx], q)
    }

    fun previous() {
        val q = _queue.value
        if (q.isEmpty()) return
        val prevIdx = (_queueIndex.value - 1).coerceAtLeast(0)
        playSong(q[prevIdx], q)
    }

    fun toggleShuffle() { _shuffle.value = !_shuffle.value }
    fun cycleRepeat() {
        _repeat.value = when (_repeat.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun updateProgress(cur: Float, dur: Float) { _progress.value = cur; _duration.value = dur }
    fun seekTo(seconds: Float) { controllerBridge?.seekTo(seconds) }

    fun onTrackEnded() {
        when (_repeat.value) {
            RepeatMode.ONE -> controllerBridge?.let { _currentSong.value?.let(it::play) }
            RepeatMode.ALL -> next()
            RepeatMode.OFF -> if (_queueIndex.value < _queue.value.size - 1) next() else _isPlaying.value = false
        }
    }

    private fun addToRecentlyPlayed(song: Song) {
        val cur = _recentlyPlayed.value.filterNot { it.id == song.id }
        _recentlyPlayed.value = (listOf(song) + cur).take(20)
        syncToCloud()
    }

    fun setSleepTimer(mins: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMins.value = mins
        if (mins == null) return
        sleepTimerJob = viewModelScope.launch {
            kotlinx.coroutines.delay(mins * 60_000L)
            _isPlaying.value = false
            controllerBridge?.pause()
            _sleepTimerMins.value = null
        }
    }

    // ── Downloads (metadata-only cache for offline browsing, no audio files
    //    are actually saved — matches the web app's existing behavior) ──────
    private val downloadsPrefs = appContext.getSharedPreferences("soundwave_downloads", Application.MODE_PRIVATE)

    private fun restoreDownloads(): Set<String> =
        getApplication<Application>().getSharedPreferences("soundwave_downloads", Application.MODE_PRIVATE)
            .getStringSet("ids", emptySet()) ?: emptySet()

    fun isDownloaded(song: Song) = _downloadedIds.value.contains(song.id)

    fun toggleDownload(song: Song) {
        val cur = _downloadedIds.value
        _downloadedIds.value = if (cur.contains(song.id)) cur - song.id else cur + song.id
        downloadsPrefs.edit().putStringSet("ids", _downloadedIds.value).apply()
        // Cache the song's metadata so it can be browsed offline, same as web app
        val metaPrefs = appContext.getSharedPreferences("soundwave_downloads_meta", Application.MODE_PRIVATE)
        if (_downloadedIds.value.contains(song.id)) {
            metaPrefs.edit().putString(song.id, songToJson(song).toString()).apply()
        } else {
            metaPrefs.edit().remove(song.id).apply()
        }
    }

    fun listDownloadedSongs(): List<Song> {
        val metaPrefs = appContext.getSharedPreferences("soundwave_downloads_meta", Application.MODE_PRIVATE)
        return _downloadedIds.value.mapNotNull { id ->
            metaPrefs.getString(id, null)?.let { jsonStr -> try { jsonToSong(JSONObject(jsonStr)) } catch (e: Exception) { null } }
        }
    }

    // ── Cloud sync ────────────────────────────────────────────────────
    private val libraryPrefs = appContext.getSharedPreferences("soundwave_library", Application.MODE_PRIVATE)

    private fun loadUserData(userId: String) {
        // Load from local disk FIRST — this is the source of truth for what
        // the user sees immediately, works fully offline, and can never be
        // wiped by a flaky/empty network response.
        restoreLibraryFromDisk()

        viewModelScope.launch {
            try {
                val data = SupabaseClient.getUserData(userId) ?: return@launch
                // Only adopt cloud data for a field if the cloud actually has
                // something in it. A successful-but-empty cloud response must
                // never erase non-empty local data — that was the data-loss bug.
                data.optJSONArray("liked_songs")?.takeIf { it.length() > 0 }?.let {
                    _likedSongs.value = parseSongsJson(it)
                }
                data.optJSONArray("recently_played")?.takeIf { it.length() > 0 }?.let {
                    _recentlyPlayed.value = parseSongsJson(it)
                }
                data.optJSONArray("playlists")?.takeIf { it.length() > 0 }?.let {
                    _playlists.value = parsePlaylistsJson(it)
                }
                data.optJSONArray("albums")?.takeIf { it.length() > 0 }?.let {
                    _savedAlbums.value = parseAlbumsJson(it)
                }
                persistLibraryToDisk()
            } catch (_: Exception) { /* offline — local data from disk already loaded above, nothing to lose */ }
        }
    }

    private fun persistLibraryToDisk() {
        libraryPrefs.edit()
            .putString("liked_songs", songsToJson(_likedSongs.value).toString())
            .putString("recently_played", songsToJson(_recentlyPlayed.value).toString())
            .putString("playlists", playlistsToJson(_playlists.value).toString())
            .putString("albums", albumsToJson(_savedAlbums.value).toString())
            .apply()
    }

    private fun restoreLibraryFromDisk() {
        try {
            libraryPrefs.getString("liked_songs", null)?.let { _likedSongs.value = parseSongsJson(JSONArray(it)) }
            libraryPrefs.getString("recently_played", null)?.let { _recentlyPlayed.value = parseSongsJson(JSONArray(it)) }
            libraryPrefs.getString("playlists", null)?.let { _playlists.value = parsePlaylistsJson(JSONArray(it)) }
            libraryPrefs.getString("albums", null)?.let { _savedAlbums.value = parseAlbumsJson(JSONArray(it)) }
        } catch (_: Exception) { /* corrupted/missing local cache, start fresh */ }
    }

    private fun syncToCloud() {
        persistLibraryToDisk() // always save locally first, regardless of network
        val uid = _user.value?.id ?: return
        viewModelScope.launch {
            try {
                SupabaseClient.saveUserData(
                    userId = uid,
                    likedSongs = songsToJson(_likedSongs.value),
                    playlists = playlistsToJson(_playlists.value),
                    albums = albumsToJson(_savedAlbums.value),
                    recentlyPlayed = songsToJson(_recentlyPlayed.value)
                )
            } catch (_: Exception) { /* will retry on next change; local copy is already safe */ }
        }
    }

    // ── JSON (de)serialization matching the web app's stored shape ──
    private fun songToJson(s: Song) = JSONObject().apply {
        put("id", s.id); put("title", s.title); put("artist", s.artist)
        put("album", s.album ?: ""); put("thumbnail", s.thumbnail ?: "")
        put("durationSec", s.durationSec); put("streamUrl", s.streamUrl ?: "")
    }
    private fun songsToJson(list: List<Song>) = JSONArray().apply { list.forEach { put(songToJson(it)) } }
    private fun jsonToSong(o: JSONObject) = Song(
        id = o.optString("id"), title = o.optString("title"), artist = o.optString("artist"),
        album = o.optString("album").ifBlank { null }, thumbnail = o.optString("thumbnail").ifBlank { null },
        durationSec = o.optInt("durationSec"), streamUrl = o.optString("streamUrl").ifBlank { null }
    )
    private fun parseSongsJson(arr: JSONArray?): List<Song> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::jsonToSong) }
    }
    private fun playlistsToJson(list: List<Playlist>) = JSONArray().apply {
        list.forEach { pl -> put(JSONObject().apply {
            put("id", pl.id); put("name", pl.name); put("songs", songsToJson(pl.songs))
        }) }
    }
    private fun parsePlaylistsJson(arr: JSONArray?): List<Playlist> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                Playlist(o.optString("id"), o.optString("name"), parseSongsJson(o.optJSONArray("songs")).toMutableList())
            }
        }
    }
    private fun albumsToJson(list: List<SavedAlbum>) = JSONArray().apply {
        list.forEach { a -> put(JSONObject().apply {
            put("id", a.id); put("name", a.name); put("artist", a.artist)
            put("thumbnail", a.thumbnail ?: ""); put("query", a.query)
        }) }
    }
    private fun parseAlbumsJson(arr: JSONArray?): List<SavedAlbum> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                SavedAlbum(o.optString("id"), o.optString("name"), o.optString("artist"),
                    o.optString("thumbnail").ifBlank { null }, o.optString("query"))
            }
        }
    }
}
