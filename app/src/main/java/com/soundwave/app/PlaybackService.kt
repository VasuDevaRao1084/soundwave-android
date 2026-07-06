package com.soundwave.app

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Runs as a real Android foreground service.
 * Unlike a WebView/iframe, this keeps playing audio when the app
 * is backgrounded, in Recents, or the screen is locked — because it
 * uses Android's actual media playback APIs, not a web page.
 */
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        com.soundwave.app.audio.AudioEffectsManager.init(this)

        // Attach EQ/BassBoost/Volume Boost the moment ExoPlayer allocates a real
        // audio session. This uses AnalyticsListener, which is purely observational
        // — it cannot affect playback, timing, or any of the Player.Listener logic
        // that already runs in MainActivity.
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                try {
                    com.soundwave.app.audio.AudioEffectsManager.attachToSession(this@PlaybackService, audioSessionId)
                } catch (e: Exception) {
                    android.util.Log.e("SoundWave", "Failed to attach audio effects (non-fatal)", e)
                }
            }
        })

        // Redundant safety net: the AnalyticsListener callback above only fires
        // on a session ID CHANGE. If ExoPlayer assigns the session ID before
        // that listener was registered (or the callback is simply unreliable
        // on a given device/OS version), the callback might never fire and
        // effects would silently never attach. This listener re-checks
        // player.audioSessionId directly — a plain getter that always reflects
        // whatever the real current session ID is — every time playback
        // actually becomes ready, which guarantees a valid session ID exists.
        // attachToSession() already no-ops if it's the same session it already
        // has, so calling this repeatedly is harmless.
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    try {
                        val sessionId = player.audioSessionId
                        if (sessionId > 0) {
                            com.soundwave.app.audio.AudioEffectsManager.attachToSession(this@PlaybackService, sessionId)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SoundWave", "Failed to attach audio effects on ready (non-fatal)", e)
                    }
                }
            }
        })

        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    fun loadAndPlay(audioUrl: String, title: String, artist: String, artworkUrl: String?) {
        val mediaItem = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun getPlayer(): ExoPlayer = player

    override fun onDestroy() {
        com.soundwave.app.audio.AudioEffectsManager.releaseEffects()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing when app is swiped away from Recents, matching Spotify-like behavior,
        // UNLESS playback has actually stopped/paused intentionally.
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }
}
