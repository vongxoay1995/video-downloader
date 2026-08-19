package com.brightfetch.app.browser

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

data class ResolvedTikTokMedia(
    val mediaUrl: String,
    val pageUrl: String,
    val cookie: String?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Long?,
    val contentLengthBytes: Long?,
)

data class ParsedTikTokMedia(
    val mediaUrl: String,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Long?,
)

/**
 * Resolves the public HTML returned for a TikTok share page. TikTok signs its media URLs and
 * binds them to cookies from the same response, so the URL and cookie must be kept together.
 */
object TikTokPageResolver {
    private const val MAX_REDIRECTS = 6
    private const val MAX_HTML_BYTES = 6 * 1024 * 1024

    fun supports(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    /** Returns an ID only for a single-video page; profile/feed pages intentionally return null. */
    fun videoPageId(value: String?): String? {
        val uri = value?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (!supports(value)) return null
        Regex("/(?:video|v)/(\\d+)(?:\\.html)?(?:/|$)", RegexOption.IGNORE_CASE)
            .find(uri.path.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return uri.rawQuery
            .orEmpty()
            .split('&')
            .firstNotNullOfOrNull { parameter ->
                val name = parameter.substringBefore('=', "")
                val candidate = parameter.substringAfter('=', "")
                candidate.takeIf { name == "share_item_id" && it.all(Char::isDigit) }
            }
    }

    fun resolve(
        pageUrl: String,
        userAgent: String?,
        initialCookie: String?,
    ): ResolvedTikTokMedia? {
        if (!supports(pageUrl)) return null

        val cookieJar = linkedMapOf<String, String>()
        mergeCookieHeader(cookieJar, initialCookie)
        var currentUrl = pageUrl

        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.7,en;q=0.6")
                userAgent?.takeIf(String::isNotBlank)?.let { setRequestProperty("User-Agent", it) }
                cookieHeader(cookieJar)?.let { setRequestProperty("Cookie", it) }
            }

            try {
                val responseCode = connection.responseCode
                mergeResponseCookies(cookieJar, connection)

                if (responseCode in 300..399) {
                    if (redirectCount >= MAX_REDIRECTS) return null
                    val location = connection.getHeaderField("Location") ?: return null
                    val nextUrl = runCatching { URI(currentUrl).resolve(location).toString() }.getOrNull()
                        ?: return null
                    // A TikTok share link should only redirect to another TikTok web host.
                    if (!supports(nextUrl)) return null
                    currentUrl = nextUrl
                    return@repeat
                }

                if (responseCode !in 200..299) return null
                val html = readHtml(connection) ?: return null
                val parsed = TikTokMediaPageParser.extractPreferredMedia(html) ?: return null
                val cookie = cookieHeader(cookieJar)
                val probe = runCatching {
                    probeMediaFile(
                        mediaUrl = parsed.mediaUrl,
                        pageUrl = currentUrl,
                        userAgent = userAgent,
                        cookie = cookie,
                    )
                }.getOrNull()
                return ResolvedTikTokMedia(
                    mediaUrl = parsed.mediaUrl,
                    pageUrl = currentUrl,
                    cookie = cookie,
                    mimeType = probe?.mimeType,
                    width = parsed.width,
                    height = parsed.height,
                    durationSeconds = parsed.durationSeconds,
                    contentLengthBytes = probe?.contentLengthBytes,
                )
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun probeMediaFile(
        mediaUrl: String,
        pageUrl: String,
        userAgent: String?,
        cookie: String?,
    ): MediaFileProbe? {
        val connection = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Range", "bytes=0-0")
            setRequestProperty("Referer", pageUrl)
            userAgent?.takeIf(String::isNotBlank)?.let { setRequestProperty("User-Agent", it) }
            cookie?.takeIf(String::isNotBlank)?.let { setRequestProperty("Cookie", it) }
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) return null
            val totalFromRange = connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/', "")
                ?.trim()
                ?.toLongOrNull()
            val contentLength = totalFromRange
                ?: connection.contentLengthLong.takeIf { responseCode == HttpURLConnection.HTTP_OK && it > 0L }
            val mimeType = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf(String::isNotBlank)
            runCatching { connection.inputStream.use { it.read() } }
            return MediaFileProbe(contentLength, mimeType)
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

    private fun mergeResponseCookies(
        cookieJar: MutableMap<String, String>,
        connection: HttpURLConnection,
    ) {
        connection.headerFields
            .filterKeys { it.equals("Set-Cookie", ignoreCase = true) }
            .values
            .flatten()
            .forEach { setCookie -> mergeCookiePair(cookieJar, setCookie.substringBefore(';')) }
    }

    private fun mergeCookieHeader(cookieJar: MutableMap<String, String>, header: String?) {
        header.orEmpty().split(';').forEach { mergeCookiePair(cookieJar, it) }
    }

    private fun mergeCookiePair(cookieJar: MutableMap<String, String>, pair: String) {
        val name = pair.substringBefore('=', "").trim()
        val value = pair.substringAfter('=', "").trim()
        if (name.isNotBlank() && value.isNotBlank()) cookieJar[name] = value
    }

    private fun cookieHeader(cookieJar: Map<String, String>): String? = cookieJar
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString("; ") { (name, value) -> "$name=$value" }

    private data class MediaFileProbe(
        val contentLengthBytes: Long?,
        val mimeType: String?,
    )
}

/** Pure HTML/JSON-string extraction so the important signed-URL behavior is JVM-testable. */
object TikTokMediaPageParser {
    private val preferredKeys = listOf("playAddr", "playUrl", "play_url", "downloadAddr")
    private val videoObjectStart = Regex("\\\"video\\\"\\s*:\\s*\\{")

    fun extractPreferredMedia(html: String): ParsedTikTokMedia? {
        for (key in preferredKeys) {
            val pattern = Regex(
                "\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
                RegexOption.IGNORE_CASE,
            )
            pattern.findAll(html).forEach { match ->
                val decoded = decodeJsonString(match.groupValues[1]) ?: return@forEach
                if (!MediaUrlClassifier.isLikelyNetworkMedia(decoded)) return@forEach
                val windowStart = (match.range.first - METADATA_LOOKBACK).coerceAtLeast(0)
                val prefix = html.substring(windowStart, match.range.first)
                val objectStart = videoObjectStart.findAll(prefix)
                    .lastOrNull()
                    ?.range
                    ?.first
                    ?.plus(windowStart)
                    ?: windowStart
                val context = html.substring(objectStart, match.range.last + 1)
                return ParsedTikTokMedia(
                    mediaUrl = decoded,
                    width = numberField(context, "width")?.toIntOrNull(),
                    height = numberField(context, "height")?.toIntOrNull(),
                    durationSeconds = numberField(context, "duration")?.toLongOrNull(),
                )
            }
        }
        return null
    }

    fun extractPreferredMediaUrl(html: String): String? = extractPreferredMedia(html)?.mediaUrl

    private fun numberField(context: String, key: String): String? = Regex(
        "\\\"${Regex.escape(key)}\\\"\\s*:\\s*(\\d+)",
        RegexOption.IGNORE_CASE,
    ).find(context)?.groupValues?.getOrNull(1)

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
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> {
                    if (index + 4 > encoded.length) return null
                    val codePoint = encoded.substring(index, index + 4).toIntOrNull(16) ?: return null
                    result.append(codePoint.toChar())
                    index += 4
                }
                else -> return null
            }
        }
        return result.toString()
    }

    private const val METADATA_LOOKBACK = 100_000
}
