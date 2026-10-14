package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Playlist
import com.example.ui.MusicViewModel
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.DeletePlaylistConfirmDialog
import com.example.ui.components.FolderInfoDialog
import com.example.ui.components.MiniPlayer
import com.example.ui.components.NowPlayingScreen
import com.example.ui.components.QueueSheet
import com.example.ui.components.RenamePlaylistDialog
import com.example.ui.components.SleepTimerSheet
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PlaylistsOverviewScreen
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.BrightCyan
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonPink
import com.example.ui.theme.Obsidian
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AuraPlayerApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraPlayerApp(viewModel: MusicViewModel) {
    val playerState by viewModel.playerState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val selectedPlaylistSongs by viewModel.filteredSelectedPlaylistSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val rootFolderPath by viewModel.rootFolderPath.collectAsState()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsState()
    val isSleepTimerSheetOpen by viewModel.isSleepTimerSheetOpen.collectAsState()
    val isFolderInfoDialogOpen by viewModel.isFolderInfoDialogOpen.collectAsState()
    val isQueueSheetOpen by viewModel.isQueueSheetOpen.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    // Audio file picker launcher
    var targetImportPlaylistName by remember { mutableStateOf<String?>(null) }
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(uris, targetImportPlaylistName)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(statusNotice) {
        statusNotice?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusNotice()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Obsidian,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                // Top App Bar (shown on overview)
                if (selectedPlaylist == null) {
                    TopAppBar(
                        onOpenFolderInfo = { viewModel.setFolderInfoDialogOpen(true) },
                        onUploadClick = {
                            targetImportPlaylistName = playlists.firstOrNull()?.name ?: "Default"
                            audioPickerLauncher.launch(arrayOf("audio/*", "application/ogg", "*/*"))
                        },
                        onOpenSleepTimer = { viewModel.setSleepTimerSheetOpen(true) },
                        isSleepTimerActive = playerState.isSleepTimerActive,
                        sleepTimerText = playerState.formattedSleepTimer
                    )
                }

                // Main Content Screen
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedPlaylist != null) {
                        PlaylistDetailScreen(
                            playlist = selectedPlaylist!!,
                            songs = selectedPlaylistSongs,
                            currentSong = playerState.currentSong,
                            isPlaying = playerState.isPlaying,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onBack = { viewModel.selectPlaylist(null) },
                            onSongClick = { song -> viewModel.playSong(song, selectedPlaylistSongs) },
                            onPlayAll = { viewModel.playPlaylist(selectedPlaylist!!) },
                            onShuffleAll = { viewModel.playPlaylist(selectedPlaylist!!, shuffle = true) },
                            onUploadAudio = {
                                targetImportPlaylistName = selectedPlaylist!!.name
                                audioPickerLauncher.launch(arrayOf("audio/*", "application/ogg", "*/*"))
                            },
                            onDeleteSong = { song -> viewModel.deleteSong(song) }
                        )
                    } else {
                        PlaylistsOverviewScreen(
                            playlists = playlists,
                            allSongs = allSongs,
                            rootFolderPath = rootFolderPath,
                            onPlaylistClick = { pl -> viewModel.selectPlaylist(pl) },
                            onPlayPlaylist = { pl -> viewModel.playPlaylist(pl) },
                            onShufflePlaylist = { pl -> viewModel.playPlaylist(pl, shuffle = true) },
                            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                            onUploadClick = {
                                targetImportPlaylistName = playlists.firstOrNull()?.name ?: "Favorites"
                                audioPickerLauncher.launch(arrayOf("audio/*", "application/ogg", "*/*"))
                            },
                            onRenamePlaylist = { pl -> playlistToRename = pl },
                            onDeletePlaylist = { pl -> playlistToDelete = pl },
                            onFolderInfoClick = { viewModel.setFolderInfoDialogOpen(true) },
                            onRefresh = { viewModel.refreshAll() }
                        )
                    }
                }

                // Docked MiniPlayer
                if (playerState.currentSong != null && !isNowPlayingExpanded) {
                    MiniPlayer(
                        playerState = playerState,
                        onExpand = { viewModel.setNowPlayingExpanded(true) },
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipNext() },
                        onOpenSleepTimer = { viewModel.setSleepTimerSheetOpen(true) },
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            }
        }

        // Full Screen Immersive Now Playing Player
        AnimatedVisibility(
            visible = isNowPlayingExpanded && playerState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            NowPlayingScreen(
                playerState = playerState,
                onCollapse = { viewModel.setNowPlayingExpanded(false) },
                onPlayPause = { viewModel.togglePlayPause() },
                onSkipNext = { viewModel.skipNext() },
                onSkipPrevious = { viewModel.skipPrevious() },
                onStop = { viewModel.stop() },
                onSeekTo = { posMs -> viewModel.seekTo(posMs) },
                onToggleRepeat = { viewModel.toggleRepeatMode() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onOpenSleepTimer = { viewModel.setSleepTimerSheetOpen(true) },
                onOpenQueue = { viewModel.setQueueSheetOpen(true) },
                onOpenFolderInfo = { viewModel.setFolderInfoDialogOpen(true) }
            )
        }

        // Sleep Timer Sheet
        if (isSleepTimerSheetOpen) {
            SleepTimerSheet(
                playerState = playerState,
                onDismiss = { viewModel.setSleepTimerSheetOpen(false) },
                onSetMinutes = { mins -> viewModel.setSleepTimerMinutes(mins) },
                onSetEndOfSong = { viewModel.setSleepTimerEndOfSong() },
                onAddMinutes = { extra -> viewModel.addSleepTimerMinutes(extra) },
                onCancelTimer = { viewModel.cancelSleepTimer() }
            )
        }

        // Queue Sheet
        if (isQueueSheetOpen) {
            QueueSheet(
                queue = viewModel.playerManager.getQueue(),
                currentSong = playerState.currentSong,
                isPlaying = playerState.isPlaying,
                onSongSelected = { song -> viewModel.playSong(song) },
                onDismiss = { viewModel.setQueueSheetOpen(false) }
            )
        }

        // Dialogs
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onConfirm = { name ->
                    showCreatePlaylistDialog = false
                    viewModel.createPlaylist(name)
                }
            )
        }

        playlistToRename?.let { pl ->
            RenamePlaylistDialog(
                playlist = pl,
                onDismiss = { playlistToRename = null },
                onConfirm = { newName ->
                    playlistToRename = null
                    viewModel.renamePlaylist(pl.name, newName)
                }
            )
        }

        playlistToDelete?.let { pl ->
            DeletePlaylistConfirmDialog(
                playlist = pl,
                onDismiss = { playlistToDelete = null },
                onConfirm = {
                    playlistToDelete = null
                    viewModel.deletePlaylist(pl)
                }
            )
        }

        if (isFolderInfoDialogOpen) {
            FolderInfoDialog(
                rootFolderPath = rootFolderPath,
                playlists = playlists,
                onDismiss = { viewModel.setFolderInfoDialogOpen(false) },
                onRefresh = {
                    viewModel.setFolderInfoDialogOpen(false)
                    viewModel.refreshAll()
                }
            )
        }
    }
}

@Composable
fun TopAppBar(
    onOpenFolderInfo: () -> Unit,
    onUploadClick: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App branding
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ElectricViolet, BrightCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Obsidian,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "AuraPlayer",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Smart Playlist Storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrightCyan
                )
            }
        }

        // Actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Sleep Timer indicator button
            IconButton(
                onClick = onOpenSleepTimer,
                modifier = Modifier.testTag("appbar_sleep_timer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Sleep timer",
                    tint = if (isSleepTimerActive) AmberGlow else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Upload audio button
            IconButton(
                onClick = onUploadClick,
                modifier = Modifier.testTag("appbar_upload_button")
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = "Upload audio",
                    tint = BrightCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Storage Folder Info button
            IconButton(
                onClick = onOpenFolderInfo,
                modifier = Modifier.testTag("appbar_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder storage",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
