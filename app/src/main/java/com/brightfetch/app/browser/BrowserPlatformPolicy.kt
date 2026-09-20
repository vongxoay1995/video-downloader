package com.brightfetch.app.browser

import java.net.URI
import java.util.Locale

/** Apps that the embedded browser may open from an explicit user-initiated app link. */
enum class ExternalBrowserApp(
    val id: String,
    val displayName: String,
    val packageNames: List<String>,
    val deepLinkSchemes: Set<String>,
) {
    TIKTOK(
        id = "tiktok",
        displayName = "TikTok",
        packageNames = listOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go",
        ),
        deepLinkSchemes = setOf(
            "tiktok",
            "musically",
            "snssdk1233",
            "snssdk1180",
        ),
    ),
    FACEBOOK(
        id = "facebook",
        displayName = "Facebook",
        packageNames = listOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.facebook.orca",
        ),
        deepLinkSchemes = setOf(
            "fb",
            "facebook",
            "fbapi",
            "fbauth2",
            "fb-messenger",
        ),
    ),
}

data class UnsupportedDownloadPlatform(
    val displayName: String,
    val message: String,
)

/** Pure URL policy shared by WebView navigation and the download-button state. */
object BrowserPlatformPolicy {
    private val youtubeUnsupported = UnsupportedDownloadPlatform(
        displayName = "YouTube",
        message = "YouTube isn't supported for video downloads. Copyright-protected or encrypted videos can't be downloaded.",
    )

    fun externalAppForScheme(scheme: String?): ExternalBrowserApp? {
        val normalized = scheme?.lowercase(Locale.ROOT) ?: return null
        return ExternalBrowserApp.entries.firstOrNull { normalized in it.deepLinkSchemes }
    }

    fun externalAppForPackage(packageName: String?): ExternalBrowserApp? {
        val normalized = packageName?.lowercase(Locale.ROOT) ?: return null
        return ExternalBrowserApp.entries.firstOrNull { app ->
            app.packageNames.any { it.equals(normalized, ignoreCase = true) }
        }
    }

    fun externalAppForWebUrl(url: String?): ExternalBrowserApp? {
        val host = httpHost(url) ?: return null
        return when {
            host.matchesDomain("tiktok.com") -> ExternalBrowserApp.TIKTOK
            host.matchesDomain("facebook.com") ||
                host.matchesDomain("fb.com") ||
                host.matchesDomain("fb.watch") ||
                host.matchesDomain("messenger.com") ||
                host.matchesDomain("m.me") -> ExternalBrowserApp.FACEBOOK
            else -> null
        }
    }

    /**
     * Ordinary TikTok/Facebook links stay inside WebView. Only well-known short/app-link
     * endpoints are handed to an installed app after a real user gesture.
     */
    fun explicitWebAppLink(url: String?): ExternalBrowserApp? {
        val uri = parseHttpUri(url) ?: return null
        val host = uri.host?.normalizeHost() ?: return null
        val path = uri.rawPath.orEmpty().lowercase(Locale.ROOT)
        val query = uri.rawQuery.orEmpty().lowercase(Locale.ROOT)
        return when {
            host in setOf("vm.tiktok.com", "vt.tiktok.com") ||
                (host.matchesDomain("tiktok.com") && path.startsWith("/t/")) ||
                (host.matchesDomain("tiktok.com") && query.hasOpenAppMarker()) -> {
                ExternalBrowserApp.TIKTOK
            }
            host == "fb.watch" || host == "m.me" ||
                (host.matchesDomain("facebook.com") && query.hasOpenAppMarker()) -> {
                ExternalBrowserApp.FACEBOOK
            }
            else -> null
        }
    }

    fun unsupportedDownloadFor(url: String?): UnsupportedDownloadPlatform? {
        val host = httpHost(url) ?: return null
        return if (
            host.matchesDomain("youtube.com") ||
            host.matchesDomain("youtube-nocookie.com") ||
            host.matchesDomain("youtu.be")
        ) {
            youtubeUnsupported
        } else {
            null
        }
    }

    fun safeHttpUrl(rawUrl: String?): String? {
        val uri = parseHttpUri(rawUrl) ?: return null
        return uri.toASCIIString()
    }

    fun isSafeTargetFor(app: ExternalBrowserApp, rawUrl: String?): Boolean {
        val uri = runCatching { URI(rawUrl?.trim().orEmpty()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        return when (scheme) {
            "http", "https" -> externalAppForWebUrl(rawUrl) == app
            else -> externalAppForScheme(scheme) == app
        }
    }

    private fun httpHost(url: String?): String? = parseHttpUri(url)?.host?.normalizeHost()

    private fun parseHttpUri(rawUrl: String?): URI? {
        val uri = runCatching { URI(rawUrl?.trim().orEmpty()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return uri
    }

    private fun String.normalizeHost(): String = lowercase(Locale.ROOT).trimEnd('.')

    private fun String.matchesDomain(domain: String): Boolean =
        this == domain || endsWith(".$domain")

    private fun String.hasOpenAppMarker(): Boolean = split('&').any { parameter ->
        val key = parameter.substringBefore('=')
        val value = parameter.substringAfter('=', missingDelimiterValue = "")
        when (key) {
            "open_in_app", "openapp", "open_app" -> value == "1"
            "deeplink" -> value.isNotBlank()
            else -> false
        }
    }
}
