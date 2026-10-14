package com.example.model

enum class PlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED,
    STOPPED
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class PlayerState(
    val currentSong: Song? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isShuffle: Boolean = false,
    val isSleepTimerActive: Boolean = false,
    val sleepTimerRemainingSec: Int = 0,
    val sleepTimerInitialSec: Int = 0,
    val sleepTimerStopAtEndOfSong: Boolean = false,
    val activePlaylistName: String? = null
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING

    val progressFraction: Float
        get() = if (durationMs > 0) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedCurrentTime: String
        get() {
            val totalSeconds = currentPositionMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    val formattedTotalTime: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    val formattedSleepTimer: String
        get() {
            if (sleepTimerStopAtEndOfSong) return "End of track"
            val mins = sleepTimerRemainingSec / 60
            val secs = sleepTimerRemainingSec % 60
            return "%02d:%02d".format(mins, secs)
        }
}
