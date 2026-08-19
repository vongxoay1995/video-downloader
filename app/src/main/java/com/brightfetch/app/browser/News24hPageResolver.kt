package com.brightfetch.app.browser

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

data class ResolvedNews24hMedia(
    val mediaUrl: String,
    val pageUrl: String,
    val title: String?,
    val mimeType: String,
    val cookie: String?,
)

/** Extracts the real HLS source hidden behind the blob URL used by the 24h video player. */
object News24hPageResolver {
    private const val MAX_HTML_BYTES = 3 * 1024 * 1024

    fun supports(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        return host == "24h.com.vn" || host.endsWith(".24h.com.vn")
    }

    fun resolve(pageUrl: String, userAgent: String?, initialCookie: String?): ResolvedNews24hMedia? {
        if (!supports(pageUrl)) return null
        val connection = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.6")
            userAgent?.takeIf(String::isNotBlank)?.let { setRequestProperty("User-Agent", it) }
            initialCookie?.takeIf(String::isNotBlank)?.let { setRequestProperty("Cookie", it) }
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val finalUrl = connection.url.toString()
            if (!supports(finalUrl)) return null
            val html = readHtml(connection) ?: return null
            val parsed = News24hMediaPageParser.extract(html) ?: return null
            val responseCookies = connection.headerFields
                .filterKeys { it.equals("Set-Cookie", ignoreCase = true) }
                .values
                .flatten()
                .map { it.substringBefore(';').trim() }
                .filter(String::isNotBlank)
            val cookie = (initialCookie.orEmpty().split(';') + responseCookies)
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinctBy { it.substringBefore('=') }
                .joinToString("; ")
                .takeIf(String::isNotBlank)
            return ResolvedNews24hMedia(
                mediaUrl = parsed.mediaUrl,
                pageUrl = finalUrl,
                title = parsed.title,
                mimeType = if (MediaUrlClassifier.isHls(parsed.mediaUrl)) {
                    "application/vnd.apple.mpegurl"
                } else {
                    "video/mp4"
                },
                cookie = cookie,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readHtml(connection: HttpURLConnection): String? {
        val source = when (connection.contentEncoding?.lowercase(Locale.US)) {
            "gzip" -> GZIPInputStream(connection.inputStream)
            "deflate" -> InflaterInputStream(connection.inputStream)
            else -> connection.inputStream
        }
        return source.buffered().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_HTML_BYTES) return null
                output.write(buffer, 0, read)
            }
            output.toString(StandardCharsets.UTF_8.name())
        }
    }
}

data class ParsedNews24hMedia(val mediaUrl: String, val title: String?)

object News24hMediaPageParser {
    private val contentUrlPattern = Regex(
        "\\\"contentUrl\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val sourcePattern = Regex(
        "<source[^>]+src\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun extract(html: String): ParsedNews24hMedia? {
        val mediaUrl = contentUrlPattern.findAll(html)
            .mapNotNull { decodeJsonString(it.groupValues[1]) }
            .firstOrNull(MediaUrlClassifier::isLikelyNetworkMedia)
            ?: sourcePattern.findAll(html)
                .map { decodeHtmlEntities(it.groupValues[1]) }
                .firstOrNull(MediaUrlClassifier::isLikelyNetworkMedia)
            ?: return null
        return ParsedNews24hMedia(mediaUrl, extractTitle(html))
    }

    private fun extractTitle(html: String): String? {
        val ogTitle = Regex(
            "<meta[^>]+property\\s*=\\s*[\\\"']og:title[\\\"'][^>]+content\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.getOrNull(1)
        val title = ogTitle ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)
        return title?.let(::decodeHtmlEntities)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf(String::isNotBlank)
    }

    private fun decodeJsonString(encoded: String): String? {
        val result = StringBuilder(encoded.length)
        var index = 0
        while (index < encoded.length) {
            val char = encoded[index++]
            if (char != '\\') {
                result.append(char)
                continue
            }
            if (index >= encoded.length) return null
            when (val escaped = encoded[index++]) {
                '\"', '\\', '/' -> result.append(escaped)
                'u' -> {
                    if (index + 4 > encoded.length) return null
                    result.append(encoded.substring(index, index + 4).toIntOrNull(16)?.toChar() ?: return null)
                    index += 4
                }
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                else -> return null
            }
        }
        return result.toString()
    }

    private fun decodeHtmlEntities(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&ndash;", "–", ignoreCase = true)
        .replace("&mdash;", "—", ignoreCase = true)
}
