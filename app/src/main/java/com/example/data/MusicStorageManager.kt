package com.example.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.Playlist
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

class MusicStorageManager(private val context: Context) {

    private val defaultRootFolder: File by lazy {
        val base = context.getExternalFilesDir("Music") ?: context.filesDir
        File(base, "AuraPlaylists").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private var currentRootDirectory: File = defaultRootFolder

    suspend fun getRootFolderPath(): String = withContext(Dispatchers.IO) {
        currentRootDirectory.absolutePath
    }

    suspend fun setCustomRootDirectory(path: String) = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            currentRootDirectory = dir
        }
    }

    suspend fun resetToDefaultRootDirectory() = withContext(Dispatchers.IO) {
        currentRootDirectory = defaultRootFolder
    }

    /**
     * Initializes default demo playlists and pleasant generated audio tracks if directory is empty.
     */
    suspend fun ensureInitialData() = withContext(Dispatchers.IO) {
        try {
            if (!currentRootDirectory.exists()) {
                currentRootDirectory.mkdirs()
            }

            val existingPlaylists = currentRootDirectory.listFiles { file -> file.isDirectory }
            if (existingPlaylists.isNullOrEmpty()) {
                val chillDir = File(currentRootDirectory, "Ambient Chill").apply { mkdirs() }
                val nightDir = File(currentRootDirectory, "Night Drive").apply { mkdirs() }

                generateSyntheticTrack(
                    targetFile = File(chillDir, "Neon Dawn.wav"),
                    title = "Neon Dawn",
                    notes = listOf(261.63, 329.63, 392.00, 523.25), // C - E - G - C
                    durationSec = 22
                )
                generateSyntheticTrack(
                    targetFile = File(chillDir, "Velvet Echoes.wav"),
                    title = "Velvet Echoes",
                    notes = listOf(220.00, 261.63, 329.63, 440.00), // A minor
                    durationSec = 25
                )
                generateSyntheticTrack(
                    targetFile = File(nightDir, "Cyber Synthwave.wav"),
                    title = "Cyber Synthwave",
                    notes = listOf(293.66, 349.23, 440.00, 587.33), // D minor
                    durationSec = 24
                )
                generateSyntheticTrack(
                    targetFile = File(nightDir, "Cosmic Horizon.wav"),
                    title = "Cosmic Horizon",
                    notes = listOf(349.23, 440.00, 523.25, 659.25), // F major
                    durationSec = 26
                )
            }
        } catch (e: Exception) {
            Log.e("MusicStorage", "Error initializing initial tracks", e)
        }
    }

    /**
     * Scans the root directory for playlist subdirectories and returns the playlist list.
     */
    suspend fun loadPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<Playlist>()
        val dirs = currentRootDirectory.listFiles { file -> file.isDirectory } ?: emptyArray()

        // Sort alphabetically
        dirs.sortedBy { it.name.lowercase() }.forEach { dir ->
            val songs = scanAudioFilesInDir(dir, dir.name)
            val totalDuration = songs.sumOf { it.durationMs }
            playlists.add(
                Playlist(
                    id = dir.name,
                    name = dir.name,
                    folderPath = dir.absolutePath,
                    songCount = songs.size,
                    totalDurationMs = totalDuration,
                    isSystemOrDemo = false
                )
            )
        }
        playlists
    }

    /**
     * Loads all songs from a specific playlist folder.
     */
    suspend fun loadSongsForPlaylist(playlistName: String): List<Song> = withContext(Dispatchers.IO) {
        val playlistDir = File(currentRootDirectory, playlistName)
        if (!playlistDir.exists() || !playlistDir.isDirectory) return@withContext emptyList()
        scanAudioFilesInDir(playlistDir, playlistName)
    }

    /**
     * Loads all songs across all playlists/subfolders.
     */
    suspend fun loadAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val allSongs = mutableListOf<Song>()
        val dirs = currentRootDirectory.listFiles { file -> file.isDirectory } ?: emptyArray()
        dirs.forEach { dir ->
            allSongs.addAll(scanAudioFilesInDir(dir, dir.name))
        }
        // Also check if there are audio files directly in the root directory
        val rootSongs = scanAudioFilesInDir(currentRootDirectory, "Uncategorized")
        allSongs.addAll(rootSongs)
        allSongs
    }

    /**
     * Creates a new playlist subdirectory.
     */
    suspend fun createPlaylist(name: String): Boolean = withContext(Dispatchers.IO) {
        val safeName = sanitizeFolderName(name)
        if (safeName.isBlank()) return@withContext false
        val newDir = File(currentRootDirectory, safeName)
        if (!newDir.exists()) {
            newDir.mkdirs()
        } else false
    }

    /**
     * Renames a playlist subdirectory.
     */
    suspend fun renamePlaylist(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val oldDir = File(currentRootDirectory, oldName)
        val safeNewName = sanitizeFolderName(newName)
        if (!oldDir.exists() || safeNewName.isBlank()) return@withContext false
        val newDir = File(currentRootDirectory, safeNewName)
        if (newDir.exists()) return@withContext false
        oldDir.renameTo(newDir)
    }

    /**
     * Deletes a playlist directory and its files.
     */
    suspend fun deletePlaylist(name: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(currentRootDirectory, name)
        if (dir.exists()) {
            dir.deleteRecursively()
        } else false
    }

    /**
     * Deletes an audio song file.
     */
    suspend fun deleteSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(song.filePath)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Imports audio files from URIs into a target playlist folder.
     */
    suspend fun importAudioFiles(playlistName: String, uris: List<Uri>): List<Song> = withContext(Dispatchers.IO) {
        val playlistDir = File(currentRootDirectory, playlistName).apply {
            if (!exists()) mkdirs()
        }
        val importedSongs = mutableListOf<Song>()

        for (uri in uris) {
            try {
                val originalName = queryFileName(uri) ?: "Audio_${System.currentTimeMillis()}.mp3"
                var targetFile = File(playlistDir, originalName)
                if (targetFile.exists()) {
                    val nameWithoutExt = targetFile.nameWithoutExtension
                    val ext = targetFile.extension
                    targetFile = File(playlistDir, "${nameWithoutExt}_${System.currentTimeMillis()}.$ext")
                }

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val song = extractSongMetadata(targetFile, playlistName)
                importedSongs.add(song)
            } catch (e: Exception) {
                Log.e("MusicStorage", "Failed to import uri: $uri", e)
            }
        }
        importedSongs
    }

    private fun scanAudioFilesInDir(dir: File, playlistName: String): List<Song> {
        val audioExtensions = setOf("mp3", "wav", "m4a", "aac", "flac", "ogg")
        val files = dir.listFiles { file ->
            file.isFile && file.extension.lowercase() in audioExtensions
        } ?: emptyArray()

        return files.sortedBy { it.name.lowercase() }.map { file ->
            extractSongMetadata(file, playlistName)
        }
    }

    private fun extractSongMetadata(file: File, playlistName: String): Song {
        var title = file.nameWithoutExtension
        var artist = "Unknown Artist"
        var album = playlistName
        var durationMs = 0L

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val extractedAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val extractedDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!extractedTitle.isNullOrBlank()) title = extractedTitle.trim()
            if (!extractedArtist.isNullOrBlank()) artist = extractedArtist.trim()
            if (!extractedAlbum.isNullOrBlank()) album = extractedAlbum.trim()
            if (!extractedDuration.isNullOrBlank()) {
                durationMs = extractedDuration.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            // Fallback for simple WAV or unparsed metadata
            if (durationMs <= 0 && file.extension.lowercase() == "wav") {
                val sampleRate = 44100L
                val bytesPerSec = sampleRate * 2 * 2 // 16-bit stereo
                val audioBytes = (file.length() - 44).coerceAtLeast(0)
                durationMs = (audioBytes * 1000) / bytesPerSec
            }
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        return Song(
            id = file.absolutePath.hashCode().toString(),
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            filePath = file.absolutePath,
            fileUriString = Uri.fromFile(file).toString(),
            playlistName = playlistName,
            dateAdded = file.lastModified()
        )
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }

    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    /**
     * Synthesizes a melodic 44.1kHz 16-bit stereo PCM WAV file with warm harmonics and smooth decay.
     */
    private fun generateSyntheticTrack(
        targetFile: File,
        title: String,
        notes: List<Double>,
        durationSec: Int
    ) {
        val sampleRate = 44100
        val totalSamples = sampleRate * durationSec
        val numChannels = 2
        val bytesPerSample = 2
        val dataSize = totalSamples * numChannels * bytesPerSample

        RandomAccessFile(targetFile, "rw").use { raf ->
            raf.setLength(0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            // RIFF header
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            // fmt subchunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1) // AudioFormat 1 = PCM
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * numChannels * bytesPerSample) // ByteRate
            header.putShort((numChannels * bytesPerSample).toShort()) // BlockAlign
            header.putShort((bytesPerSample * 8).toShort()) // BitsPerSample
            // data subchunk
            header.put("data".toByteArray())
            header.putInt(dataSize)

            raf.write(header.array())

            val bufferSize = 8192
            val pcmBuffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)

            val noteDuration = sampleRate * 1.5 // 1.5 sec per arpeggio note
            for (i in 0 until totalSamples) {
                val currentNoteIndex = ((i / noteDuration).toInt()) % notes.size
                val baseFreq = notes[currentNoteIndex]
                val subFreq = baseFreq * 0.5
                val harmonicFreq = baseFreq * 2.0

                val noteTimeSec = (i % noteDuration) / sampleRate.toDouble()
                // Envelope: quick attack, smooth exponential decay
                val envelope = (1.0 - Math.exp(-noteTimeSec * 4.0)) * Math.exp(-noteTimeSec * 1.2)

                val t = i.toDouble() / sampleRate
                val mainWave = sin(2.0 * PI * baseFreq * t)
                val subWave = 0.4 * sin(2.0 * PI * subFreq * t)
                val harmWave = 0.25 * sin(2.0 * PI * harmonicFreq * t)

                val sample = ((mainWave + subWave + harmWave) * envelope * 0.45 * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                // Left & Right stereo slight phase chorus
                val rightSample = ((mainWave + subWave + 0.3 * sin(2.0 * PI * (harmonicFreq + 1.5) * t)) * envelope * 0.45 * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                pcmBuffer.putShort(sample)
                pcmBuffer.putShort(rightSample)

                if (!pcmBuffer.hasRemaining()) {
                    raf.write(pcmBuffer.array())
                    pcmBuffer.clear()
                }
            }

            if (pcmBuffer.position() > 0) {
                raf.write(pcmBuffer.array(), 0, pcmBuffer.position())
            }
        }
    }
}
