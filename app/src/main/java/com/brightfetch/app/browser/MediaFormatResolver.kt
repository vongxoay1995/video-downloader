package com.brightfetch.app.browser

import com.brightfetch.app.download.HlsPlaylistParser
import com.brightfetch.app.model.MediaCandidate
import com.brightfetch.app.model.MediaDownloadOption
import com.brightfetch.app.model.MediaDownloadSource
import com.brightfetch.app.model.VideoFileNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.ceil

data class MediaFormatHttpRequest(
    val url: String,
    val userAgent: String?,
    val cookie: String?,
    val referer: String?,
)

data class MediaFormatTextResponse(
    val finalUrl: String,
    val body: String,
    val mimeType: String? = null,
)

data class MediaFormatProbeResponse(
    val finalUrl: String,
    val mimeType: String?,
    /** Full entity length, not the one-byte Content-Length of a 206 response. */
    val contentLengthBytes: Long?,
)

/** Injectable so resolution behavior remains deterministic in local unit tests. */
interface MediaFormatTransport {
    suspend fun fetchText(
        request: MediaFormatHttpRequest,
        maximumBytes: Int,
    ): MediaFormatTextResponse

    /** Implementations use GET with `Range: bytes=0-0`; HEAD is rejected by many media CDNs. */
    suspend fun probe(request: MediaFormatHttpRequest): MediaFormatProbeResponse
}

