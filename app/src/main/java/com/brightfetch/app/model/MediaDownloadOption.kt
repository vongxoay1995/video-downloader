package com.brightfetch.app.model

import kotlin.math.roundToLong

enum class MediaDownloadSource {
    DIRECT,
    HLS,
}

/** A concrete file or HLS media playlist the user can choose to download. */
data class MediaDownloadOption(
    val downloadUrl: String,
    val source: MediaDownloadSource,
    val label: String,
    val mimeType: String,
    val outputExtension: String,
    val width: Int? = null,
    val height: Int? = null,
    val bandwidthBitsPerSecond: Long? = null,
    val averageBandwidthBitsPerSecond: Long? = null,
    val codecs: String? = null,
    val durationSeconds: Double? = null,
    val segmentCount: Int? = null,
    val estimatedSizeBytes: Long? = null,
    val sizeIsExact: Boolean = false,
    val unavailableReason: String? = null,
) {
    val id: String = listOf(downloadUrl, width, height, bandwidthBitsPerSecond, codecs)
        .joinToString("|")
        .hashCode()
        .toUInt()
        .toString(16)

    val isAvailable: Boolean get() = unavailableReason == null

    /**
     * Uses the selected leaf URL. In particular, an HLS option points at a media playlist rather
     * than its master playlist, so the download worker cannot silently choose another quality.
     */
    fun toCandidate(original: MediaCandidate): MediaCandidate = original.copy(
        url = downloadUrl,
        mimeType = mimeType,
        width = width ?: original.width,
        height = height ?: original.height,
        durationSeconds = durationSeconds
            ?.takeIf { it.isFinite() && it >= 0.0 && it <= Long.MAX_VALUE.toDouble() }
            ?.roundToLong()
            ?: original.durationSeconds,
        contentLengthBytes = estimatedSizeBytes ?: original.contentLengthBytes,
    )
}
