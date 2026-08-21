package com.brightfetch.app.browser

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal class BrowserRepository(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val _history = MutableStateFlow(
        BrowserJsonCodec.decodeHistory(preferences.getString(KEY_HISTORY, null)),
    )
    val history: StateFlow<List<BrowserHistoryEntry>> = _history.asStateFlow()

    private val _bookmarks = MutableStateFlow(
        BrowserJsonCodec.decodeBookmarks(preferences.getString(KEY_BOOKMARKS, null)),
    )
    val bookmarks: StateFlow<List<BrowserBookmark>> = _bookmarks.asStateFlow()

    private val _settings = MutableStateFlow(
        BrowserJsonCodec.decodeSettings(preferences.getString(KEY_SETTINGS, null)),
    )
    val settings: StateFlow<BrowserSettings> = _settings.asStateFlow()

    @Synchronized
    fun recordVisit(url: String, title: String, visitedAt: Long = System.currentTimeMillis()) {
        val safeUrl = url.trim().takeIf(BrowserNavigation::isHttpUrl)?.take(MAX_URL_LENGTH) ?: return
        val entry = BrowserHistoryEntry(
            url = safeUrl,
            title = safeTitle(title, safeUrl),
            visitedAt = visitedAt.coerceAtLeast(0L),
        )
        val updated = BrowserHistoryRules.upsert(_history.value, entry)
        _history.value = updated
        writeJson(KEY_HISTORY, BrowserJsonCodec.encodeHistory(updated))
    }

    @Synchronized
    fun clearHistory() {
        _history.value = emptyList()
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    @Synchronized
    fun toggleBookmark(url: String, title: String, createdAt: Long = System.currentTimeMillis()): Boolean {
        val safeUrl = url.trim().takeIf(BrowserNavigation::isHttpUrl)?.take(MAX_URL_LENGTH) ?: return false
        val current = _bookmarks.value
        val existing = current.firstOrNull { it.url == safeUrl }
        val updated: List<BrowserBookmark>
        val nowBookmarked: Boolean
        if (existing != null) {
            updated = current.filterNot { it.url == safeUrl }
            nowBookmarked = false
        } else {
            updated = listOf(
                BrowserBookmark(
                    url = safeUrl,
                    title = safeTitle(title, safeUrl),
                    createdAt = createdAt.coerceAtLeast(0L),
                ),
            ) + current
            nowBookmarked = true
        }
        _bookmarks.value = updated
        writeJson(KEY_BOOKMARKS, BrowserJsonCodec.encodeBookmarks(updated))
        return nowBookmarked
    }

    @Synchronized
    fun removeBookmark(url: String) {
        val normalizedUrl = url.trim().take(MAX_URL_LENGTH)
        val updated = _bookmarks.value.filterNot { it.url == normalizedUrl }
        if (updated.size == _bookmarks.value.size) return
        _bookmarks.value = updated
        writeJson(KEY_BOOKMARKS, BrowserJsonCodec.encodeBookmarks(updated))
    }

    fun isBookmarked(url: String): Boolean =
        _bookmarks.value.any { it.url == url.trim().take(MAX_URL_LENGTH) }

    @Synchronized
    fun clearBookmarks() {
        _bookmarks.value = emptyList()
        preferences.edit().remove(KEY_BOOKMARKS).apply()
    }

    @Synchronized
    fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        writeJson(KEY_SETTINGS, BrowserJsonCodec.encodeSettings(updated))
    }

    @Synchronized
    fun resetSettings() {
        _settings.value = BrowserSettings()
        preferences.edit().remove(KEY_SETTINGS).apply()
    }

    private fun writeJson(key: String, json: String) {
        runCatching { preferences.edit().putString(key, json).apply() }
    }

    private fun safeTitle(rawTitle: String, url: String): String {
        val value = rawTitle.trim().take(MAX_TITLE_LENGTH)
        if (value.isNotBlank()) return value
        return runCatching { Uri.parse(url).host.orEmpty() }
            .getOrDefault("")
            .ifBlank { url.take(MAX_TITLE_LENGTH) }
    }

    private companion object {
        const val PREFERENCES_NAME = "brightfetch_browser"
        const val KEY_HISTORY = "history_v1"
        const val KEY_BOOKMARKS = "bookmarks_v1"
        const val KEY_SETTINGS = "settings_v1"
        const val MAX_URL_LENGTH = 8_192
        const val MAX_TITLE_LENGTH = 300
    }
}

internal object BrowserJsonCodec {
    fun encodeHistory(entries: List<BrowserHistoryEntry>): String = JSONArray().apply {
        entries.take(BrowserHistoryRules.MAX_ENTRIES).forEach { entry ->
            put(JSONObject().apply {
                put("url", entry.url)
                put("title", entry.title)
                put("visitedAt", entry.visitedAt)
            })
        }
    }.toString()

    fun decodeHistory(raw: String?): List<BrowserHistoryEntry> = decodeArray(raw) { item ->
        val url = item.safeString("url")?.takeIf(BrowserNavigation::isHttpUrl) ?: return@decodeArray null
        BrowserHistoryEntry(
            url = url,
            title = item.safeString("title").orEmpty().ifBlank { url },
            visitedAt = item.optLong("visitedAt", 0L).coerceAtLeast(0L),
        )
    }.distinctBy(BrowserHistoryEntry::url).take(BrowserHistoryRules.MAX_ENTRIES)

    fun encodeBookmarks(entries: List<BrowserBookmark>): String = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().apply {
                put("url", entry.url)
                put("title", entry.title)
                put("createdAt", entry.createdAt)
            })
        }
    }.toString()

    fun decodeBookmarks(raw: String?): List<BrowserBookmark> = decodeArray(raw) { item ->
        val url = item.safeString("url")?.takeIf(BrowserNavigation::isHttpUrl) ?: return@decodeArray null
        BrowserBookmark(
            url = url,
            title = item.safeString("title").orEmpty().ifBlank { url },
            createdAt = item.optLong("createdAt", 0L).coerceAtLeast(0L),
        )
    }.distinctBy(BrowserBookmark::url)

    fun encodeSettings(settings: BrowserSettings): String = JSONObject().apply {
        put("searchEngine", settings.searchEngine.name)
        put("javaScriptEnabled", settings.javaScriptEnabled)
        put("cookiesEnabled", settings.cookiesEnabled)
        put("desktopModeEnabled", settings.desktopModeEnabled)
    }.toString()

    fun decodeSettings(raw: String?): BrowserSettings {
        if (raw.isNullOrBlank()) return BrowserSettings()
        return runCatching {
            val item = JSONObject(raw)
            BrowserSettings(
                searchEngine = item.safeString("searchEngine")
                    ?.let { name -> BrowserSearchEngine.entries.firstOrNull { it.name == name } }
                    ?: BrowserSearchEngine.GOOGLE,
                javaScriptEnabled = item.optBoolean("javaScriptEnabled", true),
                cookiesEnabled = item.optBoolean("cookiesEnabled", true),
                desktopModeEnabled = item.optBoolean("desktopModeEnabled", false),
            )
        }.getOrDefault(BrowserSettings())
    }

    private fun <T : Any> decodeArray(raw: String?, transform: (JSONObject) -> T?): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    runCatching { transform(item) }.getOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.safeString(key: String): String? =
        optString(key, "").trim().takeIf(String::isNotBlank)
}
