package com.brightfetch.app.browser

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class BrowserHistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long,
)

data class BrowserBookmark(
    val url: String,
    val title: String,
    val createdAt: Long,
)

data class BrowserSettings(
    val searchEngine: BrowserSearchEngine = BrowserSearchEngine.GOOGLE,
    val javaScriptEnabled: Boolean = true,
    val cookiesEnabled: Boolean = true,
    val desktopModeEnabled: Boolean = false,
)

enum class BrowserSearchEngine(
    val displayName: String,
    val searchUrlPrefix: String,
) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    YAHOO("Yahoo", "https://search.yahoo.com/search?p="),
    ;

    fun buildSearchUrl(query: String): String =
        searchUrlPrefix + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
}

/** Converts address-bar input into a safe http(s) URL or a search-engine query. */
object BrowserNavigation {
    private val sharedUrlPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun resolve(raw: String, searchEngine: BrowserSearchEngine): String? {
        val value = raw.trim()
        if (value.isBlank()) return null

        val sharedUrl = sharedUrlPattern.find(value)
            ?.value
            ?.trimEnd('.', ',', ';', ')', ']', '}')
        return when {
            sharedUrl != null -> sharedUrl
            looksLikeHost(value) -> "https://$value"
            else -> searchEngine.buildSearchUrl(value)
        }
    }

    fun isHttpUrl(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("https://") || normalized.startsWith("http://")
    }

    private fun looksLikeHost(value: String): Boolean =
        value.contains('.') && value.none(Char::isWhitespace) &&
            !value.contains("://")
}

internal object BrowserHistoryRules {
    const val MAX_ENTRIES = 200

    fun upsert(
        current: List<BrowserHistoryEntry>,
        entry: BrowserHistoryEntry,
    ): List<BrowserHistoryEntry> = buildList {
        add(entry)
        current.asSequence()
            .filterNot { it.url == entry.url }
            .take(MAX_ENTRIES - 1)
            .forEach(::add)
    }
}
