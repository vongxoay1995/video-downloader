package com.brightfetch.app.download

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Repackages a downloaded MPEG-TS HLS stream as a standards-compliant MP4. */
@OptIn(UnstableApi::class)
internal object HlsMp4Remuxer {
    suspend fun remux(context: Context, input: File, output: File) {
        require(input.isFile && input.length() > 0L) { "HLS source is empty" }
        val sourceTracks = runCatching { inspectTracks(input) }.getOrElse { error ->
            throw HlsRemuxException(
                error.message?.let { "Cannot read HLS tracks: $it" } ?: "Cannot read HLS tracks",
            )
        }
        if (!sourceTracks.hasVideo) throw HlsRemuxException("HLS stream has no video track")

        if (output.exists() && !output.delete()) {
            throw HlsRemuxException("Cannot replace the temporary MP4")
        }

        try {
            exportOnMainLooper(context.applicationContext, input, output)
            val outputTracks = inspectTracks(output)
            if (!output.isFile || output.length() <= 0L || !outputTracks.hasVideo) {
                throw HlsRemuxException("Packaged MP4 has no playable video track")
            }
            if (sourceTracks.hasAudio && !outputTracks.hasAudio) {
                throw HlsRemuxException("Packaged MP4 lost its audio track")
            }
            if (outputTracks.durationUs <= 0L) {
                throw HlsRemuxException("Packaged MP4 has an invalid duration")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            output.delete()
            throw cancelled
        } catch (error: HlsRemuxException) {
            output.delete()
            throw error
        } catch (error: Exception) {
            output.delete()
            throw HlsRemuxException(
                error.message?.takeIf(String::isNotBlank)?.let { "Cannot package HLS as MP4: $it" }
                    ?: "Cannot package HLS as MP4",
            )
        }
    }

    private suspend fun exportOnMainLooper(
        context: Context,
        input: File,
        output: File,
    ): ExportResult = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            lateinit var transformer: Transformer
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(exportResult)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }
            transformer = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .addListener(listener)
                .build()

            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post {
                    transformer.cancel()
                    output.delete()
                }
            }

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(input))
                // The worker intentionally stores the source as `.ts.part`; provide the real
                // container type so Media3 uses its MPEG-TS extractor instead of guessing.
                .setMimeType(MimeTypes.VIDEO_MP2T)
                .build()
            try {
                transformer.start(mediaItem, output.absolutePath)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    private fun inspectTracks(file: File): TrackSummary {
        if (!file.isFile || file.length() <= 0L) return TrackSummary()
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var hasVideo = false
            var hasAudio = false
            var durationUs = 0L
            repeat(extractor.trackCount) { index ->
                val format = extractor.getTrackFormat(index)
                val mimeType = runCatching { format.getString(MediaFormat.KEY_MIME) }.getOrNull()
                hasVideo = hasVideo || mimeType?.startsWith("video/") == true
                hasAudio = hasAudio || mimeType?.startsWith("audio/") == true
                durationUs = maxOf(
                    durationUs,
                    runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L),
                )
            }
            TrackSummary(hasVideo = hasVideo, hasAudio = hasAudio, durationUs = durationUs)
        } finally {
            extractor.release()
        }
    }

    private data class TrackSummary(
        val hasVideo: Boolean = false,
        val hasAudio: Boolean = false,
        val durationUs: Long = 0L,
    )

    class HlsRemuxException(message: String) : Exception(message)
}
