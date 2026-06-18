package com.soundwave.app

import android.content.ComponentName
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.soundwave.app.data.SaavnApi
import com.soundwave.app.data.Song
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var mediaController: MediaController? = null
    private var currentSong: Song? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectToPlaybackService()
        setupSearch()
    }

    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
        }, MoreExecutors.directExecutor())
    }

    private fun setupSearch() {
        val searchBox = findViewById<android.widget.EditText>(R.id.searchBox)
        val resultsList = findViewById<android.widget.ListView>(R.id.resultsList)

        searchBox.setOnEditorActionListener { _, _, _ ->
            val query = searchBox.text.toString()
            if (query.isNotBlank()) {
                lifecycleScope.launch {
                    try {
                        val songs = SaavnApi.search(query)
                        val titles = songs.map { "${it.title} — ${it.artist}" }
                        val adapter = android.widget.ArrayAdapter(
                            this@MainActivity,
                            android.R.layout.simple_list_item_1,
                            titles
                        )
                        resultsList.adapter = adapter
                        resultsList.setOnItemClickListener { _, _, position, _ ->
                            playSong(songs[position])
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Search failed: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            true
        }
    }

    private fun playSong(song: Song) {
        currentSong = song
        if (song.streamUrl == null) {
            android.widget.Toast.makeText(this, "No stream available for this song", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(song.streamUrl)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
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

    override fun onDestroy() {
        mediaController?.release()
        super.onDestroy()
    }
}
