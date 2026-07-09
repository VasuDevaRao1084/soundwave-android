package com.soundwave.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.common.util.concurrent.MoreExecutors
import com.soundwave.app.auth.GoogleAuth
import com.soundwave.app.data.Playlist
import com.soundwave.app.data.SavedAlbum
import com.soundwave.app.data.Song
import com.soundwave.app.ui.components.AddToPlaylistSheet
import com.soundwave.app.ui.components.MiniPlayer
import com.soundwave.app.ui.screens.AlbumDetailScreen
import com.soundwave.app.ui.screens.AlbumSearchScreen
import com.soundwave.app.ui.screens.DiagnosticsScreen
import com.soundwave.app.ui.screens.SoundSettingsScreen
import com.soundwave.app.ui.screens.SongListScreen
import com.soundwave.app.ui.screens.HomeScreen
import com.soundwave.app.ui.screens.LibraryScreen
import com.soundwave.app.ui.screens.LoginScreen
import com.soundwave.app.ui.screens.NowPlayingScreen
import com.soundwave.app.ui.screens.PlaylistDetailScreen
import com.soundwave.app.ui.screens.ProfileScreen
import com.soundwave.app.ui.screens.SearchScreen
import com.soundwave.app.ui.theme.SoundWaveTheme
import com.soundwave.app.ui.theme.SwSurfaceLight
import com.soundwave.app.ui.theme.SwPurple
import androidx.compose.ui.graphics.Color
import com.soundwave.app.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var mediaController: MediaController? = null

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                lifecycleScope.launch {
                    val userId = com.soundwave.app.data.SupabaseAuth.signInWithGoogleIdToken(idToken)
                    val profile = GoogleAuth.toUserProfile(account)
                    vm.setUser(if (userId != null) profile.copy(id = userId) else profile)
                }
            }
        } catch (e: ApiException) {
            // Sign-in failed/cancelled — silently ignore, user stays on Login screen
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        showLastCrashIfAny()
        connectToPlaybackService()

        vm.controllerBridge = object : AppViewModel.ControllerBridge {
            override fun play(song: Song) {
                // Explicit user request to play this song — always start
                // audio, whether it's brand new or the same song re-tapped
                // while paused (e.g. the Home screen's "Jump Back In" card).
                playQueue(listOf(song), 0, autoPlay = true)
            }
            override fun playQueue(songs: List<Song>, startIndex: Int, autoPlay: Boolean) {
                val mediaItems = songs.mapNotNull { s ->
                    val url = s.streamUrl ?: return@mapNotNull null
                    MediaItem.Builder()
                        .setUri(url)
                        .setMediaId(s.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(s.title)
                                .setArtist(s.artist)
                                .setArtworkUri(s.thumbnail?.let { android.net.Uri.parse(it) })
                                .build()
                        )
                        .build()
                }
                if (mediaItems.isEmpty()) return
                val safeStartIndex = startIndex.coerceIn(0, mediaItems.size - 1)
                val c = mediaController ?: return
                val targetMediaId = mediaItems[safeStartIndex].mediaId
                val isSameSongAlreadyPlaying = c.currentMediaItem?.mediaId == targetMediaId

                // Background queue-window refresh (autoPlay=false) for the song
                // that's already loaded and playing: only insert the neighbor
                // songs around it using incremental add/remove, and never touch
                // the currently playing item itself. Calling setMediaItems()
                // here — even with the same media ID and a restored position —
                // still makes ExoPlayer tear down and reload the item, which is
                // exactly what caused the audible stutter/pause a second or two
                // into every song (the time it takes this background refresh to
                // finish resolving). This path avoids that entirely.
                if (!autoPlay && isSameSongAlreadyPlaying) {
                    val currentIdx = c.currentMediaItemIndex
                    val beforeItems = mediaItems.subList(0, safeStartIndex)
                    val afterItems = mediaItems.subList(safeStartIndex + 1, mediaItems.size)
                    if (currentIdx + 1 < c.mediaItemCount) c.removeMediaItems(currentIdx + 1, c.mediaItemCount)
                    if (currentIdx > 0) c.removeMediaItems(0, currentIdx)
                    // currently playing item is now at index 0, untouched and still playing
                    if (afterItems.isNotEmpty()) c.addMediaItems(1, afterItems)
                    if (beforeItems.isNotEmpty()) c.addMediaItems(0, beforeItems)
                    return
                }

                val currentPos = c.currentPosition
                val wasPlaying = c.isPlaying

                // Only preserve the current playback position if the song at
                // safeStartIndex is the SAME song already playing (e.g. this is
                // a background queue-window refresh from playSong(), not a brand
                // new song starting). Otherwise this carries the OLD song's
                // position onto the NEW song, making every new track start
                // partway through instead of at 0 — that was the bug.
                val startPositionMs = if (isSameSongAlreadyPlaying) currentPos.coerceAtLeast(0L) else 0L

                c.setMediaItems(mediaItems, safeStartIndex, startPositionMs)
                c.prepare()

                // autoPlay=true means this came from an explicit play() request
                // (the user tapped something) — always start playing, even if
                // it's the same song that's currently paused. autoPlay=false
                // means this is the silent background queue-window refresh, so
                // just keep whatever play/pause state already existed instead
                // of forcing a change the user didn't ask for.
                val shouldPlay = if (autoPlay) true else wasPlaying
                if (shouldPlay) c.play()
            }
            override fun togglePlayPause() {
                val c = mediaController ?: return
                if (c.isPlaying) c.pause() else c.play()
            }
            override fun pause() { mediaController?.pause() }
            override fun resume() { mediaController?.play() }
            override fun seekTo(seconds: Float) { mediaController?.seekTo((seconds * 1000).toLong()) }
            override fun skipToNext() { mediaController?.takeIf { it.hasNextMediaItem() }?.seekToNext() }
            override fun skipToPrevious() {
                val c = mediaController ?: return
                // If more than 3 seconds into the song, restart it (standard
                // music player behavior — matches Spotify/Apple Music), otherwise
                // go to the actual previous track.
                if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) {
                    c.seekTo(0)
                } else {
                    c.seekToPrevious()
                }
            }
        }

        setContent {
            SoundWaveTheme {
                AppRoot(vm, onSignInClick = { launchGoogleSignIn() })
            }
        }
    }

    // Catches any crash the app has, saves the full stack trace to a file
    // in app-private storage, then lets the OS handle it normally (so the
    // crash dialog / process death still happens as usual). On the NEXT
    // launch, showLastCrashIfAny() surfaces it. This exists because there's
    // no Android Studio/adb Logcat available to inspect crashes directly —
    // this makes the exact error visible on-device instead.
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                java.io.File(filesDir, "last_crash.txt").writeText(sw.toString())
                com.soundwave.app.data.ErrorLog.log(this, "CRASH", sw.toString().take(500))
            } catch (e: Exception) { /* best effort */ }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun showLastCrashIfAny() {
        val crashFile = java.io.File(filesDir, "last_crash.txt")
        if (!crashFile.exists()) return
        val text = try { crashFile.readText() } catch (e: Exception) { "" }
        crashFile.delete()
        if (text.isBlank()) return

        // Put the full trace on the clipboard so it can be pasted back to
        // whoever's debugging this, and show the first line as a quick Toast.
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SoundWave crash log", text))
        val firstLine = text.lineSequence().firstOrNull() ?: "Unknown error"
        android.widget.Toast.makeText(
            this,
            "App crashed last time: $firstLine\n(Full trace copied to clipboard — paste it to debug)",
            android.widget.Toast.LENGTH_LONG
        ).show()
        android.util.Log.e("SoundWaveCrash", text)
    }

    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    val stateName = when (state) {
                        androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                        androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                        androidx.media3.common.Player.STATE_READY -> "READY"
                        androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    android.util.Log.d("SoundWavePlayback", "State changed: $stateName")
                    // ExoPlayer auto-advances through setMediaItems() queue on its own.
                    // STATE_ENDED now means the entire queue finished (no more items),
                    // not a single-song end — repeat/queue-extension logic lives in onTrackEnded.
                    if (state == androidx.media3.common.Player.STATE_ENDED) vm.onTrackEnded()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    android.util.Log.d("SoundWavePlayback", "isPlaying changed: $isPlaying")
                    vm.syncPlayingState(isPlaying)
                }
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    // Fires whenever ExoPlayer moves to a different song in its queue —
                    // whether from our own app's Next/Previous, the system widget's
                    // skip buttons, headset controls, or natural auto-advance at the
                    // end of a track. Keeps the app's currentSong state truthful
                    // no matter which path triggered the change.
                    val mediaId = mediaItem?.mediaId ?: return
                    val queue = vm.queue.value
                    val matched = queue.firstOrNull { it.id == mediaId }
                        ?: vm.currentSong.value?.takeIf { it.id == mediaId }
                    if (matched != null) {
                        vm.syncQueuePosition(matched)
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // This is the key diagnostic — shows the EXACT reason playback failed
                    // (403 Forbidden, 404 Not Found, unsupported format, network timeout, etc.)
                    val msg = "Playback error: ${error.errorCodeName} — ${error.message}"
                    android.util.Log.e("SoundWavePlayback", msg, error)
                    com.soundwave.app.data.ErrorLog.log(this@MainActivity, "PLAYBACK", msg)
                    android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            })
            startProgressPolling()
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressPolling() {
        lifecycleScope.launch {
            while (true) {
                val c = mediaController
                if (c != null) {
                    // ExoPlayer's c.duration can stay C.TIME_UNSET (-1) for a while
                    // after loading a new stream URL, especially for YouTube audio
                    // streams that don't expose duration in headers immediately.
                    // Fall back to the song's known duration from the API response
                    // so the seek bar / time display isn't stuck at 0:00.
                    val exoDuration = c.duration
                    val fallbackDuration = vm.currentSong.value?.durationSec?.toFloat() ?: 0f
                    val effectiveDuration = if (exoDuration > 0) exoDuration / 1000f else fallbackDuration

                    if (effectiveDuration > 0) {
                        val position = c.currentPosition.takeIf { it >= 0 } ?: 0L
                        vm.updateProgress(position / 1000f, effectiveDuration)
                    }

                    // Smooth transition: gently lowers volume in the last few
                    // seconds of a track and restores it once the next one
                    // starts, instead of an abrupt cut. This ONLY ever reads/
                    // sets c.volume — it never touches the queue, position, or
                    // play/pause state, so it can't affect any of the playback
                    // fixes already in place. Fully skipped if the setting is off.
                    if (vm.smoothTransitionsEnabled.value && exoDuration > 0 && c.isPlaying) {
                        val fadeWindowMs = 3000L
                        val remainingMs = exoDuration - c.currentPosition
                        if (remainingMs in 0..fadeWindowMs) {
                            val target = (remainingMs.toFloat() / fadeWindowMs).coerceIn(0f, 1f)
                            if (kotlin.math.abs(c.volume - target) > 0.02f) c.volume = target
                        } else if (c.volume < 1f) {
                            // Ramp back up — after a transition, or if the user
                            // seeked away from the end mid-fade.
                            c.volume = (c.volume + 0.08f).coerceAtMost(1f)
                        }
                    }
                }
                delay(200)
            }
        }
    }

    private fun launchGoogleSignIn() {
        signInLauncher.launch(GoogleAuth.client(this).signInIntent)
    }

    override fun onDestroy() {
        mediaController?.release()
        super.onDestroy()
    }
}

