package com.brightfetch.app.browser

import com.brightfetch.app.model.MediaCandidate
import com.brightfetch.app.model.MediaDownloadSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFormatResolverTest {
    @Test
    fun directProbeUsesFinalUrlExactRangeTotalAndCarriesRequestContext() = runBlocking {
        val transport = FakeTransport().apply {
            probes["https://origin.test/video"] = MediaFormatProbeResponse(
                finalUrl = "https://cdn.test/final/video.mp4?token=signed",
                mimeType = "video/mp4; charset=binary",
                contentLengthBytes = 12_345_678L,
            )
        }
        val candidate = MediaCandidate(
            url = "https://origin.test/video",
            title = "Public clip",
            pageUrl = "https://origin.test/article",
            userAgent = "BrightFetch test UA",
            cookie = "session=abc",
            height = 720,
        )

        val option = MediaFormatResolver(transport).resolve(candidate).single()

        assertEquals("https://cdn.test/final/video.mp4?token=signed", option.downloadUrl)
        assertEquals(MediaDownloadSource.DIRECT, option.source)
        assertEquals("video/mp4", option.mimeType)
        assertEquals("mp4", option.outputExtension)
        assertEquals(12_345_678L, option.estimatedSizeBytes)
        assertTrue(option.sizeIsExact)
        assertTrue(option.isAvailable)
        assertEquals(1, transport.probeRequests.size)
        with(transport.probeRequests.single()) {
            assertEquals("BrightFetch test UA", userAgent)
            assertEquals("session=abc", cookie)
            assertEquals("https://origin.test/article", referer)
        }
    }

    @Test
    fun masterVariantsResolveAgainstRedirectAndEstimateUsingAverageBandwidth() = runBlocking {
        val masterRequest = "https://origin.test/path/master.m3u8"
        val highRequest = "https://cdn.test/live/high/media.m3u8"
        val lowRequest = "https://cdn.test/live/low/media.m3u8"
        val transport = FakeTransport().apply {
            texts[masterRequest] = MediaFormatTextResponse(
                finalUrl = "https://cdn.test/live/master.m3u8",
                body = """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=2400000,AVERAGE-BANDWIDTH=1600000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
                    high/media.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=800000,AVERAGE-BANDWIDTH=600000,RESOLUTION=640x360,CODECS="avc1.42e01e,mp4a.40.2"
                    low/media.m3u8
                """.trimIndent(),
            )
            texts[highRequest] = vodResponse(highRequest, segmentDurations = listOf(4.0, 6.0), finalUrl = "https://edge.test/video/high.m3u8")
            texts[lowRequest] = vodResponse(lowRequest, segmentDurations = listOf(4.0, 6.0), finalUrl = "https://edge.test/video/low.m3u8")
        }
        val candidate = MediaCandidate(
            url = masterRequest,
            title = "Match",
            pageUrl = "https://origin.test/article",
            mimeType = MediaFormatResolver.HLS_MIME_TYPE,
            userAgent = "UA",
            cookie = "auth=1",
        )

        val options = MediaFormatResolver(transport).resolve(candidate)

        assertEquals(2, options.size)
        assertEquals(listOf(720, 360), options.map { it.height })
        assertEquals("https://edge.test/video/high.m3u8", options[0].downloadUrl)
        assertEquals("720p • 1.6 Mbps", options[0].label)
        assertEquals(2, options[0].segmentCount)
        assertEquals(2_000_000L, options[0].estimatedSizeBytes)
        assertFalse(options[0].sizeIsExact)
        assertEquals("avc1.4d401f,mp4a.40.2", options[0].codecs)
        assertEquals(MediaFormatResolver.HLS_MIME_TYPE, options[0].mimeType)
        val selected = options[1].toCandidate(candidate)
        assertEquals("https://edge.test/video/low.m3u8", selected.url)
        assertEquals(MediaFormatResolver.HLS_MIME_TYPE, selected.mimeType)
        assertTrue(selected.isHls)
        assertEquals(360, selected.height)
        assertTrue(transport.textRequests.all {
            it.userAgent == "UA" && it.cookie == "auth=1" && it.referer == candidate.pageUrl
        })
    }

    @Test
    fun flattensNestedRedirectedMasterToFinalMediaPlaylist() = runBlocking {
        val root = "https://origin.test/root/master.m3u8"
        val nestedRequest = "https://redirected.test/nested/master.m3u8"
        val leafRequest = "https://edge.test/branch/leaf/video.m3u8"
        val transport = FakeTransport().apply {
            texts[root] = MediaFormatTextResponse(
                finalUrl = "https://redirected.test/base/master.m3u8",
                body = """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360
                    ../nested/master.m3u8
                """.trimIndent(),
            )
            texts[nestedRequest] = MediaFormatTextResponse(
                finalUrl = "https://edge.test/branch/master.m3u8",
                body = """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=850000,CODECS="avc1.42e01e,mp4a.40.2"
                    leaf/video.m3u8
                """.trimIndent(),
            )
            texts[leafRequest] = vodResponse(
                requestedUrl = leafRequest,
                segmentDurations = listOf(5.0),
                finalUrl = "https://media.test/signed/final-playlist?token=abc",
            )
        }

        val option = MediaFormatResolver(transport).resolve(
            MediaCandidate(root, "Nested", mimeType = MediaFormatResolver.HLS_MIME_TYPE)
        ).single()

        assertEquals("https://media.test/signed/final-playlist?token=abc", option.downloadUrl)
        assertEquals(360, option.height)
        assertEquals(850_000L, option.bandwidthBitsPerSecond)
        assertTrue(option.isAvailable)
    }

    @Test
    fun marksUnsupportedHlsModesUnavailable() = runBlocking {
        val root = "https://video.test/master.m3u8"
        val transport = FakeTransport().apply {
            texts[root] = MediaFormatTextResponse(
                root,
                """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,AUDIO="audio-main"
                    audio-group.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=854x480
                    live.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360
                    byterange.m3u8
                """.trimIndent(),
            )
            texts["https://video.test/audio-group.m3u8"] = vodResponse("https://video.test/audio-group.m3u8", listOf(4.0))
            texts["https://video.test/live.m3u8"] = MediaFormatTextResponse(
                "https://video.test/live.m3u8",
                "#EXTM3U\n#EXTINF:4,\nlive.ts",
            )
            texts["https://video.test/byterange.m3u8"] = MediaFormatTextResponse(
                "https://video.test/byterange.m3u8",
                "#EXTM3U\n#EXT-X-BYTERANGE:1000@0\n#EXTINF:4,\npart.ts\n#EXT-X-ENDLIST",
            )
        }

        val options = MediaFormatResolver(transport).resolve(
            MediaCandidate(root, "Unsupported", mimeType = MediaFormatResolver.HLS_MIME_TYPE)
        )

        assertEquals(3, options.size)
        assertTrue(options.all { !it.isAvailable })
        assertTrue(options.any { it.unavailableReason?.contains("audio", ignoreCase = true) == true })
        assertTrue(options.any { it.unavailableReason?.contains("Live", ignoreCase = true) == true })
        assertTrue(options.any { it.unavailableReason?.contains("Byte-range", ignoreCase = true) == true })
    }

    @Test
    fun detectsNestedPlaylistCycle() = runBlocking {
        val first = "https://cycle.test/a.m3u8"
        val second = "https://cycle.test/b.m3u8"
        val transport = FakeTransport().apply {
            texts[first] = MediaFormatTextResponse(first, "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nb.m3u8")
            texts[second] = MediaFormatTextResponse(second, "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\na.m3u8")
        }

        val option = MediaFormatResolver(transport).resolve(
            MediaCandidate(first, "Cycle", mimeType = MediaFormatResolver.HLS_MIME_TYPE)
        ).single()

        assertFalse(option.isAvailable)
        assertTrue(option.unavailableReason?.contains("cycle", ignoreCase = true) == true)
        assertEquals(3, transport.textRequests.size)
    }

    @Test
    fun hlsMimeWithoutFileExtensionStillUsesPlaylistResolver() = runBlocking {
        val url = "https://media.test/signed/play?token=abc"
        val transport = FakeTransport().apply {
            texts[url] = vodResponse(url, listOf(2.5, 2.5))
        }

        val option = MediaFormatResolver(transport).resolve(
            MediaCandidate(url, "Signed", mimeType = "application/x-mpegURL")
        ).single()

        assertEquals(MediaDownloadSource.HLS, option.source)
        assertEquals(2, option.segmentCount)
        assertEquals(0, transport.probeRequests.size)
    }

    @Test
    fun cancellationIsPropagatedInsteadOfBecomingAnUnavailableOption() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val transport = object : MediaFormatTransport {
            override suspend fun fetchText(
                request: MediaFormatHttpRequest,
                maximumBytes: Int,
            ): MediaFormatTextResponse {
                started.complete(Unit)
                awaitCancellation()
            }

            override suspend fun probe(request: MediaFormatHttpRequest): MediaFormatProbeResponse =
                error("Not expected")
        }
        var completedNormally = false
        val job = launch {
            MediaFormatResolver(transport).resolve(
                MediaCandidate("https://video.test/media.m3u8", "Cancel", mimeType = MediaFormatResolver.HLS_MIME_TYPE)
            )
            completedNormally = true
        }

        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertFalse(completedNormally)
    }

    @Test
    fun estimatorPrefersAverageBandwidthAndRejectsUnknownOrOverflow() {
        assertEquals(2_000_000L, estimateHlsSizeBytes(10.0, 1_600_000L, 2_400_000L))
        assertEquals(3_000_000L, estimateHlsSizeBytes(10.0, null, 2_400_000L))
        assertNull(estimateHlsSizeBytes(null, 1_000_000L, null))
        assertNull(estimateHlsSizeBytes(10.0, null, null))
        assertNull(estimateHlsSizeBytes(Double.MAX_VALUE, Long.MAX_VALUE, null))
    }

    private class FakeTransport : MediaFormatTransport {
        val texts = mutableMapOf<String, MediaFormatTextResponse>()
        val probes = mutableMapOf<String, MediaFormatProbeResponse>()
        val textRequests = mutableListOf<MediaFormatHttpRequest>()
        val probeRequests = mutableListOf<MediaFormatHttpRequest>()

        override suspend fun fetchText(
            request: MediaFormatHttpRequest,
            maximumBytes: Int,
        ): MediaFormatTextResponse {
            textRequests += request
            return texts[request.url] ?: error("Unexpected text request: ${request.url}")
        }

        override suspend fun probe(request: MediaFormatHttpRequest): MediaFormatProbeResponse {
            probeRequests += request
            return probes[request.url] ?: error("Unexpected probe request: ${request.url}")
        }
    }

    private fun vodResponse(
        requestedUrl: String,
        segmentDurations: List<Double>,
        finalUrl: String = requestedUrl,
    ): MediaFormatTextResponse = MediaFormatTextResponse(
        finalUrl = finalUrl,
        body = buildString {
            appendLine("#EXTM3U")
            segmentDurations.forEachIndexed { index, duration ->
                appendLine("#EXTINF:$duration,")
                appendLine("segment-$index.ts")
            }
            append("#EXT-X-ENDLIST")
        },
    )
}
