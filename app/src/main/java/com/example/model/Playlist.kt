package com.example.model

data class Playlist(
    val id: String,
    val name: String,
    val folderPath: String,
    val songCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val isSystemOrDemo: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (totalDurationMs <= 0) return "0 min"
            val totalSeconds = totalDurationMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            return if (hours > 0) {
                "${hours}h ${minutes % 60}m"
            } else {
                "${minutes}m"
            }
        }
}
