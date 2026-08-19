package com.brightfetch.app.model

import android.net.Uri
import java.util.Locale

data class MediaCandidate(
    val url: String,
    val title: String,
    val pageUrl: String? = null,
    val mimeType: String? = null,
    val userAgent: String? = null,
    val cookie: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Long? = null,
    val contentLengthBytes: Long? = null,
    val preferredFileName: String? = null,
) {
    val id: String = url.hashCode().toUInt().toString(16)
    val isHls: Boolean = url.lowercase(Locale.US).substringBefore('?').endsWith(".m3u8") ||
        mimeType?.contains("mpegurl", ignoreCase = true) == true

    val suggestedFileName: String
        get() {
            val pathName = Uri.parse(url).lastPathSegment
                ?.substringBefore('?')
                ?.takeIf { it.contains('.') }
            val readableTitle = title.takeIf {
                it.isNotBlank() &&
                    !it.equals("Detected video", ignoreCase = true) &&
                    !it.equals("BrightFetch", ignoreCase = true)
            }
            val raw = preferredFileName?.takeIf(String::isNotBlank)
                ?: (if (isHls) readableTitle ?: pathName else pathName)
                ?: title.ifBlank { "video_$id" }
            return VideoFileNames.normalize(raw, mimeType = mimeType, isHls = isHls)
        }
}
