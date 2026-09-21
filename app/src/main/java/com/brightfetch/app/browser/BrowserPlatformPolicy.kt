package com.brightfetch.app.browser

import java.net.URI
import java.net.URLDecoder
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
            "com.ss.android.ugc.tiktok.lite",
            "com.tiktok.lite.go",
        ),
        deepLinkSchemes = setOf(
            "tiktok",
            "musically",
            "snssdk1233",
            "snssdk1180",
            "snssdkonly1180",
            "snssdkotl1233",
            "snssdkm21233",
            "snssdklitem21233",
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

data class ExternalAppLinkRequest(
    val app: ExternalBrowserApp,
    val targetUrl: String,
)

data class StoreInstallRequest(
    val app: ExternalBrowserApp,
    val requestedPackage: String,
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
        return externalAppLinkRequest(url)?.app
    }

    /**
     * Resolves user-facing web app links to the URL that the native app should receive.
     * TikTok's OneLink page exposes the real deep link through its signed campaign URL's
     * `af_dp` parameter; dispatching that value avoids the redirect to Google Play.
     */
    fun externalAppLinkRequest(url: String?): ExternalAppLinkRequest? {
        val uri = parseHttpUri(url) ?: return null
        val host = uri.host?.normalizeHost() ?: return null
        val path = uri.rawPath.orEmpty().lowercase(Locale.ROOT)
        val query = uri.rawQuery.orEmpty().lowercase(Locale.ROOT)
        val directApp = when {
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
        if (directApp != null) {
            return ExternalAppLinkRequest(directApp, uri.toASCIIString())
        }

        if (
            uri.scheme.equals("https", ignoreCase = true) &&
            uri.hasStandardHttpsAuthority() &&
            host in TIKTOK_ONE_LINK_HOSTS &&
            path.isNotBlank() &&
            path != "/"
        ) {
            val deepLink = uri.uniqueDecodedQueryValue("af_dp") ?: return null
            if (isSafeTargetFor(ExternalBrowserApp.TIKTOK, deepLink)) {
                return ExternalAppLinkRequest(ExternalBrowserApp.TIKTOK, deepLink)
            }
        }
        return null
    }

    /** Exact allowlisted Google Play/market install URL, never an arbitrary package. */
    fun storeInstallRequest(url: String?): StoreInstallRequest? {
        val uri = runCatching { URI(url?.trim().orEmpty()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.normalizeHost() ?: return null
        val path = uri.rawPath.orEmpty()
        val isSupportedStoreUrl = when (scheme) {
            "https" -> uri.hasStandardHttpsAuthority() &&
                host == "play.google.com" &&
                path == "/store/apps/details"
            "market" -> uri.rawUserInfo == null &&
                uri.port == -1 &&
                host == "details" &&
                (path.isBlank() || path == "/")
            else -> false
        }
        if (!isSupportedStoreUrl) return null
        val requestedPackage = uri.uniqueDecodedQueryValue("id") ?: return null
        val app = externalAppForPackage(requestedPackage) ?: return null
        return StoreInstallRequest(app, requestedPackage)
    }

    fun externalAppForStoreUrl(url: String?): ExternalBrowserApp? {
        return storeInstallRequest(url)?.app
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

    private fun URI.hasStandardHttpsAuthority(): Boolean =
        rawUserInfo == null && (port == -1 || port == 443)

    private fun URI.uniqueDecodedQueryValue(expectedKey: String): String? {
        val matches = rawQuery.orEmpty().split('&').filter { parameter ->
            val rawKey = parameter.substringBefore('=')
            val key = rawKey.decodeQueryComponent() ?: return@filter false
            key == expectedKey
        }
        val match = matches.singleOrNull() ?: return null
        return match.substringAfter('=', missingDelimiterValue = "")
            .decodeQueryComponent()
            ?.takeIf(String::isNotBlank)
    }

    private fun String.decodeQueryComponent(): String? = runCatching {
        URLDecoder.decode(this, Charsets.UTF_8.name())
    }.getOrNull()

    private fun String.hasOpenAppMarker(): Boolean = split('&').any { parameter ->
        val key = parameter.substringBefore('=')
        val value = parameter.substringAfter('=', missingDelimiterValue = "")
        when (key) {
            "open_in_app", "openapp", "open_app" -> value == "1"
            "deeplink" -> value.isNotBlank()
            else -> false
        }
    }

    private val TIKTOK_ONE_LINK_HOSTS = setOf(
        "snssdk1180.onelink.me",
        "snssdk1233.onelink.me",
    )
}
