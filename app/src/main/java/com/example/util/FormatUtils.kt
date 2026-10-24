package com.example.util

object FormatUtils {
    /**
     * Formats duration in milliseconds to HH:MM:SS or MM:SS.
     */
    fun formatDuration(durationMs: Long): String {
        val totalSec = (durationMs / 1000).coerceAtLeast(0L)
        val min = totalSec / 60
        val sec = totalSec % 60
        val hours = min / 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, min % 60, sec)
        } else {
            String.format("%02d:%02d", min, sec)
        }
    }

    /**
     * Formats transfer speed in bytes per second to human-readable MB/s, KB/s, or B/s.
     */
    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec.toDouble() / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec.toDouble() / 1024)
            else -> "$bytesPerSec B/s"
        }
    }

    /**
     * Formats byte sizes into GB, MB, KB, or B.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format("%.0f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }
}
