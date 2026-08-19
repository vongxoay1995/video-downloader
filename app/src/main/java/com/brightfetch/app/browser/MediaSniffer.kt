package com.brightfetch.app.browser

import android.net.Uri
import com.brightfetch.app.model.MediaCandidate

object MediaSniffer {
    fun fromRequest(
        url: String,
        pageTitle: String,
        pageUrl: String?,
        userAgent: String?,
        cookie: String?,
    ): MediaCandidate? {
        if (!MediaUrlClassifier.isLikelyNetworkMedia(url)) return null
        return MediaCandidate(url, pageTitle.ifBlank { "Detected video" }, pageUrl, userAgent = userAgent, cookie = cookie)
    }

    fun fromVideoElement(
        url: String,
        pageTitle: String,
        pageUrl: String?,
        userAgent: String?,
        cookie: String?,
    ): MediaCandidate? {
        if (!MediaUrlClassifier.isUsableVideoElementUrl(url)) return null
        return MediaCandidate(
            url = url,
            title = pageTitle.ifBlank { "Detected video" },
            pageUrl = pageUrl,
            mimeType = if (MediaUrlClassifier.isHls(url)) "application/vnd.apple.mpegurl" else "video/mp4",
            userAgent = userAgent,
            cookie = cookie,
        )
    }

    fun fromDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        pageTitle: String,
        pageUrl: String?,
        userAgent: String?,
        cookie: String?,
    ): MediaCandidate? {
        if (!MediaUrlClassifier.isHttpUrl(url)) return null
        val looksLikeVideo = mimeType?.startsWith("video/", ignoreCase = true) == true ||
            mimeType?.contains("mpegurl", ignoreCase = true) == true ||
            fromRequest(url, pageTitle, pageUrl, userAgent, cookie) != null
        if (!looksLikeVideo) return null
        val dispositionName = Regex("filename\\*?=(?:UTF-8''|\")?([^\";]+)", RegexOption.IGNORE_CASE)
            .find(contentDisposition.orEmpty())?.groupValues?.getOrNull(1)
        return MediaCandidate(
            url = url,
            title = dispositionName?.let(Uri::decode)?.ifBlank { pageTitle } ?: pageTitle,
            pageUrl = pageUrl,
            mimeType = mimeType,
            userAgent = userAgent,
            cookie = cookie,
        )
    }
}
