package com.example

import com.example.model.PlaybackStatus
import com.example.model.PlayerState
import com.example.model.Playlist
import com.example.model.RepeatMode
import com.example.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testSongFormattedDuration() {
        val song = Song(
            id = "1",
            title = "Test Track",
            filePath = "/path/test.mp3",
            fileUriString = "content://test",
            playlistName = "Chill",
            durationMs = 185000L // 3 minutes 5 seconds
        )
        assertEquals("03:05", song.formattedDuration)
    }

    @Test
    fun testPlayerStateProgressAndSleepTimer() {
        val state = PlayerState(
            currentPositionMs = 60000L,
            durationMs = 120000L,
            status = PlaybackStatus.PLAYING,
            repeatMode = RepeatMode.ALL,
            isShuffle = false,
            isSleepTimerActive = true,
            sleepTimerRemainingSec = 870 // 14 mins 30 secs
        )

        assertEquals(0.5f, state.progressFraction, 0.01f)
        assertEquals("01:00", state.formattedCurrentTime)
        assertEquals("02:00", state.formattedTotalTime)
        assertEquals("14:30", state.formattedSleepTimer)
        assertTrue(state.isPlaying)
    }

    @Test
    fun testPlaylistFormatting() {
        val playlist = Playlist(
            id = "chill",
            name = "Ambient Chill",
            folderPath = "/storage/AuraPlaylists/Ambient Chill",
            songCount = 4,
            totalDurationMs = 3600000L // 1 hour
        )
        assertEquals("1h 0m", playlist.formattedDuration)
    }
}
