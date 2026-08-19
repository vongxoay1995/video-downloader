package com.brightfetch.app.model

import java.util.Locale

object VideoFileNames {
    val supportedExtensions = setOf(
        "mp4", "webm", "mov", "mkv", "avi", "flv", "3gp", "m4v",
        "mpg", "mpeg", "mp2", "ts", "m2ts",
    )

    fun normalize(rawValue: String, mimeType: String? = null, isHls: Boolean = false): String {
        val sanitized = rawValue
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.')
            .let { truncateUtf8(it, MAX_BASE_BYTES) }
            .ifBlank { "video" }
        val currentExtension = sanitized.substringAfterLast('.', "").lowercase(Locale.US)
        val extension = if (isHls) "mp4" else currentExtension.takeIf {
            it in supportedExtensions
        } ?: extensionForMime(mimeType) ?: "mp4"
        if (!isHls && currentExtension == extension && currentExtension in supportedExtensions) {
            return sanitized
        }
        val base = if (currentExtension.isNotBlank() && currentExtension.length <= 5) {
            sanitized.substringBeforeLast('.')
        } else {
            sanitized
        }.trimEnd(' ', '.').ifBlank { "video" }
        return "$base.$extension"
    }

    fun hasSupportedExtension(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase(Locale.US) in supportedExtensions

    fun mimeTypeFor(fileName: String, fallback: String? = null): String {
        val normalizedFallback = fallback?.substringBefore(';')?.trim()?.lowercase(Locale.US)
        if (normalizedFallback?.startsWith("video/") == true) return normalizedFallback
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            "3gp" -> "video/3gpp"
            "m4v" -> "video/x-m4v"
            "mpg", "mpeg" -> "video/mpeg"
            "mp2", "ts", "m2ts" -> "video/mp2t"
            else -> "video/mp4"
        }
    }

    private fun extensionForMime(mimeType: String?): String? = when (
        mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
    ) {
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/x-matroska", "video/matroska" -> "mkv"
        "video/x-msvideo", "video/avi" -> "avi"
        "video/x-flv" -> "flv"
        "video/3gpp", "video/3gp" -> "3gp"
        "video/x-m4v" -> "m4v"
        "video/mpeg" -> "mpeg"
        "video/mp2t" -> "ts"
        "video/mp4" -> "mp4"
        else -> null
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        val result = StringBuilder()
        var byteCount = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val text = String(Character.toChars(codePoint))
            val bytes = text.toByteArray(Charsets.UTF_8).size
            if (byteCount + bytes > maxBytes) break
            result.append(text)
            byteCount += bytes
            index += Character.charCount(codePoint)
        }
        return result.toString().trimEnd(' ', '.')
    }

    private const val MAX_BASE_BYTES = 220
}
