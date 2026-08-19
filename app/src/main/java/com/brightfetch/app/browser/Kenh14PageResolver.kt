package com.brightfetch.app.browser

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

data class ResolvedKenh14Media(
    val mediaUrl: String,
    val pageUrl: String,
    val title: String,
    val mimeType: String,
    val contentLengthBytes: Long?,
    val cookie: String?,
)

/** Resolves Kênh14's data-vid attribute to the original MP4 instead of a player HLS rendition. */
object Kenh14PageResolver {
    private const val MAX_HTML_BYTES = 2 * 1024 * 1024

    fun supports(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        return host == "kenh14.vn" || host.endsWith(".kenh14.vn")
    }

    fun resolve(pageUrl: String, userAgent: String?, initialCookie: String?): ResolvedKenh14Media? {
        if (!supports(pageUrl)) return null
        val connection = openConnection(pageUrl, pageUrl, userAgent, initialCookie, rangeProbe = false)
        try {
            if (connection.responseCode !in 200..299) return null
            val finalPageUrl = connection.url.toString()
            if (!supports(finalPageUrl)) return null
            val html = readHtml(connection) ?: return null
            val parsed = Kenh14MediaPageParser.extract(html) ?: return null
            val cookie = mergeCookies(initialCookie, responseCookies(connection))
            val probe = runCatching {
                probeMedia(parsed.mediaUrl, finalPageUrl, userAgent, cookie)
            }.getOrNull()
            return ResolvedKenh14Media(
                mediaUrl = parsed.mediaUrl,
                pageUrl = finalPageUrl,
                title = parsed.title,
                mimeType = probe?.mimeType ?: "video/mp4",
                contentLengthBytes = probe?.contentLengthBytes,
                cookie = cookie,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun probeMedia(
        mediaUrl: String,
        pageUrl: String,
        userAgent: String?,
        cookie: String?,
    ): MediaProbe? {
        val connection = openConnection(mediaUrl, pageUrl, userAgent, cookie, rangeProbe = true)
        try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val contentLength = connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/', "")
                ?.trim()
                ?.toLongOrNull()
                ?: connection.contentLengthLong.takeIf { code == HttpURLConnection.HTTP_OK && it > 0L }
            val mimeType = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf(String::isNotBlank)
            runCatching { connection.inputStream.use { it.read() } }
            return MediaProbe(contentLength, mimeType)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        referer: String,
        userAgent: String?,
        cookie: String?,
        rangeProbe: Boolean,
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 20_000
        requestMethod = "GET"
        setRequestProperty("Accept", if (rangeProbe) "*/*" else "text/html,application/xhtml+xml,*/*;q=0.8")
        setRequestProperty("Accept-Encoding", "identity")
        setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.6")
        setRequestProperty("Referer", referer)
        if (rangeProbe) setRequestProperty("Range", "bytes=0-0")
        userAgent?.takeIf(String::isNotBlank)?.let { setRequestProperty("User-Agent", it) }
        cookie?.takeIf(String::isNotBlank)?.let { setRequestProperty("Cookie", it) }
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

    private fun responseCookies(connection: HttpURLConnection): List<String> = connection.headerFields
        .filterKeys { it.equals("Set-Cookie", ignoreCase = true) }
        .values
        .flatten()
        .map { it.substringBefore(';').trim() }

    private fun mergeCookies(initialCookie: String?, responseCookies: List<String>): String? {
        val jar = linkedMapOf<String, String>()
        (initialCookie.orEmpty().split(';') + responseCookies).forEach { pair ->
            val name = pair.substringBefore('=', "").trim()
            val value = pair.substringAfter('=', "").trim()
            if (name.isNotBlank() && value.isNotBlank()) jar[name] = value
        }
        return jar.entries.joinToString("; ") { (name, value) -> "$name=$value" }.takeIf(String::isNotBlank)
    }

    private data class MediaProbe(val contentLengthBytes: Long?, val mimeType: String?)
}

data class ParsedKenh14Media(val mediaUrl: String, val title: String)

object Kenh14MediaPageParser {
    private val dataVideoPattern = Regex(
        "data-vid\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']",
        RegexOption.IGNORE_CASE,
    )

    fun extract(html: String): ParsedKenh14Media? {
        val rawVideo = dataVideoPattern.findAll(html)
            .map { decodeHtmlEntities(it.groupValues[1]) }
            .firstOrNull { it.contains(".mp4", ignoreCase = true) }
            ?: return null
        val mediaUrl = when {
            rawVideo.startsWith("https://", ignoreCase = true) || rawVideo.startsWith("http://", ignoreCase = true) -> rawVideo
            rawVideo.startsWith("//") -> "https:$rawVideo"
            else -> "https://${rawVideo.trimStart('/')}"
        }
        if (!MediaUrlClassifier.isLikelyNetworkMedia(mediaUrl)) return null
        val title = extractTitle(html) ?: return null
        return ParsedKenh14Media(mediaUrl, title)
    }

    private fun extractTitle(html: String): String? {
        val ogTitle = Regex(
            "<meta[^>]+property\\s*=\\s*[\\\"']og:title[\\\"'][^>]+content\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.getOrNull(1)
        val title = ogTitle ?: Regex(
            "<title[^>]*>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.getOrNull(1)
        return title
            ?.let(::decodeHtmlEntities)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private fun decodeHtmlEntities(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&ndash;", "–", ignoreCase = true)
        .replace("&mdash;", "—", ignoreCase = true)
}
