package com.example.engine

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.model.AudioTrackItem
import kotlin.math.abs

class MediaAudioPlayer(private val context: Context) {

    private val players = mutableMapOf<String, MediaPlayer>()
    private val preparedIds = mutableSetOf<String>()

    fun sync(
        tracks: List<AudioTrackItem>,
        currentTimeSec: Float,
        isPlaying: Boolean,
        isMuted: Boolean
    ) {
        val activeIds = tracks.map { it.id }.toSet()
        val staleIds = players.keys.filter { it !in activeIds }
        staleIds.forEach { id ->
            runCatching { players.remove(id)?.release() }
            preparedIds.remove(id)
        }

        for (track in tracks) {
            val player = players.getOrPut(track.id) {
                MediaPlayer().apply {
                    runCatching {
                        setDataSource(context, Uri.parse(track.uri))
                        setOnPreparedListener { mp ->
                            preparedIds.add(track.id)
                            val vol = if (isMuted) 0f else track.volume.coerceIn(0f, 1f)
                            mp.setVolume(vol, vol)
                        }
                        prepareAsync()
                    }
                }
            }

            if (track.id !in preparedIds) continue

            val vol = if (isMuted) 0f else track.volume.coerceIn(0f, 1f)
            runCatching { player.setVolume(vol, vol) }

            val inWindow = currentTimeSec >= track.startSec && currentTimeSec <= track.endSec
            val targetMediaMs = ((currentTimeSec - track.startSec + track.trimStartSec) * 1000f)
                .toInt()
                .coerceAtLeast(0)

            if (isPlaying && inWindow) {
                runCatching {
                    val drift = abs(player.currentPosition - targetMediaMs)
                    if (drift > 280) {
                        player.seekTo(targetMediaMs)
                    }
                    if (!player.isPlaying && targetMediaMs < player.duration) {
                        player.start()
                    }
                }
            } else {
                runCatching {
                    if (player.isPlaying) player.pause()
                    if (inWindow && abs(player.currentPosition - targetMediaMs) > 250) {
                        player.seekTo(targetMediaMs)
                    }
                }
            }
        }
    }

    fun pauseAll() {
        players.values.forEach { mp ->
            runCatching { if (mp.isPlaying) mp.pause() }
        }
    }

    fun release() {
        players.values.forEach { mp -> runCatching { mp.release() } }
        players.clear()
        preparedIds.clear()
    }
}
