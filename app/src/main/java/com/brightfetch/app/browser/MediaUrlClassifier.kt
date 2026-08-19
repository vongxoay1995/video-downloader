package com.brightfetch.app.browser

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Pure URL classification so it can be unit-tested without a WebView or Android runtime. */
object MediaUrlClassifier {
    private val supportedExtensions = setOf(
        "mp4", "m3u8", "webm", "mov", "mkv", "avi", "flv", "3gp", "m4v",
    )
    private val ignoredSegmentExtensions = setOf("ts", "m4s", "cmfv", "cmfa", "aac")
    private val audioExtensions = setOf("aac", "flac", "m4a", "mp3", "oga", "ogg", "opus", "wav")
    private val mediaQueryHints = listOf(
        "mime_type=video",
        "mime=video",
        "video_mp4",
        "content-type=video",
        "content_type=video",
    )
    private val mediaPathHints = listOf(
        "/video/tos/",
        "/aweme/v1/play/",
        "/aweme/v1/playwm/",
        "/video/play/",
    )
    private val mediaCdnHostHints = listOf(
        "tiktokcdn",
        "tiktokv",
        "byteoversea",
        "ibytedtos",
        "muscdn",
        "musical.ly",
    )

    fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase(Locale.US) in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    fun isHls(value: String): Boolean = extensionOf(value) == "m3u8"

    fun isLikelyNetworkMedia(value: String): Boolean {
        if (!isHttpUrl(value)) return false
        if (isLikelyAudio(value)) return false
        val extension = extensionOf(value)
        if (extension in ignoredSegmentExtensions) return false
        if (extension in supportedExtensions) return true

        val normalized = runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value).lowercase(Locale.US)
        if (mediaQueryHints.any(normalized::contains)) return true

        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        if (mediaPathHints.any(path::contains)) return true
        return mediaCdnHostHints.any(host::contains) &&
            (path.contains("/video/") || path.contains("/play/") || path.contains("playwm"))
    }

    fun isUsableVideoElementUrl(value: String): Boolean {
        if (!isHttpUrl(value)) return false
        if (isLikelyAudio(value)) return false
        return extensionOf(value) !in ignoredSegmentExtensions
    }

    fun isLikelyAudio(value: String): Boolean {
        if (!isHttpUrl(value)) return false
        if (extensionOf(value) in audioExtensions) return true
        val normalized = runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value).lowercase(Locale.US)
        val host = runCatching { URI(value).host }.getOrNull().orEmpty().lowercase(Locale.US)
        return "mime_type=audio" in normalized ||
            "mime=audio" in normalized ||
            "audio_mp4" in normalized ||
            "content-type=audio" in normalized ||
            "content_type=audio" in normalized ||
            "-music-" in host ||
            host.startsWith("music.") ||
            ".music." in host
    }

    private fun extensionOf(value: String): String {
        val path = runCatching { URI(value).path }.getOrNull().orEmpty()
        return path.substringAfterLast('.', "").lowercase(Locale.US)
    }
}
