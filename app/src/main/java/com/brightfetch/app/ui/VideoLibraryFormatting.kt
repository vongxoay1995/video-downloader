package com.brightfetch.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object VideoLibraryFormatting {
    private val downloadedDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun bytes(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        if (safeBytes < 1_024L) return "$safeBytes B"

        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = safeBytes.toDouble()
        var unitIndex = -1
        while (value >= 1_024.0 && unitIndex < units.lastIndex) {
            value /= 1_024.0
            unitIndex++
        }
        val formatted = String.format(Locale.US, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')
        return "$formatted ${units[unitIndex]}"
    }

    fun extension(name: String, mimeType: String): String {
        val nameExtension = name.substringAfterLast('.', "")
            .trim()
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
        val extension = nameExtension ?: when (mimeType.substringBefore(';').lowercase(Locale.ROOT)) {
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/x-matroska", "video/matroska" -> "mkv"
            "video/x-msvideo", "video/avi" -> "avi"
            "video/x-flv" -> "flv"
            "video/3gpp", "video/3gp" -> "3gp"
            "video/x-m4v" -> "m4v"
            "video/mpeg" -> "mpeg"
            "video/mp2t" -> "ts"
            else -> "mp4"
        }
        return extension.uppercase(Locale.ROOT)
    }

    fun quality(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "Unknown quality"
        return "${minOf(width, height)}p"
    }

    fun resolution(width: Int, height: Int): String =
        if (width > 0 && height > 0) "${width}×$height" else "Unknown resolution"

    fun duration(durationMillis: Long): String? {
        if (durationMillis <= 0L) return null
        val totalSeconds = durationMillis / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun downloadedDate(
        downloadedAtMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (downloadedAtMillis <= 0L) return "--/--/----"
        return downloadedDateFormatter.format(Instant.ofEpochMilli(downloadedAtMillis).atZone(zoneId))
    }
}
