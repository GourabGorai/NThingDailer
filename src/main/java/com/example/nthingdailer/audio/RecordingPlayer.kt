package com.example.nthingdailer.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import java.io.File

object RecordingPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingId: String? = null

    fun play(context: Context, id: String, path: String, onComplete: () -> Unit) {
        if (currentPlayingId == id && mediaPlayer?.isPlaying == true) {
            stop()
            return
        }

        stop()

        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            AudioFileGenerator.generateSampleWavFile(file)
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                prepare()
                start()
                setOnCompletionListener {
                    onComplete()
                    stop()
                }
            }
            currentPlayingId = id
            Toast.makeText(context, "Playing recording...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error playing audio: ${e.message}", Toast.LENGTH_SHORT).show()
            onComplete()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentPlayingId = null
    }
}
