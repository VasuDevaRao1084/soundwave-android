package com.soundwave.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundwave.app.data.*

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
        loadTopCharts()
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

    // ── Smart Recommendations ────────────────────────────────────────
    // Lightweight on-device recommendation engine: tracks which artists
    // the user plays most, then surfaces more songs from those artists
    // plus searches for "similar artist" style queries. No ML model —
    // just frequency-weighted artist affinity, refreshed as they listen.
    private val _recommendedSongs = MutableStateFlow<List<Song>>(emptyList())
    val recommendedSongs: StateFlow<List<Song>> = _recommendedSongs.asStateFlow()

    // ── Top charts by language ────────────────────────────────────────────────
    // Populated from JioSaavn's real play_count field, sorted by actual
    // popularity — not curated by us, not random, genuinely the most-played
    // matching results JioSaavn's own catalog returns for each language.
    private val _topTelugu = MutableStateFlow<List<Song>>(emptyList())
    val topTelugu: StateFlow<List<Song>> = _topTelugu.asStateFlow()
    private val _topHindi = MutableStateFlow<List<Song>>(emptyList())
    val topHindi: StateFlow<List<Song>> = _topHindi.asStateFlow()
    private val _topEnglish = MutableStateFlow<List<Song>>(emptyList())
    val topEnglish: StateFlow<List<Song>> = _topEnglish.asStateFlow()

    private fun loadTopCharts() {
        viewModelScope.launch {
            try { _topTelugu.value = SaavnApi.getTopSongsByLanguage("telugu") } catch (e: Exception) {
                com.soundwave.app.data.ErrorLog.log(appContext, "TOP_CHARTS", "Telugu chart load failed: ${e.message}")
            }
        }
        viewModelScope.launch {
            try { _topHindi.value = SaavnApi.getTopSongsByLanguage("hindi") } catch (e: Exception) {
                com.soundwave.app.data.ErrorLog.log(appContext, "TOP_CHARTS", "Hindi chart load failed: ${e.message}")
            }
        }
        viewModelScope.launch {
            try { _topEnglish.value = SaavnApi.getTopSongsByLanguage("english") } catch (e: Exception) {
                com.soundwave.app.data.ErrorLog.log(appContext, "TOP_CHARTS", "English chart load failed: ${e.message}")
            }
        }
    }
    private val artistPlayCounts = mutableMapOf<String, Int>()
    private var lastRecommendationRefresh = 0L

    // ── Search ────────────────────────────────────────────────────────
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    private val _searchHistory = MutableStateFlow<List<String>>(restoreSearchHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

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

    // ── Sound settings (quality, EQ toggle, smooth transitions) ──────────────
    private val soundPrefs = appContext.getSharedPreferences("soundwave_sound_settings", Application.MODE_PRIVATE)

    private val _smoothTransitionsEnabled = MutableStateFlow(soundPrefs.getBoolean("smoothTransitions", true))
    val smoothTransitionsEnabled: StateFlow<Boolean> = _smoothTransitionsEnabled.asStateFlow()
    fun setSmoothTransitions(enabled: Boolean) {
        _smoothTransitionsEnabled.value = enabled
        soundPrefs.edit().putBoolean("smoothTransitions", enabled).apply()
    }

    enum class AudioQuality(val kbps: Int, val label: String) {
        DATA_SAVER(96, "Data Saver"),
        NORMAL(160, "Normal"),
        HIGH(320, "High")
    }

    private val _audioQuality = MutableStateFlow(
        AudioQuality.values().firstOrNull { it.kbps == soundPrefs.getInt("qualityKbps", 320) } ?: AudioQuality.HIGH
    )
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()
    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
        SaavnApi.preferredQualityKbps = quality.kbps
        soundPrefs.edit().putInt("qualityKbps", quality.kbps).apply()
    }

    init {
        // Apply the saved quality preference to SaavnApi immediately, since
        // it's a plain object property (not itself persisted) that needs to
        // be set once at startup to match whatever the user chose last time.
        SaavnApi.preferredQualityKbps = _audioQuality.value.kbps
    }

    private val _downloadedIds = MutableStateFlow<Set<String>>(restoreDownloads())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()
    // Maps song ID → absolute path of the locally downloaded audio file
    private val _downloadedPaths = MutableStateFlow<Map<String, String>>(restoreDownloadPaths())
    val downloadedPaths: StateFlow<Map<String, String>> = _downloadedPaths.asStateFlow()
    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    fun clearPlaybackError() { _playbackError.value = null }

    // Bridge to the real MediaController, set by MainActivity once connected
    var controllerBridge: ControllerBridge? = null
    interface ControllerBridge {
        fun play(song: Song)
        fun playQueue(songs: List<Song>, startIndex: Int, autoPlay: Boolean = false)
        fun togglePlayPause()
        fun seekTo(seconds: Float)
        fun pause()
        fun resume()
        fun skipToNext()
        fun skipToPrevious()
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
                _searchResults.value = SaavnApi.search(query)
                // Save to recent searches (deduped, max 10)
                val updated = (listOf(query) + _searchHistory.value.filter { it != query }).take(10)
                _searchHistory.value = updated
                appContext.getSharedPreferences("soundwave_search", Application.MODE_PRIVATE)
                    .edit().putString("history", updated.joinToString("|||")).apply()
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        appContext.getSharedPreferences("soundwave_search", Application.MODE_PRIVATE)
            .edit().remove("history").apply()
    }

    private fun restoreSearchHistory(): List<String> {
        val raw = appContext.getSharedPreferences("soundwave_search", Application.MODE_PRIVATE)
            .getString("history", null) ?: return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun playSong(song: Song, fromQueue: List<Song>? = null) {
        _currentSong.value = song
        _isPlaying.value = true
        val queueToUse = fromQueue ?: _queue.value.ifEmpty { listOf(song) }
        _queue.value = queueToUse
        val startIndex = queueToUse.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _queueIndex.value = startIndex

        addToRecentlyPlayed(song)
        _user.value?.let { u -> viewModelScope.launch { SupabaseClient.trackPlay(u.id, song) } }
        updateSmartRecommendations(song)

        suspend fun resolvePlayable(s: Song): Song? {
            // Check offline first — if we have a local file, use it immediately
            val localPath = validLocalFileOrNull(s.id)
            if (localPath != null) {
                return s.copy(streamUrl = "file://$localPath")
            }
            val fresh = try { SaavnApi.getSongById(s.id) } catch (e: Exception) { null }
            var playable = fresh?.takeIf { it.streamUrl != null }
            if (playable == null) {
                playable = try {
                    val results = SaavnApi.search("${s.title} ${s.artist}")
                    results.firstOrNull { it.source == s.source && it.streamUrl != null }
                        ?: results.firstOrNull { it.streamUrl != null }
                } catch (e: Exception) { null }
            }
            return playable
        }

        viewModelScope.launch {
            // ── Step 1: Start playing the tapped song IMMEDIATELY ──────────────
            // If the song already has a stream URL (from search results or
            // local file), hand it to ExoPlayer right now — zero delay.
            // This stops the previous song instantly and starts the new one.
            val immediateUrl = run {
                val localPath = validLocalFileOrNull(song.id)
                if (localPath != null) "file://$localPath"
                else song.streamUrl
            }

            if (immediateUrl != null) {
                val immediate = song.copy(streamUrl = immediateUrl)
                _currentSong.value = immediate
                controllerBridge?.play(immediate)
            }

            // ── Step 2: Fetch a fresh URL in background (stream URLs expire) ───
            // Do this even if we already started playing — the current URL
            // might be stale if this song was from a liked/playlist list.
            val playableStart = resolvePlayable(song)
            if (playableStart?.streamUrl == null) {
                if (immediateUrl == null) {
                    _playbackError.value = "Couldn't find a playable version of \"${song.title}\""
                    _isPlaying.value = false
                }
                return@launch
            }
            _currentSong.value = playableStart

            // Only update ExoPlayer with the fresh URL if we didn't already
            // start playing (i.e. there was no immediateUrl), to avoid
            // restarting a song that's already playing fine.
            if (immediateUrl == null) {
                controllerBridge?.play(playableStart)
            }

            // ── Step 3: Resolve queue neighbors in background ─────────────────
            // Do this AFTER starting playback so it never blocks the user.
            // Fills in the queue window so Next/Previous widget buttons work.
            //
            // Defensive: if queueToUse doesn't actually contain the song being
            // played (e.g. a caller passed the wrong list — this is exactly
            // what caused a real IndexOutOfBoundsException crash before),
            // fall back to a single-song queue instead of indexing into a
            // list that doesn't have the song in it. Also wrapped in a
            // try/catch as a last-resort safety net: this step is a
            // background nice-to-have (Next/Previous queue), so if anything
            // still goes wrong here, playback (already started in Step 1/2)
            // just continues without crashing the app.
            try {
                val safeQueue = if (queueToUse.isNotEmpty() && startIndex < queueToUse.size) queueToUse else listOf(song)
                val safeStartIndex = if (safeQueue === queueToUse) startIndex else 0

                val windowSize = 5 // reduced from 10 — less background work
                val from = (safeStartIndex - windowSize).coerceAtLeast(0)
                val to = (safeStartIndex + windowSize).coerceAtMost(safeQueue.size - 1)
                val windowSongs = safeQueue.subList(from, to + 1).toMutableList()
                val relativeStartIndex = (safeStartIndex - from).coerceIn(0, windowSongs.size - 1)
                if (windowSongs.isEmpty()) return@launch
                windowSongs[relativeStartIndex] = playableStart

                val resolvedWindow = windowSongs.mapIndexed { idx, s ->
                    if (idx == relativeStartIndex) s
                    else {
                        val local = validLocalFileOrNull(s.id)
                        if (local != null) s.copy(streamUrl = "file://$local")
                        else (try { SaavnApi.getSongById(s.id) } catch (e: Exception) { null }) ?: s
                    }
                }

                // Update ExoPlayer's queue now that neighbors are resolved —
                // seekTo keeps playback position so the current song isn't restarted
                controllerBridge?.playQueue(resolvedWindow, relativeStartIndex)
            } catch (e: Exception) {
                android.util.Log.e("SoundWave", "Queue window resolution failed (non-fatal)", e)
                com.soundwave.app.data.ErrorLog.log(appContext, "QUEUE", "Window resolution failed: ${e.message}")
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
        if (_shuffle.value) {
            val nextIdx = q.indices.random()
            playSong(q[nextIdx], q)
            return
        }
        // Native skip — keeps ExoPlayer's internal queue position in sync,
        // which is what the system widget/notification buttons rely on too
        controllerBridge?.skipToNext()
    }

    fun previous() {
        val q = _queue.value
        if (q.isEmpty()) return
        controllerBridge?.skipToPrevious()
    }

    /** Called by MainActivity when ExoPlayer's current queue position changes
     * (from native skip, widget buttons, or auto-advance) — keeps app state in sync. */
    fun syncQueuePosition(song: Song) {
        _currentSong.value = song
        _queueIndex.value = _queue.value.indexOfFirst { it.id == song.id }.coerceAtLeast(_queueIndex.value)
        addToRecentlyPlayed(song)
        updateSmartRecommendations(song)
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

    /** Called only when ExoPlayer's entire internal queue finishes playing
     * (STATE_ENDED) — mid-queue transitions are now handled natively by
     * ExoPlayer itself via setMediaItems(), with syncQueuePosition() keeping
     * app state in sync. This only handles repeat behavior at the true end. */
    fun onTrackEnded() {
        when (_repeat.value) {
            RepeatMode.ONE -> controllerBridge?.let { _currentSong.value?.let(it::play) }
            RepeatMode.ALL -> {
                val q = _queue.value
                if (q.isNotEmpty()) playSong(q[0], q)
            }
            RepeatMode.OFF -> _isPlaying.value = false
        }
    }

    private fun addToRecentlyPlayed(song: Song) {
        val cur = _recentlyPlayed.value.filterNot { it.id == song.id }
        _recentlyPlayed.value = (listOf(song) + cur).take(20)
        syncToCloud()
    }

    /**
     * Updates artist play-frequency tracking and refreshes recommendations.
     * Strategy: every play increments that artist's count. When the top
     * artist changes or it's been a while, re-search JioSaavn for that
     * artist's other songs (excluding ones already in recently played)
     * to surface a "More like this" style list — similar to Spotify's
     * "Made For You" but using simple, transparent frequency weighting
     * instead of an opaque ML model, which is appropriate for an on-device
     * app with no server-side user data warehouse.
     */
    private fun updateSmartRecommendations(song: Song) {
        artistPlayCounts[song.artist] = (artistPlayCounts[song.artist] ?: 0) + 1

        val now = System.currentTimeMillis()
        // Refresh at most every 60 seconds to avoid hammering the API on rapid skips
        if (now - lastRecommendationRefresh < 60_000) return
        lastRecommendationRefresh = now

        val topArtists = artistPlayCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }
        if (topArtists.isEmpty()) return

        viewModelScope.launch {
            val alreadyPlayedIds = _recentlyPlayed.value.map { it.id }.toSet()
            val recommendations = mutableListOf<Song>()
            for (artist in topArtists) {
                try {
                    val results = SaavnApi.search(artist)
                    recommendations.addAll(
                        results.filter { it.id !in alreadyPlayedIds && it.streamUrl != null }.take(5)
                    )
                } catch (e: Exception) { /* skip this artist on failure, try the rest */ }
            }
            _recommendedSongs.value = recommendations.distinctBy { it.id }.shuffled().take(15)
        }
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

    // A genuine downloaded song is always well over this size. Anything
    // smaller means the download was actually a corrupt/error response that
    // got saved by mistake (see downloadSong's own guard) — or an older
    // download from before that guard existed. Treating it as "not
    // downloaded" and cleaning it up here means the app self-heals instead
    // of trying to hand a broken file to ExoPlayer and crashing.
    private fun validLocalFileOrNull(songId: String): String? {
        val path = _downloadedPaths.value[songId] ?: return null
        val f = java.io.File(path)
        if (f.exists() && f.length() >= 50_000L) return path
        // Corrupt/missing — clean up so it stops being offered as "downloaded"
        f.delete()
        _downloadedIds.value = _downloadedIds.value - songId
        _downloadedPaths.value = _downloadedPaths.value - songId
        downloadsPrefs.edit()
            .putStringSet("ids", _downloadedIds.value)
            .remove("path_$songId")
            .remove("meta_$songId")
            .apply()
        return null
    }

    private fun restoreDownloads(): Set<String> =
        getApplication<Application>().getSharedPreferences("soundwave_downloads", Application.MODE_PRIVATE)
            .getStringSet("ids", emptySet()) ?: emptySet()

    private fun restoreDownloadPaths(): Map<String, String> {
        val prefs = getApplication<Application>().getSharedPreferences("soundwave_downloads", Application.MODE_PRIVATE)
        val all = prefs.all
        return all.entries
            .filter { it.key.startsWith("path_") }
            .associate { it.key.removePrefix("path_") to (it.value as? String ?: "") }
            .filter { it.value.isNotBlank() }
    }

    fun isDownloaded(song: Song) = _downloadedIds.value.contains(song.id)

    fun toggleDownload(song: Song) {
        if (_downloadedIds.value.contains(song.id)) {
            // Delete the local audio file and remove from tracking
            val path = _downloadedPaths.value[song.id]
            if (path != null) {
                try { java.io.File(path).delete() } catch (e: Exception) { /* ignore */ }
            }
            _downloadedIds.value = _downloadedIds.value - song.id
            _downloadedPaths.value = _downloadedPaths.value - song.id
            downloadsPrefs.edit()
                .putStringSet("ids", _downloadedIds.value)
                .remove("path_${song.id}")
                .remove("meta_${song.id}")
                .apply()
        } else {
            // Kick off a real background download of the audio file
            viewModelScope.launch { downloadSong(song) }
        }
    }

    private suspend fun downloadSong(song: Song) {
        if (_downloadingIds.value.contains(song.id)) return
        _downloadingIds.value = _downloadingIds.value + song.id

        try {
            // Get a fresh stream URL — the one on the song object may be stale
            val playable = SaavnApi.getSongById(song.id) ?: run {
                val results = SaavnApi.search("${song.title} ${song.artist}")
                results.firstOrNull { it.streamUrl != null }
            }
            val url = playable?.streamUrl ?: run {
                _playbackError.value = "Couldn't find a downloadable version of \"${song.title}\""
                return
            }

            // The actual network + disk I/O MUST run off the main thread —
            // HttpURLConnection.connect()/getInputStream() throw
            // NetworkOnMainThreadException if called from Dispatchers.Main,
            // which is what viewModelScope.launch uses by default. That
            // exception was being silently swallowed by the catch block
            // below, so downloads always failed instantly with no visible
            // progress and nothing ever appeared in the Downloads tab.
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val downloadsDir = java.io.File(appContext.filesDir, "downloads").also { it.mkdirs() }
                val targetFile = java.io.File(downloadsDir, "${song.id}.m4a")

                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw java.io.IOException("Server returned HTTP ${connection.responseCode}")
                    }
                    // Some CDNs return a 200 OK with a tiny HTML/JSON error body
                    // instead of the actual audio when a signed URL is
                    // rejected. Bail out early rather than saving that as a
                    // "downloaded" song — it previously looked like a
                    // successful 1-second download and then crashed/failed
                    // to play, since it wasn't a real audio file.
                    val contentType = connection.contentType ?: ""
                    if (contentType.contains("text") || contentType.contains("json")) {
                        throw java.io.IOException("Server returned $contentType instead of audio")
                    }
                    connection.inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 8 * 1024)
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                // A real song is at minimum tens of KB. Anything smaller is
                // almost certainly a truncated/error response, not audio.
                val minValidBytes = 50_000L
                if (!targetFile.exists() || targetFile.length() < minValidBytes) {
                    targetFile.delete()
                    throw java.io.IOException(
                        "Downloaded file was only ${targetFile.length()} bytes — not a valid audio file"
                    )
                }
                targetFile
            }

            // Save metadata for offline browsing
            val metaStr = songToJson(song.copy(streamUrl = null)).toString()
            _downloadedIds.value = _downloadedIds.value + song.id
            _downloadedPaths.value = _downloadedPaths.value + (song.id to file.absolutePath)
            downloadsPrefs.edit()
                .putStringSet("ids", _downloadedIds.value)
                .putString("path_${song.id}", file.absolutePath)
                .putString("meta_${song.id}", metaStr)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("SoundWave", "Download failed for ${song.title}", e)
            com.soundwave.app.data.ErrorLog.log(appContext, "DOWNLOAD", "Failed for \"${song.title}\": ${e.message}")
            _playbackError.value = "Download failed for \"${song.title}\""
        } finally {
            _downloadingIds.value = _downloadingIds.value - song.id
        }
    }

    fun listDownloadedSongs(): List<Song> {
        return _downloadedIds.value.mapNotNull { id ->
            val metaStr = downloadsPrefs.getString("meta_$id", null) ?: return@mapNotNull null
            try {
                val song = jsonToSong(JSONObject(metaStr))
                // Point streamUrl at local file so it plays offline. If the
                // file is corrupt/too small, validLocalFileOrNull() removes
                // it from tracking and returns null, so it drops out of the
                // Downloads tab instead of sitting there as a broken entry.
                val localPath = validLocalFileOrNull(id)
                if (localPath != null) song.copy(streamUrl = "file://$localPath") else null
            } catch (e: Exception) { null }
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