private enum class Tab { HOME, SEARCH, LIBRARY, ALBUMS }

@Composable
private fun AppRoot(vm: AppViewModel, onSignInClick: () -> Unit) {
    val user by vm.user.collectAsState()
    val topTelugu by vm.topTelugu.collectAsState()
    val topHindi by vm.topHindi.collectAsState()
    val topEnglish by vm.topEnglish.collectAsState()
    val mostSearchedTelugu by vm.mostSearchedTelugu.collectAsState()
    val mostSearchedHindi by vm.mostSearchedHindi.collectAsState()
    val mostSearchedEnglish by vm.mostSearchedEnglish.collectAsState()
    val moodPlaylist by vm.moodPlaylist.collectAsState()
    val audioQuality by vm.audioQuality.collectAsState()
    val smoothTransitionsEnabled by vm.smoothTransitionsEnabled.collectAsState()
    val currentSong by vm.currentSong.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val queue by vm.queue.collectAsState()
    val queueIndex by vm.queueIndex.collectAsState()
    val recommendedSongs by vm.recommendedSongs.collectAsState()
    val likedSongs by vm.likedSongs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val savedAlbums by vm.savedAlbums.collectAsState()
    val recentlyPlayed by vm.recentlyPlayed.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val repeat by vm.repeat.collectAsState()
    val progress by vm.progress.collectAsState()
    val duration by vm.duration.collectAsState()
    val playbackError by vm.playbackError.collectAsState()
    val downloadedIds by vm.downloadedIds.collectAsState()
    val downloadingIds by vm.downloadingIds.collectAsState()
    val searchHistory by vm.searchHistory.collectAsState()
    val sleepTimerMins by vm.sleepTimerMins.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(playbackError) {
        playbackError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            vm.clearPlaybackError()
        }
    }

    var tab by remember { mutableStateOf(Tab.HOME) }
    var query by remember { mutableStateOf("") }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var openAlbum by remember { mutableStateOf<SavedAlbum?>(null) }
    var showAlbumSearch by remember { mutableStateOf(false) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showSoundSettings by remember { mutableStateOf(false) }
    var openChart by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var avatarRefreshTick by remember { mutableStateOf(0) }

    fun goToTab(t: Tab) {
        showSoundSettings = false
        showDiagnostics = false
        showAlbumSearch = false
        showProfile = false
        openAlbum = null
        openPlaylist = null
        openChart = null
        vm.closeMoodPlaylist()
        tab = t
    }

    val likedIds = remember(likedSongs) { likedSongs.map { it.id }.toSet() }

    LaunchedEffect(query) {
        delay(400)
        vm.search(query)
    }

    if (user == null) {
        LoginScreen(isLoading = isSigningIn, onSignInClick = {
            isSigningIn = true
            onSignInClick()
        })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                val pl = openPlaylist
                val al = openAlbum
                when {
                    pl != null -> PlaylistDetailScreen(
                        playlist = pl, currentSongId = currentSong?.id, isAudioPlaying = isPlaying, likedIds = likedIds,
                        onBack = { openPlaylist = null },
                        onPlay = { song, q -> vm.playSong(song, q) }, onLike = { vm.toggleLike(it) },
                        onRemoveSong = { vm.removeFromPlaylist(pl.id, it.id) }
                    )
                    al != null -> AlbumDetailScreen(
                        album = al, currentSongId = currentSong?.id, isAudioPlaying = isPlaying, likedIds = likedIds,
                        onBack = { openAlbum = null },
                        onPlay = { song, q -> vm.playSong(song, q) }, onLike = { vm.toggleLike(it) },
                        onAddToPlaylist = { addToPlaylistSong = it }
                    )
                    showAlbumSearch -> AlbumSearchScreen(
                        savedAlbumIds = savedAlbums.map { it.id }.toSet(),
                        onBack = { showAlbumSearch = false },
                        onSaveAlbum = { vm.saveAlbum(it); showAlbumSearch = false },
                        onOpenAlbum = { openAlbum = it; showAlbumSearch = false }
                    )
                    showProfile -> ProfileScreen(
                        user = user,
                        onBack = { showProfile = false },
                        onSignOut = {
                            GoogleAuth.client(context).signOut()
                            vm.signOut()
                            showProfile = false
                        },
                        onAvatarUpdated = { avatarRefreshTick++ }
                    )
                    showDiagnostics -> {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        DiagnosticsScreen(
                            entries = remember(showDiagnostics) { com.soundwave.app.data.ErrorLog.getAll(context) },
                            onBack = { showDiagnostics = false },
                            onCopyAll = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SoundWave diagnostics", com.soundwave.app.data.ErrorLog.asPlainText(context)))
                                android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onClear = { com.soundwave.app.data.ErrorLog.clear(context); showDiagnostics = false }
                        )
                    }
                    showSoundSettings -> SoundSettingsScreen(
                        audioQuality = audioQuality,
                        onSetAudioQuality = { vm.setAudioQuality(it) },
                        smoothTransitionsEnabled = smoothTransitionsEnabled,
                        onSetSmoothTransitions = { vm.setSmoothTransitions(it) },
                        onBack = {
                            showSoundSettings = false
                            if (currentSong != null) showNowPlaying = true
                        }
                    )
                    moodPlaylist != null -> SongListScreen(
                        title = moodPlaylist!!.title,
                        subtitle = "Mood playlist",
                        songs = moodPlaylist!!.songs,
                        isLoading = moodPlaylist!!.isLoading,
                        currentSongId = currentSong?.id, isAudioPlaying = isPlaying, likedIds = likedIds,
                        onPlay = { song, q -> vm.playSong(song, q) },
                        onLike = { vm.toggleLike(it) },
                        onAddToPlaylist = { addToPlaylistSong = it },
                        onBack = { vm.closeMoodPlaylist() }
                    )
                    openChart != null -> SongListScreen(
                        title = openChart!!.first,
                        subtitle = "Top 30 by popularity",
                        songs = openChart!!.second,
                        currentSongId = currentSong?.id, isAudioPlaying = isPlaying, likedIds = likedIds,
                        onPlay = { song, q -> vm.playSong(song, q) },
                        onLike = { vm.toggleLike(it) },
                        onAddToPlaylist = { addToPlaylistSong = it },
                        onBack = { openChart = null }
                    )
                    else -> when (tab) {
                        Tab.HOME -> HomeScreen(
                            user = user, recentlyPlayed = recentlyPlayed,
                            savedAlbums = savedAlbums,
                            recommendedSongs = recommendedSongs,
                            topTelugu = topTelugu,
                            topHindi = topHindi,
                            topEnglish = topEnglish,
                            mostSearchedTelugu = mostSearchedTelugu,
                            mostSearchedHindi = mostSearchedHindi,
                            mostSearchedEnglish = mostSearchedEnglish,
                            currentSongId = currentSong?.id,
                            isAudioPlaying = isPlaying,
                            likedIds = likedIds,
                            onPlay = { vm.playSong(it, recentlyPlayed) },
                            onLike = { vm.toggleLike(it) },
                            onSearchAlbums = { showAlbumSearch = true },
                            onOpenAlbum = { openAlbum = it },
                            onOpenDiagnostics = { showDiagnostics = true },
                            onMoodClick = { title, moodQuery -> vm.openMoodPlaylist(title, moodQuery) },
                            onOpenChart = { title, songs -> openChart = title to songs },
                            avatarRefreshTick = avatarRefreshTick,
                            onOpenProfile = { showProfile = true }
                        )
                        Tab.ALBUMS -> AlbumSearchScreen(
                            savedAlbumIds = savedAlbums.map { it.id }.toSet(),
                            onBack = { tab = Tab.HOME },
                            onSaveAlbum = { vm.saveAlbum(it) },
                            onOpenAlbum = { openAlbum = it }
                        )
                        Tab.SEARCH -> SearchScreen(
                            query = query, onQueryChange = { query = it }, results = searchResults,
                            isSearching = isSearching, searchHistory = searchHistory,
                            currentSongId = currentSong?.id, isAudioPlaying = isPlaying, likedIds = likedIds,
                            onPlay = { vm.playSong(it, searchResults) }, onLike = { vm.toggleLike(it) },
                            onAddToPlaylist = { addToPlaylistSong = it },
                            onClearHistory = { vm.clearSearchHistory() }
                        )
                        Tab.LIBRARY -> LibraryScreen(
                            likedSongs = likedSongs, playlists = playlists, savedAlbums = savedAlbums,
                            downloadedSongs = vm.listDownloadedSongs(),
                            currentSongId = currentSong?.id, isAudioPlaying = isPlaying, onPlay = { song, q -> vm.playSong(song, q) },
                            onLike = { vm.toggleLike(it) }, onOpenPlaylist = { openPlaylist = it }, onOpenAlbum = { openAlbum = it },
                            onCreatePlaylist = { vm.createPlaylist(it) },
                            onDeletePlaylist = { vm.deletePlaylist(it.id) },
                            onRemoveAlbum = { vm.removeAlbum(it.id) },
                            onSearchAlbums = { showAlbumSearch = true },
                            onAddToPlaylist = { addToPlaylistSong = it }
                        )
                    }
                }
            }
            currentSong?.let { song ->
                Box(modifier = Modifier.padding(start = 0.dp, end = 0.dp, bottom = 0.dp, top = 0.dp)) {
                    MiniPlayer(
                        song = song,
                        isPlaying = isPlaying,
                        isLiked = likedIds.contains(song.id),
                        onTogglePlay = { vm.togglePlayPause() },
                        onNext = { vm.next() },
                        onLike = { vm.toggleLike(song) },
                        onClick = { showNowPlaying = true }
                    )
                }
            }
            NavigationBar(
                containerColor = Color(0xFF0D0B18),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = tab == Tab.HOME, onClick = { goToTab(Tab.HOME) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SwPurple, selectedTextColor = SwPurple,
                        unselectedIconColor = Color(0xFF4A4560), unselectedTextColor = Color(0xFF4A4560),
                        indicatorColor = Color(0xFF1A1530)
                    )
                )
                NavigationBarItem(
                    selected = tab == Tab.SEARCH, onClick = { goToTab(Tab.SEARCH) },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    label = { Text("Search") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SwPurple, selectedTextColor = SwPurple,
                        unselectedIconColor = Color(0xFF4A4560), unselectedTextColor = Color(0xFF4A4560),
                        indicatorColor = Color(0xFF1A1530)
                    )
                )
                NavigationBarItem(
                    selected = tab == Tab.LIBRARY, onClick = { goToTab(Tab.LIBRARY) },
                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Library") },
                    label = { Text("Library") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SwPurple, selectedTextColor = SwPurple,
                        unselectedIconColor = Color(0xFF4A4560), unselectedTextColor = Color(0xFF4A4560),
                        indicatorColor = Color(0xFF1A1530)
                    )
                )
                NavigationBarItem(
                    selected = tab == Tab.ALBUMS, onClick = { goToTab(Tab.ALBUMS) },
                    icon = { Icon(Icons.Filled.Album, contentDescription = "Albums") },
                    label = { Text("Albums") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SwPurple, selectedTextColor = SwPurple,
                        unselectedIconColor = Color(0xFF4A4560), unselectedTextColor = Color(0xFF4A4560),
                        indicatorColor = Color(0xFF1A1530)
                    )
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showNowPlaying && currentSong != null,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
        ) {
            currentSong?.let { song ->
                NowPlayingScreen(
                    song = song, isPlaying = isPlaying, isLiked = likedIds.contains(song.id),
                    isDownloaded = downloadedIds.contains(song.id),
                    isDownloading = downloadingIds.contains(song.id),
                    progress = progress, duration = duration, shuffle = shuffle, repeat = repeat,
                    sleepTimerMins = sleepTimerMins,
                    onClose = { showNowPlaying = false }, onTogglePlay = { vm.togglePlayPause() },
                    onNext = { vm.next() }, onPrevious = { vm.previous() },
                    onSeek = { vm.seekTo(it) }, onLike = { vm.toggleLike(song) },
                    onToggleShuffle = { vm.toggleShuffle() }, onCycleRepeat = { vm.cycleRepeat() },
                    onToggleDownload = { vm.toggleDownload(song) },
                    onSetSleepTimer = { vm.setSleepTimer(it) },
                    onShowQueue = { showQueue = true },
                    onAddToPlaylist = { addToPlaylistSong = song },
                    onOpenSoundSettings = {
                        // NowPlayingScreen renders as an overlay ON TOP of the main
                        // content area (including Sound Settings) — so opening
                        // Settings without also collapsing this overlay left it
                        // sitting there fully blocking the screen underneath, even
                        // though Settings had genuinely opened. Collapsing to mini
                        // player here reveals it immediately, and onBack below
                        // restores the full player.
                        showNowPlaying = false
                        showSoundSettings = true
                    }
                )
            }
        }

        if (showQueue) {
            com.soundwave.app.ui.components.QueueSheet(
                queue = queue,
                currentSongId = currentSong?.id,
                queueIndex = queueIndex,
                onDismiss = { showQueue = false },
                onPlaySong = { vm.playSong(it, queue) }
            )
        }

        addToPlaylistSong?.let { song ->
            AddToPlaylistSheet(
                playlists = playlists,
                onDismiss = { addToPlaylistSong = null },
                onPick = { vm.addToPlaylist(it.id, song); addToPlaylistSong = null },
                onCreateNew = { name -> vm.createPlaylist(name); addToPlaylistSong = null }
            )
        }
    }
}
