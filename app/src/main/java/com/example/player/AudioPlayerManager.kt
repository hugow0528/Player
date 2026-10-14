package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.example.model.PlaybackStatus
import com.example.model.PlayerState
import com.example.model.RepeatMode
import com.example.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var originalQueue: List<Song> = emptyList()
    private var activeQueue: List<Song> = emptyList()
    private var currentIndex: Int = -1

    init {
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        releasePlayer()
        mediaPlayer = MediaPlayer().apply {
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                handleSongCompletion()
            }
            setOnErrorListener { _, what, extra ->
                Log.e("AudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                _playerState.update { it.copy(status = PlaybackStatus.PAUSED) }
                true
            }
        }
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int = 0, playlistName: String? = null) {
        if (songs.isEmpty()) return
        originalQueue = songs
        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        val selectedSong = songs[validIndex]

        if (_playerState.value.isShuffle) {
            val shuffled = songs.toMutableList()
            shuffled.remove(selectedSong)
            shuffled.shuffle()
            activeQueue = listOf(selectedSong) + shuffled
            currentIndex = 0
        } else {
            activeQueue = songs
            currentIndex = validIndex
        }

        playSongInternal(selectedSong, playlistName)
    }

    fun playSong(song: Song, playlistSongs: List<Song> = emptyList(), playlistName: String? = null) {
        val targetList = if (playlistSongs.isNotEmpty()) playlistSongs else listOf(song)
        val idx = targetList.indexOfFirst { it.id == song.id }.let { if (it == -1) 0 else it }
        playPlaylist(targetList, idx, playlistName ?: song.playlistName)
    }

    private fun playSongInternal(song: Song, playlistName: String? = null) {
        try {
            initMediaPlayer()
            val mp = mediaPlayer ?: return

            val file = File(song.filePath)
            if (file.exists()) {
                mp.setDataSource(file.absolutePath)
            } else {
                mp.setDataSource(context, Uri.parse(song.fileUriString))
            }

            mp.prepare()
            val duration = mp.duration.toLong().coerceAtLeast(song.durationMs)
            mp.start()
            mp.setVolume(1.0f, 1.0f)

            _playerState.update {
                it.copy(
                    currentSong = song,
                    status = PlaybackStatus.PLAYING,
                    currentPositionMs = 0L,
                    durationMs = duration,
                    activePlaylistName = playlistName ?: it.activePlaylistName
                )
            }

            startProgressTracking()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing song: ${song.title}", e)
            _playerState.update { it.copy(status = PlaybackStatus.PAUSED) }
        }
    }

    fun resume() {
        mediaPlayer?.let { mp ->
            if (!mp.isPlaying) {
                mp.start()
                _playerState.update { it.copy(status = PlaybackStatus.PLAYING) }
                startProgressTracking()
            }
        } ?: run {
            _playerState.value.currentSong?.let {
                playSong(it, activeQueue, _playerState.value.activePlaylistName)
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _playerState.update {
                    it.copy(
                        status = PlaybackStatus.PAUSED,
                        currentPositionMs = mp.currentPosition.toLong()
                    )
                }
            }
        }
        stopProgressTracking()
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun stop() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.stop()
            }
        }
        stopProgressTracking()
        _playerState.update {
            it.copy(
                status = PlaybackStatus.STOPPED,
                currentPositionMs = 0L
            )
        }
    }

    fun skipNext() {
        if (activeQueue.isEmpty()) return

        if (_playerState.value.repeatMode == RepeatMode.ONE) {
            seekTo(0)
            resume()
            return
        }

        val nextIndex = currentIndex + 1
        if (nextIndex < activeQueue.size) {
            currentIndex = nextIndex
            playSongInternal(activeQueue[currentIndex], _playerState.value.activePlaylistName)
        } else {
            // Reached end of playlist
            if (_playerState.value.repeatMode == RepeatMode.ALL) {
                currentIndex = 0
                playSongInternal(activeQueue[currentIndex], _playerState.value.activePlaylistName)
            } else {
                stop()
            }
        }
    }

    fun skipPrevious() {
        if (activeQueue.isEmpty()) return

        val mp = mediaPlayer
        if (mp != null && mp.currentPosition > 3000) {
            // If already played more than 3 seconds, restart current track
            seekTo(0)
            resume()
            return
        }

        val prevIndex = currentIndex - 1
        if (prevIndex >= 0) {
            currentIndex = prevIndex
            playSongInternal(activeQueue[currentIndex], _playerState.value.activePlaylistName)
        } else {
            // At first song, loop to last if repeat all
            if (_playerState.value.repeatMode == RepeatMode.ALL) {
                currentIndex = activeQueue.size - 1
                playSongInternal(activeQueue[currentIndex], _playerState.value.activePlaylistName)
            } else {
                seekTo(0)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            val safePos = positionMs.coerceIn(0L, _playerState.value.durationMs).toInt()
            mp.seekTo(safePos)
            _playerState.update { it.copy(currentPositionMs = safePos.toLong()) }
        }
    }

    fun toggleRepeatMode() {
        val nextMode = when (_playerState.value.repeatMode) {
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
            RepeatMode.OFF -> RepeatMode.ALL
        }
        _playerState.update { it.copy(repeatMode = nextMode) }
    }

    fun toggleShuffle() {
        val currentSong = _playerState.value.currentSong
        val newShuffle = !_playerState.value.isShuffle

        if (newShuffle && originalQueue.isNotEmpty()) {
            val shuffled = originalQueue.toMutableList()
            if (currentSong != null) {
                shuffled.remove(currentSong)
                shuffled.shuffle()
                activeQueue = listOf(currentSong) + shuffled
                currentIndex = 0
            } else {
                shuffled.shuffle()
                activeQueue = shuffled
                currentIndex = 0
            }
        } else {
            activeQueue = originalQueue
            if (currentSong != null) {
                currentIndex = originalQueue.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
            }
        }

        _playerState.update { it.copy(isShuffle = newShuffle) }
    }

    private fun handleSongCompletion() {
        // Sleep timer check: stop at end of song
        if (_playerState.value.sleepTimerStopAtEndOfSong) {
            cancelSleepTimer()
            stop()
            return
        }

        when (_playerState.value.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0)
                resume()
            }
            RepeatMode.ALL -> {
                skipNext()
            }
            RepeatMode.OFF -> {
                if (currentIndex + 1 < activeQueue.size) {
                    skipNext()
                } else {
                    stop()
                }
            }
        }
    }

    // --- Sleep Timer ---

    fun setSleepTimerMinutes(minutes: Int) {
        val seconds = minutes * 60
        startSleepTimerSeconds(seconds, stopAtEndOfSong = false)
    }

    fun setSleepTimerEndOfSong() {
        sleepTimerJob?.cancel()
        _playerState.update {
            it.copy(
                isSleepTimerActive = true,
                sleepTimerRemainingSec = 0,
                sleepTimerInitialSec = 0,
                sleepTimerStopAtEndOfSong = true
            )
        }
    }

    fun addSleepTimerMinutes(extraMinutes: Int) {
        if (!_playerState.value.isSleepTimerActive) {
            setSleepTimerMinutes(extraMinutes)
            return
        }
        val currentRemaining = _playerState.value.sleepTimerRemainingSec
        val newRemaining = currentRemaining + (extraMinutes * 60)
        startSleepTimerSeconds(newRemaining, stopAtEndOfSong = false)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        mediaPlayer?.setVolume(1.0f, 1.0f)
        _playerState.update {
            it.copy(
                isSleepTimerActive = false,
                sleepTimerRemainingSec = 0,
                sleepTimerInitialSec = 0,
                sleepTimerStopAtEndOfSong = false
            )
        }
    }

    private fun startSleepTimerSeconds(totalSeconds: Int, stopAtEndOfSong: Boolean) {
        sleepTimerJob?.cancel()
        _playerState.update {
            it.copy(
                isSleepTimerActive = true,
                sleepTimerRemainingSec = totalSeconds,
                sleepTimerInitialSec = totalSeconds,
                sleepTimerStopAtEndOfSong = stopAtEndOfSong
            )
        }

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--

                // Smooth gradual volume fade out in final 10 seconds
                if (remaining <= 10) {
                    val fadeFraction = (remaining.toFloat() / 10f).coerceIn(0f, 1f)
                    mediaPlayer?.setVolume(fadeFraction, fadeFraction)
                }

                _playerState.update { it.copy(sleepTimerRemainingSec = remaining) }
            }

            if (isActive) {
                // Timer finished!
                pause()
                mediaPlayer?.setVolume(1.0f, 1.0f)
                cancelSleepTimer()
            }
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.toLong()
                        val dur = mp.duration.toLong()
                        _playerState.update {
                            it.copy(
                                currentPositionMs = pos,
                                durationMs = if (dur > 0) dur else it.durationMs
                            )
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun getQueue(): List<Song> = activeQueue

    fun releasePlayer() {
        stopProgressTracking()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
    }
}
