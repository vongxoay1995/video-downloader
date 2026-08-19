package com.brightfetch.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer

/** Repackages a downloaded HLS transport stream as MP4 without re-encoding. */
internal object HlsMp4Remuxer {
    private val supportedTrackMimes = setOf(
        MediaFormat.MIMETYPE_VIDEO_AVC,
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        MediaFormat.MIMETYPE_AUDIO_AAC,
    )

    suspend fun remux(input: File, output: File) {
        require(input.isFile && input.length() > 0L) { "HLS source is empty" }
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var completed = false
        try {
            extractor.setDataSource(input.absolutePath)
            val sourceTracks = (0 until extractor.trackCount).map { index ->
                index to extractor.getTrackFormat(index)
            }
            val sourceVideoTracks = sourceTracks.filter { (_, format) ->
                format.mimeType()?.startsWith("video/") == true
            }
            val sourceAudioTracks = sourceTracks.filter { (_, format) ->
                format.mimeType()?.startsWith("audio/") == true
            }
            if (sourceVideoTracks.isEmpty()) throw HlsRemuxException("HLS stream has no video track")

            val selectedTracks = sourceTracks.filter { (_, format) ->
                format.mimeType() in supportedTrackMimes
            }
            if (selectedTracks.none { (_, format) -> format.mimeType()?.startsWith("video/") == true }) {
                throw HlsRemuxException("HLS video codec cannot be packaged as MP4")
            }
            if (sourceAudioTracks.isNotEmpty() &&
                selectedTracks.none { (_, format) -> format.mimeType()?.startsWith("audio/") == true }
            ) {
                throw HlsRemuxException("HLS audio codec cannot be packaged as MP4")
            }

            if (output.exists()) output.delete()
            val activeMuxer = MediaMuxer(
                output.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            muxer = activeMuxer
            val trackMap = selectedTracks.associate { (sourceTrack, format) ->
                extractor.selectTrack(sourceTrack)
                sourceTrack to activeMuxer.addTrack(format)
            }
            activeMuxer.start()
            muxerStarted = true

            var buffer = ByteBuffer.allocateDirect(DEFAULT_SAMPLE_BUFFER_BYTES)
            val info = MediaCodec.BufferInfo()
            val lastPresentationTimes = LongArray(extractor.trackCount) { Long.MIN_VALUE }
            while (true) {
                currentCoroutineContext().ensureActive()
                val sourceTrack = extractor.sampleTrackIndex
                if (sourceTrack < 0) break

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val requiredSize = extractor.sampleSize
                    if (requiredSize > Int.MAX_VALUE) throw HlsRemuxException("HLS sample is too large")
                    if (requiredSize > buffer.capacity()) {
                        buffer = ByteBuffer.allocateDirect(requiredSize.toInt())
                    }
                }

                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (size > buffer.capacity()) throw HlsRemuxException("HLS sample exceeds remux buffer")
                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs < 0L || presentationTimeUs < lastPresentationTimes[sourceTrack]) {
                    throw HlsRemuxException("HLS timestamps are discontinuous")
                }
                lastPresentationTimes[sourceTrack] = presentationTimeUs
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                info.set(0, size, presentationTimeUs, flags)
                buffer.position(0)
                buffer.limit(size)
                activeMuxer.writeSampleData(trackMap.getValue(sourceTrack), buffer, info)
                extractor.advance()
            }

            activeMuxer.stop()
            muxerStarted = false
            completed = output.isFile && output.length() > 0L
            if (!completed) throw HlsRemuxException("MP4 output is empty")
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            if (!completed) output.delete()
        }
    }

    private fun MediaFormat.mimeType(): String? = runCatching {
        getString(MediaFormat.KEY_MIME)
    }.getOrNull()

    class HlsRemuxException(message: String) : Exception(message)

    private const val DEFAULT_SAMPLE_BUFFER_BYTES = 8 * 1024 * 1024
}
