package com.brightfetch.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.brightfetch.app.MainActivity
import com.brightfetch.app.R
import com.brightfetch.app.BuildConfig
import com.brightfetch.app.model.VideoFileNames
import com.brightfetch.app.storage.PublicVideoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VideoDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val notificationId = id.hashCode()
    private val url = inputData.getString(DownloadContract.KEY_URL).orEmpty()
    private val requestedName = inputData.getString(DownloadContract.KEY_FILE_NAME).orEmpty()
    private val userAgent = inputData.getString(DownloadContract.KEY_USER_AGENT)
    private val cookie = inputData.getString(DownloadContract.KEY_COOKIE)
    private val referer = inputData.getString(DownloadContract.KEY_REFERER)
    private val isHls = inputData.getBoolean(DownloadContract.KEY_IS_HLS, false)
    private val requestedMimeType = inputData.getString(DownloadContract.KEY_MIME_TYPE)
    private var resolvedMimeType: String? = requestedMimeType

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!isAllowedUrl(url)) return@withContext failure("Only public HTTP(S) video URLs are supported")
        createNotificationChannel()
        setForeground(createForegroundInfo(0, "Starting download…"))

        val folder = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?.resolve("BrightFetch/.partials")
            ?: return@withContext failure("External storage is unavailable")
        if (!folder.exists() && !folder.mkdirs()) {
            return@withContext failure("Cannot create the video folder")
        }

        val hlsDownload = isHls || url.lowercase(Locale.US).substringBefore('?').endsWith(".m3u8")
        val safeName = VideoFileNames.normalize(
            requestedName.ifBlank { "video_${id}.mp4" },
            mimeType = requestedMimeType,
            isHls = hlsDownload,
        )
        val partFile = folder.resolve("${id}_$safeName.part")
        val hlsSourceFile = folder.resolve("${id}_hls_source.ts.part")
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Starting video download: sourceUrl=$url outputName=$safeName referer=$referer")
        }

        try {
            val ok = if (hlsDownload) {
                if (partFile.exists()) partFile.delete()
                if (hlsSourceFile.exists()) hlsSourceFile.delete()
                val downloaded = downloadHls(url, hlsSourceFile)
                if (!downloaded) return@withContext failure("Download was interrupted")
                updateProgress(96, hlsSourceFile.length(), hlsSourceFile.length(), "Packaging MP4…")
                HlsMp4Remuxer.remux(hlsSourceFile, partFile)
                resolvedMimeType = "video/mp4"
                true
            } else {
                downloadDirect(url, partFile)
            }
            if (!ok) return@withContext failure("Download was interrupted")
            updateProgress(99, partFile.length(), partFile.length(), "Saving to Movies/BrightFetch…")
            val published = PublicVideoStore.publish(
                context = applicationContext,
                source = partFile,
                requestedName = safeName,
                mimeType = resolvedMimeType,
            )
            partFile.delete()
            Log.i(
                TAG,
                "Download complete: sourceUrl=$url contentUri=${published.uri} publicPath=${published.displayPath}",
            )
            updateProgress(100, published.size, published.size, "Saved to Movies/BrightFetch")
            Result.success(
                Data.Builder()
                    .putString(DownloadContract.KEY_OUTPUT_PATH, published.displayPath)
                    .putString(DownloadContract.KEY_OUTPUT_URI, published.uri.toString())
                    .build()
            )
        } catch (unsupported: UnsupportedMediaException) {
            partFile.delete()
            failure(unsupported.message ?: "Unsupported video")
        } catch (unsupported: HlsMp4Remuxer.HlsRemuxException) {
            partFile.delete()
            failure(unsupported.message ?: "This HLS stream cannot be packaged as MP4")
        } catch (error: Exception) {
            if (runAttemptCount < 2) Result.retry() else failure(error.message ?: "Download failed")
        } finally {
            hlsSourceFile.delete()
        }
    }

    private suspend fun downloadDirect(sourceUrl: String, output: File): Boolean {
        val existing = output.takeIf(File::exists)?.length() ?: 0L
        val connection = openConnection(sourceUrl, if (existing > 0L) existing else null, requestAsMedia = true)
        try {
            val code = connection.responseCode
            if (code == HTTP_RANGE_NOT_SATISFIABLE && existing > 0L) {
                val remoteSize = connection.getHeaderField("Content-Range")
                    ?.substringAfter("*/", "")
                    ?.trim()
                    ?.toLongOrNull()
                if (remoteSize == existing) return true
                output.delete()
                return downloadDirect(sourceUrl, output)
            }
            if (code !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                throw IllegalStateException("Server returned HTTP $code")
            }
            val responseMimeType = connection.contentType
                .orEmpty()
                .substringBefore(';')
                .trim()
                .lowercase(Locale.US)
            val allowedBinaryMimeTypes = setOf(
                "",
                "application/octet-stream",
                "binary/octet-stream",
                "application/force-download",
            )
            if (!responseMimeType.startsWith("video/") && responseMimeType !in allowedBinaryMimeTypes) {
                throw UnsupportedMediaException("The server returned $responseMimeType instead of a video")
            }
            if (responseMimeType.startsWith("video/")) resolvedMimeType = responseMimeType
            val append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (!append && output.exists()) output.delete()
            val base = if (append) existing else 0L
            val remaining = connection.contentLengthLong.coerceAtLeast(0L)
            val total = if (remaining > 0L) base + remaining else 0L
            var downloaded = base
            var lastPercent = -1
            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                BufferedOutputStream(FileOutputStream(output, append), BUFFER_SIZE).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (isStopped) return false
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        val percent = if (total > 0L) ((downloaded * 100L) / total).toInt() else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            updateProgress(percent, downloaded, total, "Downloading $requestedName")
                        }
                    }
                }
            }
            return output.exists() && output.length() > 0L
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadHls(manifestUrl: String, output: File): Boolean {
        val master = fetchText(manifestUrl)
        var mediaUrl = master.finalUrl
        var playlist = master.body
        if (playlist.contains("#EXT-X-STREAM-INF")) {
            mediaUrl = selectHighestQualityVariant(mediaUrl, playlist)
                ?: throw UnsupportedMediaException("No playable HLS variant was found")
            playlist = fetchText(mediaUrl).also { mediaUrl = it.finalUrl }.body
        }
        if (!playlist.contains("#EXTM3U")) throw UnsupportedMediaException("Invalid HLS playlist")
        val parsed = HlsPlaylistParser.parseMediaPlaylist(playlist)
        if (!parsed.isVod) {
            throw UnsupportedMediaException("Live streams are not supported in this MVP")
        }
        if (parsed.hasUnsupportedEncryption) {
            throw UnsupportedMediaException("This HLS encryption method is not supported")
        }
        if (parsed.usesByteRanges) {
            throw UnsupportedMediaException("Byte-range HLS is not supported in this MVP")
        }

        if (parsed.segments.isEmpty()) throw UnsupportedMediaException("HLS playlist has no segments")
        val keyCache = mutableMapOf<String, ByteArray>()

        BufferedOutputStream(FileOutputStream(output, false), BUFFER_SIZE).use { out ->
            parsed.segments.forEachIndexed { index, segment ->
                currentCoroutineContext().ensureActive()
                if (isStopped) return false
                val segmentUrl = resolveUrl(mediaUrl, segment.reference)
                val connection = openConnection(segmentUrl, requestAsMedia = true)
                try {
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("Segment ${index + 1} returned HTTP ${connection.responseCode}")
                    }
                    val rawInput = BufferedInputStream(connection.inputStream, BUFFER_SIZE)
                    val mediaInput = segment.encryption?.let { encryption ->
                        val keyUrl = resolveUrl(mediaUrl, encryption.keyReference.orEmpty())
                        val key = keyCache.getOrPut(keyUrl) { fetchAes128Key(keyUrl) }
                        val iv = HlsPlaylistParser.initializationVector(
                            encryption.ivHex,
                            segment.sequenceNumber,
                        ) ?: throw UnsupportedMediaException("Invalid HLS AES-128 IV")
                        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                        }
                        CipherInputStream(rawInput, cipher)
                    } ?: rawInput
                    mediaInput.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (isStopped) return false
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                val progress = ((index + 1) * 95) / parsed.segments.size
                updateProgress(
                    progress,
                    index + 1L,
                    parsed.segments.size.toLong(),
                    "HLS segment ${index + 1}/${parsed.segments.size}",
                )
            }
        }
        return output.exists() && output.length() > 0L
    }

    private fun selectHighestQualityVariant(baseUrl: String, playlist: String): String? {
        return HlsPlaylistParser.highestBandwidthVariant(playlist)?.let { resolveUrl(baseUrl, it) }
    }

    private fun fetchText(sourceUrl: String): TextResponse {
        val connection = openConnection(sourceUrl)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Playlist returned HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return TextResponse(connection.url.toString(), body)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchAes128Key(sourceUrl: String): ByteArray {
        val connection = openConnection(sourceUrl)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HLS key returned HTTP ${connection.responseCode}")
            }
            val key = connection.inputStream.use { input ->
                val buffer = ByteArray(17)
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read < 0) break
                    total += read
                }
                buffer.copyOf(total)
            }
            if (key.size != 16) throw UnsupportedMediaException("Invalid HLS AES-128 key")
            return key
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        sourceUrl: String,
        rangeStart: Long? = null,
        requestAsMedia: Boolean = false,
    ): HttpURLConnection {
        val connection = URL(sourceUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Encoding", "identity")
        userAgent?.takeIf(String::isNotBlank)?.let { connection.setRequestProperty("User-Agent", it) }
        cookie?.takeIf(String::isNotBlank)?.let { connection.setRequestProperty("Cookie", it) }
        referer?.takeIf(String::isNotBlank)?.let { connection.setRequestProperty("Referer", it) }
        if (rangeStart != null || requestAsMedia) {
            connection.setRequestProperty("Range", "bytes=${rangeStart ?: 0L}-")
        }
        return connection
    }

    private suspend fun updateProgress(percent: Int, downloaded: Long, total: Long, text: String) {
        setProgress(
            Data.Builder()
                .putInt(DownloadContract.KEY_PROGRESS, percent.coerceIn(0, 100))
                .putLong(DownloadContract.KEY_DOWNLOADED, downloaded)
                .putLong(DownloadContract.KEY_TOTAL, total)
                .build()
        )
        setForeground(createForegroundInfo(percent, text))
    }

    private fun createForegroundInfo(progress: Int, text: String): ForegroundInfo {
        val openIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, DownloadContract.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(requestedName.ifBlank { "Video download" })
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(progress in 0..99)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                DownloadContract.CHANNEL_ID,
                DownloadContract.NOTIFICATION_NAME,
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun failure(message: String): Result = Result.failure(
        Data.Builder().putString(DownloadContract.KEY_ERROR, message.take(500)).build()
    )

    private fun resolveUrl(base: String, relative: String): String = URI(base).resolve(relative).toString()

    private fun isAllowedUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        return host != "youtu.be" && !host.endsWith("youtube.com") && !host.endsWith("youtube-nocookie.com")
    }

    private data class TextResponse(val finalUrl: String, val body: String)
    private class UnsupportedMediaException(message: String) : Exception(message)

    private companion object {
        const val TAG = "BrightFetchDownload"
        const val BUFFER_SIZE = 64 * 1024
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
