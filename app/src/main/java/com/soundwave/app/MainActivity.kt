package com.soundwave.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import com.soundwave.app.ui.screens.HomeScreen
import com.soundwave.app.ui.screens.LibraryScreen
import com.soundwave.app.ui.screens.LoginScreen
import com.soundwave.app.ui.screens.NowPlayingScreen
import com.soundwave.app.ui.screens.PlaylistDetailScreen
import com.soundwave.app.ui.screens.SearchScreen
import com.soundwave.app.ui.theme.SoundWaveTheme
import com.soundwave.app.ui.theme.SwSurfaceLight
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
        connectToPlaybackService()

        vm.controllerBridge = object : AppViewModel.ControllerBridge {
            override fun play(song: Song) {
                val url = song.streamUrl ?: return
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(song.thumbnail?.let { android.net.Uri.parse(it) })
                            .build()
                    )
                    .build()
                mediaController?.setMediaItem(mediaItem)
                mediaController?.prepare()
                mediaController?.play()
            }
            override fun togglePlayPause() {
                val c = mediaController ?: return
                if (c.isPlaying) c.pause() else c.play()
            }
            override fun pause() { mediaController?.pause() }
            override fun resume() { mediaController?.play() }
            override fun seekTo(seconds: Float) { mediaController?.seekTo((seconds * 1000).toLong()) }
        }

        setContent {
            SoundWaveTheme {
                AppRoot(vm, onSignInClick = { launchGoogleSignIn() })
            }
        }
    }

    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_ENDED) vm.onTrackEnded()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    vm.syncPlayingState(isPlaying)
                }
            })
            startProgressPolling()
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressPolling() {
        lifecycleScope.launch {
            while (true) {
                val c = mediaController
                if (c != null && c.duration > 0) {
                    vm.updateProgress(c.currentPosition / 1000f, c.duration / 1000f)
                }
                delay(500)
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

private enum class Tab { HOME, SEARCH, LIBRARY }

@Composable
private fun AppRoot(vm: AppViewModel, onSignInClick: () -> Unit) {
    val user by vm.user.collectAsState()
    val currentSong by vm.currentSong.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
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
    val sleepTimerMins by vm.sleepTimerMins.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(playbackError) {
        playbackError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearPlaybackError()
        }
    }

    var tab by remember { mutableStateOf(Tab.HOME) }
    var query by remember { mutableStateOf("") }
    var showNowPlaying by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var openAlbum by remember { mutableStateOf<SavedAlbum?>(null) }
    var showAlbumSearch by remember { mutableStateOf(false) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }

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
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                val pl = openPlaylist
                val al = openAlbum
                when {
                    pl != null -> PlaylistDetailScreen(
                        playlist = pl, currentSongId = currentSong?.id, likedIds = likedIds,
                        onBack = { openPlaylist = null },
                        onPlay = { song, q -> vm.playSong(song, q) }, onLike = { vm.toggleLike(it) },
                        onRemoveSong = { vm.removeFromPlaylist(pl.id, it.id) }
                    )
                    al != null -> AlbumDetailScreen(
                        album = al, currentSongId = currentSong?.id, likedIds = likedIds,
                        onBack = { openAlbum = null },
                        onPlay = { song, q -> vm.playSong(song, q) }, onLike = { vm.toggleLike(it) }
                    )
                    showAlbumSearch -> AlbumSearchScreen(
                        savedAlbumIds = savedAlbums.map { it.id }.toSet(),
                        onBack = { showAlbumSearch = false },
                        onSaveAlbum = { vm.saveAlbum(it); showAlbumSearch = false }
                    )
                    else -> when (tab) {
                        Tab.HOME -> HomeScreen(
                            user = user, recentlyPlayed = recentlyPlayed, currentSongId = currentSong?.id,
                            likedIds = likedIds, onPlay = { vm.playSong(it, recentlyPlayed) }, onLike = { vm.toggleLike(it) }
                        )
                        Tab.SEARCH -> SearchScreen(
                            query = query, onQueryChange = { query = it }, results = searchResults,
                            isSearching = isSearching, currentSongId = currentSong?.id, likedIds = likedIds,
                            onPlay = { vm.playSong(it, searchResults) }, onLike = { vm.toggleLike(it) },
                            onAddToPlaylist = { addToPlaylistSong = it }
                        )
                        Tab.LIBRARY -> LibraryScreen(
                            likedSongs = likedSongs, playlists = playlists, savedAlbums = savedAlbums,
                            downloadedSongs = vm.listDownloadedSongs(),
                            currentSongId = currentSong?.id, onPlay = { vm.playSong(it, likedSongs) },
                            onLike = { vm.toggleLike(it) }, onOpenPlaylist = { openPlaylist = it }, onOpenAlbum = { openAlbum = it },
                            onCreatePlaylist = { vm.createPlaylist(it) },
                            onDeletePlaylist = { vm.deletePlaylist(it.id) },
                            onRemoveAlbum = { vm.removeAlbum(it.id) },
                            onSearchAlbums = { showAlbumSearch = true }
                        )
                    }
                }
            }
            currentSong?.let { song ->
                Box(modifier = Modifier.padding(8.dp)) {
                    MiniPlayer(
                        song = song, isPlaying = isPlaying,
                        onTogglePlay = { vm.togglePlayPause() }, onNext = { vm.next() },
                        onClick = { showNowPlaying = true }
                    )
                }
            }
            NavigationBar(containerColor = SwSurfaceLight) {
                NavigationBarItem(
                    selected = tab == Tab.HOME, onClick = { tab = Tab.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") }, label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = tab == Tab.SEARCH, onClick = { tab = Tab.SEARCH },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") }, label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = tab == Tab.LIBRARY, onClick = { tab = Tab.LIBRARY },
                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Library") }, label = { Text("Library") }
                )
            }
        }

        if (showNowPlaying && currentSong != null) {
            NowPlayingScreen(
                song = currentSong!!, isPlaying = isPlaying, isLiked = likedIds.contains(currentSong!!.id),
                isDownloaded = downloadedIds.contains(currentSong!!.id),
                progress = progress, duration = duration, shuffle = shuffle, repeat = repeat,
                sleepTimerMins = sleepTimerMins,
                onClose = { showNowPlaying = false }, onTogglePlay = { vm.togglePlayPause() },
                onNext = { vm.next() }, onPrevious = { vm.previous() },
                onSeek = { vm.seekTo(it) }, onLike = { vm.toggleLike(currentSong!!) },
                onToggleShuffle = { vm.toggleShuffle() }, onCycleRepeat = { vm.cycleRepeat() },
                onToggleDownload = { vm.toggleDownload(currentSong!!) },
                onSetSleepTimer = { vm.setSleepTimer(it) }
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
