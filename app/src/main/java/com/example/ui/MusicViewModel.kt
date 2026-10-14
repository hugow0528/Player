package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicStorageManager
import com.example.model.Playlist
import com.example.model.Song
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val storageManager = MusicStorageManager(application)
    val playerManager = AudioPlayerManager(application)

    val playerState = playerManager.playerState

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _selectedPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val selectedPlaylistSongs: StateFlow<List<Song>> = _selectedPlaylistSongs.asStateFlow()

    private val _rootFolderPath = MutableStateFlow("")
    val rootFolderPath: StateFlow<String> = _rootFolderPath.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _isSleepTimerSheetOpen = MutableStateFlow(false)
    val isSleepTimerSheetOpen: StateFlow<Boolean> = _isSleepTimerSheetOpen.asStateFlow()

    private val _isFolderInfoDialogOpen = MutableStateFlow(false)
    val isFolderInfoDialogOpen: StateFlow<Boolean> = _isFolderInfoDialogOpen.asStateFlow()

    private val _isQueueSheetOpen = MutableStateFlow(false)
    val isQueueSheetOpen: StateFlow<Boolean> = _isQueueSheetOpen.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Filtered songs according to search query
    val filteredSelectedPlaylistSongs = combine(_selectedPlaylistSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAllSongs = combine(_allSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.playlistName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isLoading.value = true
            storageManager.ensureInitialData()
            _rootFolderPath.value = storageManager.getRootFolderPath()
            val list = storageManager.loadPlaylists()
            _playlists.value = list
            val songs = storageManager.loadAllSongs()
            _allSongs.value = songs

            val currentSelected = _selectedPlaylist.value
            if (currentSelected != null) {
                val updated = list.find { it.name == currentSelected.name }
                _selectedPlaylist.value = updated
                _selectedPlaylistSongs.value = if (updated != null) {
                    storageManager.loadSongsForPlaylist(updated.name)
                } else emptyList()
            }
            _isLoading.value = false
        }
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
        _searchQuery.value = ""
        if (playlist != null) {
            viewModelScope.launch {
                _selectedPlaylistSongs.value = storageManager.loadSongsForPlaylist(playlist.name)
            }
        } else {
            _selectedPlaylistSongs.value = emptyList()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    fun setSleepTimerSheetOpen(open: Boolean) {
        _isSleepTimerSheetOpen.value = open
    }

    fun setFolderInfoDialogOpen(open: Boolean) {
        _isFolderInfoDialogOpen.value = open
    }

    fun setQueueSheetOpen(open: Boolean) {
        _isQueueSheetOpen.value = open
    }

    fun clearStatusNotice() {
        _statusNotice.value = null
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val success = storageManager.createPlaylist(name)
            if (success) {
                _statusNotice.value = "Playlist \"$name\" created with subfolder!"
                refreshAll()
            } else {
                _statusNotice.value = "Playlist already exists or name is invalid."
            }
        }
    }

    fun renamePlaylist(oldName: String, newName: String) {
        viewModelScope.launch {
            val success = storageManager.renamePlaylist(oldName, newName)
            if (success) {
                _statusNotice.value = "Playlist renamed to \"$newName\""
                if (_selectedPlaylist.value?.name == oldName) {
                    _selectedPlaylist.value = _selectedPlaylist.value?.copy(name = newName)
                }
                refreshAll()
            } else {
                _statusNotice.value = "Failed to rename playlist folder."
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val success = storageManager.deletePlaylist(playlist.name)
            if (success) {
                _statusNotice.value = "Deleted playlist and its folder: ${playlist.name}"
                if (_selectedPlaylist.value?.id == playlist.id) {
                    _selectedPlaylist.value = null
                    _selectedPlaylistSongs.value = emptyList()
                }
                refreshAll()
            }
        }
    }

    fun importAudioFiles(uris: List<Uri>, targetPlaylistName: String?) {
        val playlist = targetPlaylistName ?: _selectedPlaylist.value?.name ?: _playlists.value.firstOrNull()?.name ?: "My Music"
        viewModelScope.launch {
            _isLoading.value = true
            val imported = storageManager.importAudioFiles(playlist, uris)
            _isLoading.value = false
            if (imported.isNotEmpty()) {
                _statusNotice.value = "Imported ${imported.size} audio file(s) into \"$playlist\" folder!"
                refreshAll()
                if (_selectedPlaylist.value?.name == playlist) {
                    _selectedPlaylistSongs.value = storageManager.loadSongsForPlaylist(playlist)
                }
            } else {
                _statusNotice.value = "No supported audio files could be imported."
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            val success = storageManager.deleteSong(song)
            if (success) {
                _statusNotice.value = "Deleted ${song.title}"
                refreshAll()
            }
        }
    }

    // --- Audio Playback Controls ---

    fun playSong(song: Song, playlistContext: List<Song>? = null) {
        val list = playlistContext ?: _selectedPlaylistSongs.value.ifEmpty { _allSongs.value }
        val plName = _selectedPlaylist.value?.name ?: song.playlistName
        playerManager.playSong(song, list, plName)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        viewModelScope.launch {
            val songs = storageManager.loadSongsForPlaylist(playlist.name)
            if (songs.isNotEmpty()) {
                if (shuffle) {
                    if (!playerState.value.isShuffle) {
                        playerManager.toggleShuffle()
                    }
                }
                playerManager.playPlaylist(songs, 0, playlist.name)
            } else {
                _statusNotice.value = "Playlist \"${playlist.name}\" has no audio files yet. Tap + to upload songs."
            }
        }
    }

    fun togglePlayPause() = playerManager.togglePlayPause()

    fun skipNext() = playerManager.skipNext()

    fun skipPrevious() = playerManager.skipPrevious()

    fun stop() = playerManager.stop()

    fun seekTo(posMs: Long) = playerManager.seekTo(posMs)

    fun toggleRepeatMode() = playerManager.toggleRepeatMode()

    fun toggleShuffle() = playerManager.toggleShuffle()

    fun setSleepTimerMinutes(minutes: Int) {
        playerManager.setSleepTimerMinutes(minutes)
        _statusNotice.value = "Sleep timer set for $minutes minutes."
    }

    fun setSleepTimerEndOfSong() {
        playerManager.setSleepTimerEndOfSong()
        _statusNotice.value = "Sleep timer will stop playback after current song."
    }

    fun addSleepTimerMinutes(extraMinutes: Int) {
        playerManager.addSleepTimerMinutes(extraMinutes)
        _statusNotice.value = "Added $extraMinutes minutes to sleep timer."
    }

    fun cancelSleepTimer() {
        playerManager.cancelSleepTimer()
        _statusNotice.value = "Sleep timer cancelled."
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.releasePlayer()
    }
}