/** Resolves a sniffed media URL into concrete, selectable download formats. */
class MediaFormatResolver(
    private val transport: MediaFormatTransport = UrlConnectionMediaFormatTransport(),
    private val maximumNestedMasterDepth: Int = DEFAULT_MAXIMUM_NESTED_MASTER_DEPTH,
    private val maximumVariantsPerMaster: Int = DEFAULT_MAXIMUM_VARIANTS_PER_MASTER,
) {
    init {
        require(maximumNestedMasterDepth >= 0)
        require(maximumVariantsPerMaster > 0)
    }

    suspend fun resolve(candidate: MediaCandidate): List<MediaDownloadOption> =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            if (!isAllowedMediaUrl(candidate.url)) {
                return@withContext listOf(
                    directOption(
                        candidate = candidate,
                        downloadUrl = candidate.url,
                        mimeType = candidate.mimeType,
                        exactSizeBytes = candidate.contentLengthBytes,
                        unavailableReason = "Only public HTTP(S) video URLs are supported",
                    )
                )
            }

            val request = candidate.request(candidate.url)
            if (candidate.isHls) {
                resolveHls(
                    candidate = candidate,
                    request = request,
                    inherited = VariantContext.from(candidate),
                    depth = 0,
                    visited = emptySet(),
                ).sortedForDisplay()
            } else {
                resolveDirect(candidate, request).sortedForDisplay()
            }
        }

    private suspend fun resolveDirect(
        candidate: MediaCandidate,
        request: MediaFormatHttpRequest,
    ): List<MediaDownloadOption> {
        currentCoroutineContext().ensureActive()
        val probe = try {
            transport.probe(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        currentCoroutineContext().ensureActive()

        val finalUrl = probe?.finalUrl?.takeIf(::isAllowedMediaUrl) ?: candidate.url
        val responseMime = normalizeMimeType(probe?.mimeType)
        if (isHlsMimeType(responseMime) || MediaUrlClassifier.isHls(finalUrl)) {
            return resolveHls(
                candidate = candidate,
                request = candidate.request(finalUrl),
                inherited = VariantContext.from(candidate),
                depth = 0,
                visited = emptySet(),
            )
        }

        val resolvedMime = responseMime ?: normalizeMimeType(candidate.mimeType)
        val unsupportedMime = resolvedMime?.takeIf(::isClearlyUnsupportedDirectMime)
        val redirectedToUnsupportedUrl = probe != null && !isAllowedMediaUrl(probe.finalUrl)
        return listOf(
            directOption(
                candidate = candidate,
                downloadUrl = finalUrl,
                mimeType = resolvedMime,
                exactSizeBytes = probe?.contentLengthBytes?.takeIf { it > 0L }
                    ?: candidate.contentLengthBytes?.takeIf { it > 0L },
                unavailableReason = when {
                    redirectedToUnsupportedUrl -> "The media redirect is not a supported public URL"
                    unsupportedMime != null -> "The server returned $unsupportedMime instead of a video"
                    else -> null
                },
            )
        )
    }

    private suspend fun resolveHls(
        candidate: MediaCandidate,
        request: MediaFormatHttpRequest,
        inherited: VariantContext,
        depth: Int,
        visited: Set<String>,
    ): List<MediaDownloadOption> {
        currentCoroutineContext().ensureActive()
        if (depth > maximumNestedMasterDepth) {
            return listOf(hlsOption(request.url, inherited, null, "Nested HLS playlist is too deep"))
        }

        val response = try {
            transport.fetchText(request, MAXIMUM_MANIFEST_BYTES)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return listOf(
                hlsOption(
                    request.url,
                    inherited,
                    null,
                    error.message?.takeIf(String::isNotBlank) ?: "Unable to read HLS playlist",
                )
            )
        }
        currentCoroutineContext().ensureActive()

        val finalUrl = response.finalUrl
        if (!isAllowedMediaUrl(finalUrl)) {
            return listOf(hlsOption(finalUrl, inherited, null, "The playlist redirected to an unsupported URL"))
        }
        if (finalUrl in visited) {
            return listOf(hlsOption(finalUrl, inherited, null, "HLS playlist cycle detected"))
        }
        if (!isValidHlsPlaylist(response.body)) {
            return listOf(hlsOption(finalUrl, inherited, null, "Invalid HLS playlist"))
        }

        val variants = HlsPlaylistParser.parseMasterPlaylist(response.body)
        val hasMasterTag = response.body.lineSequence().any {
            it.trim().startsWith("#EXT-X-STREAM-INF", ignoreCase = true)
        }
        if (hasMasterTag) {
            if (variants.isEmpty()) {
                return listOf(hlsOption(finalUrl, inherited, null, "HLS master playlist has no variants"))
            }
            val nextVisited = visited + finalUrl
            return variants
                .sortedWith(
                    compareByDescending<HlsPlaylistParser.Variant> { it.height ?: -1 }
                        .thenByDescending {
                            it.averageBandwidthBitsPerSecond ?: it.bandwidthBitsPerSecond ?: -1L
                        }
                )
                .take(maximumVariantsPerMaster)
                .flatMap { variant ->
                    currentCoroutineContext().ensureActive()
                    val childContext = inherited.with(variant)
                    val childUrl = resolveReference(finalUrl, variant.reference)
                    if (childUrl == null || !isAllowedMediaUrl(childUrl)) {
                        listOf(
                            hlsOption(
                                childUrl ?: variant.reference,
                                childContext,
                                null,
                                "HLS variant URL is invalid",
                            )
                        )
                    } else {
                        resolveHls(
                            candidate = candidate,
                            request = candidate.request(childUrl),
                            inherited = childContext,
                            depth = depth + 1,
                            visited = nextVisited,
                        )
                    }
                }
                .distinctBy { option ->
                    listOf(option.downloadUrl, option.height, option.bandwidthBitsPerSecond, option.codecs)
                }
        }

        val parsed = HlsPlaylistParser.parseMediaPlaylist(response.body)
        val duration = parsed.totalDurationSeconds ?: inherited.durationSeconds
        val context = inherited.copy(durationSeconds = duration)
        val unavailableReason = when {
            !context.audioGroup.isNullOrBlank() -> "Separate HLS audio tracks are not supported"
            !parsed.isVod -> "Live HLS streams are not supported"
            parsed.usesByteRanges -> "Byte-range HLS is not supported"
            parsed.hasUnsupportedEncryption -> "This HLS encryption method is not supported"
            parsed.mediaSegmentCount == 0 -> "HLS playlist has no media segments"
            else -> null
        }
        return listOf(hlsOption(finalUrl, context, parsed, unavailableReason))
    }

    private fun directOption(
        candidate: MediaCandidate,
        downloadUrl: String,
        mimeType: String?,
        exactSizeBytes: Long?,
        unavailableReason: String?,
    ): MediaDownloadOption {
        val resolvedMime = mimeType ?: candidate.mimeType ?: "video/mp4"
        val extension = outputExtension(downloadUrl, candidate.preferredFileName, resolvedMime)
        return MediaDownloadOption(
            downloadUrl = downloadUrl,
            source = MediaDownloadSource.DIRECT,
            label = candidate.height?.takeIf { it > 0 }?.let { "${it}p" } ?: "Original",
            mimeType = resolvedMime,
            outputExtension = extension,
            width = candidate.width,
            height = candidate.height,
            durationSeconds = candidate.durationSeconds?.toDouble(),
            estimatedSizeBytes = exactSizeBytes,
            sizeIsExact = exactSizeBytes != null,
            unavailableReason = unavailableReason,
        )
    }

    private fun hlsOption(
        downloadUrl: String,
        context: VariantContext,
        playlist: HlsPlaylistParser.MediaPlaylist?,
        unavailableReason: String?,
    ): MediaDownloadOption {
        val duration = playlist?.totalDurationSeconds ?: context.durationSeconds
        return MediaDownloadOption(
            downloadUrl = downloadUrl,
            source = MediaDownloadSource.HLS,
            label = qualityLabel(context),
            mimeType = HLS_MIME_TYPE,
            outputExtension = "mp4",
            width = context.width,
            height = context.height,
            bandwidthBitsPerSecond = context.bandwidthBitsPerSecond,
            averageBandwidthBitsPerSecond = context.averageBandwidthBitsPerSecond,
            codecs = context.codecs,
            durationSeconds = duration,
            segmentCount = playlist?.mediaSegmentCount,
            estimatedSizeBytes = estimateHlsSizeBytes(
                durationSeconds = duration,
                averageBandwidthBitsPerSecond = context.averageBandwidthBitsPerSecond,
                bandwidthBitsPerSecond = context.bandwidthBitsPerSecond,
            ),
            sizeIsExact = false,
            unavailableReason = unavailableReason,
        )
    }

    private data class VariantContext(
        val width: Int?,
        val height: Int?,
        val bandwidthBitsPerSecond: Long?,
        val averageBandwidthBitsPerSecond: Long?,
        val codecs: String?,
        val name: String?,
        val audioGroup: String?,
        val durationSeconds: Double?,
    ) {
        fun with(variant: HlsPlaylistParser.Variant): VariantContext = copy(
            width = variant.width ?: width,
            height = variant.height ?: height,
            bandwidthBitsPerSecond = variant.bandwidthBitsPerSecond ?: bandwidthBitsPerSecond,
            averageBandwidthBitsPerSecond = variant.averageBandwidthBitsPerSecond
                ?: averageBandwidthBitsPerSecond,
            codecs = variant.codecs ?: codecs,
            name = variant.name ?: name,
            audioGroup = variant.audioGroup ?: audioGroup,
        )

        companion object {
            fun from(candidate: MediaCandidate): VariantContext = VariantContext(
                width = candidate.width,
                height = candidate.height,
                bandwidthBitsPerSecond = null,
                averageBandwidthBitsPerSecond = null,
                codecs = null,
                name = null,
                audioGroup = null,
                durationSeconds = candidate.durationSeconds?.toDouble(),
            )
        }
    }

    private fun qualityLabel(context: VariantContext): String {
        val base = context.height?.takeIf { it > 0 }?.let { "${it}p" }
            ?: context.name?.takeIf(String::isNotBlank)
            ?: "HLS"
        val bitrate = context.averageBandwidthBitsPerSecond ?: context.bandwidthBitsPerSecond
        if (bitrate == null || bitrate <= 0L) return base
        val rate = when {
            bitrate >= 1_000_000L -> String.format(Locale.US, "%.1f Mbps", bitrate / 1_000_000.0)
            else -> String.format(Locale.US, "%.0f Kbps", bitrate / 1_000.0)
        }
        return if (base == "HLS") rate else "$base • $rate"
    }

    companion object {
        const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
        const val DEFAULT_MAXIMUM_NESTED_MASTER_DEPTH = 3
        const val DEFAULT_MAXIMUM_VARIANTS_PER_MASTER = 12
        const val MAXIMUM_MANIFEST_BYTES = 2 * 1024 * 1024
    }
}

internal fun estimateHlsSizeBytes(
    durationSeconds: Double?,
    averageBandwidthBitsPerSecond: Long?,
    bandwidthBitsPerSecond: Long?,
): Long? {
    val duration = durationSeconds?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val bitrate = (averageBandwidthBitsPerSecond ?: bandwidthBitsPerSecond)
        ?.takeIf { it > 0L }
        ?: return null
    val bytes = duration * bitrate.toDouble() / 8.0
    if (!bytes.isFinite() || bytes <= 0.0 || bytes > Long.MAX_VALUE.toDouble()) return null
    return ceil(bytes).toLong()
}

private fun List<MediaDownloadOption>.sortedForDisplay(): List<MediaDownloadOption> = sortedWith(
    compareByDescending<MediaDownloadOption> { it.isAvailable }
        .thenByDescending { it.height ?: -1 }
        .thenByDescending { it.averageBandwidthBitsPerSecond ?: it.bandwidthBitsPerSecond ?: -1L }
)

private fun MediaCandidate.request(url: String): MediaFormatHttpRequest = MediaFormatHttpRequest(
    url = url,
    userAgent = userAgent,
    cookie = cookie,
    referer = pageUrl,
)

private fun isValidHlsPlaylist(body: String): Boolean = body
    .lineSequence()
    .map { it.trim().removePrefix("\uFEFF") }
    .firstOrNull(String::isNotEmpty)
    ?.equals("#EXTM3U", ignoreCase = true) == true

private fun isAllowedMediaUrl(value: String): Boolean {
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return false
    val host = uri.host?.lowercase(Locale.US) ?: return false
    return host != "youtu.be" &&
        !host.endsWith("youtube.com") &&
        !host.endsWith("youtube-nocookie.com")
}

private fun resolveReference(baseUrl: String, reference: String): String? = runCatching {
    URI(baseUrl).resolve(reference).toString()
}.getOrNull()

private fun normalizeMimeType(value: String?): String? = value
    ?.substringBefore(';')
    ?.trim()
    ?.lowercase(Locale.US)
    ?.takeIf(String::isNotBlank)

private fun isHlsMimeType(value: String?): Boolean = value?.contains("mpegurl", ignoreCase = true) == true

private fun isClearlyUnsupportedDirectMime(value: String): Boolean {
    if (value.startsWith("video/")) return false
    return value !in setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/force-download",
    )
}

