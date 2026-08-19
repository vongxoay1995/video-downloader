package com.brightfetch.app.browser

import java.net.URI
import java.util.Locale

/** Stable page identity across mobile, www, and apex aliases used by supported news sites. */
object MediaPageIdentity {
    fun key(value: String?): String? {
        val uri = value?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        val canonicalHost = when {
            host == "kenh14.vn" || host.endsWith(".kenh14.vn") -> "kenh14.vn"
            host == "24h.com.vn" || host.endsWith(".24h.com.vn") -> "24h.com.vn"
            else -> host
        }
        return "$canonicalHost${uri.path.orEmpty().trimEnd('/')}"
    }
}