private fun outputExtension(url: String, preferredFileName: String?, mimeType: String?): String {
    val pathName = runCatching { URI(url).path.substringAfterLast('/') }.getOrNull()
    val rawName = preferredFileName?.takeIf(String::isNotBlank)
        ?: pathName?.takeIf(String::isNotBlank)
        ?: "video"
    return VideoFileNames.normalize(rawName, mimeType).substringAfterLast('.', "mp4")
}

private class UrlConnectionMediaFormatTransport : MediaFormatTransport {
    override suspend fun fetchText(
        request: MediaFormatHttpRequest,
        maximumBytes: Int,
    ): MediaFormatTextResponse = withContext(Dispatchers.IO) {
        require(maximumBytes > 0)
        val connection = openConnection(request, rangeProbe = false)
        val cancellationHandle = disconnectOnCancellation(connection)
        try {
            currentCoroutineContext().ensureActive()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Playlist returned HTTP $responseCode")
            }
            val inputStream = when (connection.contentEncoding?.lowercase(Locale.US)) {
                "gzip" -> GZIPInputStream(connection.inputStream)
                "deflate" -> InflaterInputStream(connection.inputStream)
                else -> connection.inputStream
            }
            val body = inputStream.buffered().use { input ->
                val output = ByteArrayOutputStream(minOf(maximumBytes, 64 * 1024))
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maximumBytes) throw IOException("HLS playlist is too large")
                    output.write(buffer, 0, read)
                }
                output.toString(StandardCharsets.UTF_8.name())
            }
            MediaFormatTextResponse(
                finalUrl = connection.url.toString(),
                body = body,
                mimeType = normalizeMimeType(connection.contentType),
            )
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    override suspend fun probe(request: MediaFormatHttpRequest): MediaFormatProbeResponse =
        withContext(Dispatchers.IO) {
            val connection = openConnection(request, rangeProbe = true)
            val cancellationHandle = disconnectOnCancellation(connection)
            try {
                currentCoroutineContext().ensureActive()
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) throw IOException("Media probe returned HTTP $responseCode")
                val totalFromRange = connection.getHeaderField("Content-Range")
                    ?.substringAfterLast('/', "")
                    ?.trim()
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                val contentLength = totalFromRange ?: connection.contentLengthLong
                    .takeIf { responseCode == HttpURLConnection.HTTP_OK && it > 0L }
                currentCoroutineContext().ensureActive()
                runCatching { connection.inputStream.use { it.read() } }
                currentCoroutineContext().ensureActive()
                MediaFormatProbeResponse(
                    finalUrl = connection.url.toString(),
                    mimeType = normalizeMimeType(connection.contentType),
                    contentLengthBytes = contentLength,
                )
            } finally {
                cancellationHandle?.dispose()
                connection.disconnect()
            }
        }

    private fun openConnection(
        request: MediaFormatHttpRequest,
        rangeProbe: Boolean,
    ): HttpURLConnection = (URL(request.url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = CONNECT_TIMEOUT_MILLIS
        readTimeout = READ_TIMEOUT_MILLIS
        requestMethod = "GET"
        setRequestProperty("Accept", if (rangeProbe) "*/*" else HLS_ACCEPT)
        setRequestProperty("Accept-Encoding", "identity")
        if (rangeProbe) setRequestProperty("Range", "bytes=0-0")
        request.userAgent?.takeIf(String::isNotBlank)?.let { setRequestProperty("User-Agent", it) }
        request.cookie?.takeIf(String::isNotBlank)?.let { setRequestProperty("Cookie", it) }
        request.referer?.takeIf(String::isNotBlank)?.let { setRequestProperty("Referer", it) }
    }

    private suspend fun disconnectOnCancellation(connection: HttpURLConnection) =
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }

    companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 12_000
        const val HLS_ACCEPT = "application/vnd.apple.mpegurl,application/x-mpegURL,text/plain,*/*"
    }
}
