package com.brightfetch.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistParserTest {
    @Test
    fun choosesHighestBandwidthVariant() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=700000
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000
            high/index.m3u8
        """.trimIndent()

        assertEquals("high/index.m3u8", HlsPlaylistParser.highestBandwidthVariant(playlist))
    }

    @Test
    fun parsesMasterVariantMetadataWithoutConfusingAverageAndPeakBandwidth() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=1800000,BANDWIDTH=2400000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2",NAME="HD, main"
            video/720/index.m3u8
            #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=600000,RESOLUTION=640X360,CODECS="avc1.42e01e,mp4a.40.2"
            video/360/index.m3u8
        """.trimIndent()

        val variants = HlsPlaylistParser.parseMasterPlaylist(playlist)

        assertEquals(2, variants.size)
        assertEquals(2_400_000L, variants[0].bandwidthBitsPerSecond)
        assertEquals(1_800_000L, variants[0].averageBandwidthBitsPerSecond)
        assertEquals(1280, variants[0].width)
        assertEquals(720, variants[0].height)
        assertEquals("avc1.4d401f,mp4a.40.2", variants[0].codecs)
        assertEquals("HD, main", variants[0].name)
        assertEquals("video/720/index.m3u8", variants[0].reference)
        assertNull(variants[1].bandwidthBitsPerSecond)
        assertEquals(600_000L, variants[1].averageBandwidthBitsPerSecond)
        assertEquals(360, variants[1].height)
    }

    @Test
    fun doesNotStealUriFromNextStreamInfWhenVariantUriIsMissing() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=426x240
            # a malformed variant with no URI
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480

            # comment before the real URI is valid
            ../480/media.m3u8
        """.trimIndent()

        val variants = HlsPlaylistParser.parseMasterPlaylist(playlist)

        assertEquals(1, variants.size)
        assertEquals(1_200_000L, variants.single().bandwidthBitsPerSecond)
        assertEquals(480, variants.single().height)
        assertEquals("../480/media.m3u8", variants.single().reference)
    }

    @Test
    fun keepsSameResolutionVariantsWithDifferentCodecsAndBitrates() {
        val variants = HlsPlaylistParser.parseMasterPlaylist(
            """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
                avc.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720,CODECS="hvc1.1.6.L93.B0,mp4a.40.2"
                hevc.m3u8
            """.trimIndent()
        )

        assertEquals(2, variants.size)
        assertEquals(listOf("avc.m3u8", "hevc.m3u8"), variants.map { it.reference })
    }

    @Test
    fun parsesVodSegmentsAndInitializationMap() {
        val playlist = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.0,
            segment-1.m4s
            #EXTINF:4.0,
            segment-2.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = HlsPlaylistParser.parseMediaPlaylist(playlist)
        assertEquals(listOf("init.mp4", "segment-1.m4s", "segment-2.m4s"), parsed.segmentReferences)
        assertEquals(2, parsed.mediaSegmentCount)
        assertEquals(8.0, parsed.totalDurationSeconds ?: -1.0, 0.0001)
        assertTrue(parsed.isVod)
        assertFalse(parsed.isEncrypted)
        assertFalse(parsed.usesByteRanges)
    }

    @Test
    fun sumsDecimalExtInfDurationsAndDoesNotCountMapAsMediaSegment() {
        val parsed = HlsPlaylistParser.parseMediaPlaylist(
            """
                #EXTM3U
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:3.337,
                one.m4s
                #EXTINF:4.125,optional title
                two.m4s
                #EXT-X-ENDLIST
            """.trimIndent()
        )

        assertEquals(2, parsed.mediaSegmentCount)
        assertEquals(7.462, parsed.totalDurationSeconds ?: -1.0, 0.0001)
        assertTrue(parsed.segments.first().isInitializationSegment)
        assertNull(parsed.segments.first().durationSeconds)
        assertEquals(3.337, parsed.segments[1].durationSeconds ?: -1.0, 0.0001)
    }

    @Test
    fun detectsByteRangeOnInitializationMap() {
        val parsed = HlsPlaylistParser.parseMediaPlaylist(
            """
                #EXTM3U
                #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
                #EXTINF:4,
                media.m4s
                #EXT-X-ENDLIST
            """.trimIndent()
        )

        assertTrue(parsed.usesByteRanges)
    }

    @Test
    fun flagsEncryptedAndByteRangePlaylists() {
        val parsed = HlsPlaylistParser.parseMediaPlaylist(
            """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                #EXT-X-BYTERANGE:1000@0
                media.ts
                #EXT-X-ENDLIST
            """.trimIndent()
        )

        assertTrue(parsed.isEncrypted)
        assertTrue(parsed.usesByteRanges)
        assertFalse(parsed.hasUnsupportedEncryption)
        assertEquals("key.bin", parsed.segments.single().encryption?.keyReference)
    }

    @Test
    fun parsesAes128IvAndMediaSequence() {
        val parsed = HlsPlaylistParser.parseMediaPlaylist(
            """
                #EXTM3U
                #EXT-X-MEDIA-SEQUENCE:42
                #EXT-X-KEY:METHOD=AES-128,URI="https://video.24h.com.vn/upload/drm.key",IV=0x082807ae9dd56c5c38ee4939ecd9deb7
                part-42.ts
                part-43.ts
                #EXT-X-ENDLIST
            """.trimIndent()
        )

        assertEquals(listOf(42L, 43L), parsed.segments.map { it.sequenceNumber })
        assertEquals(
            "082807ae9dd56c5c38ee4939ecd9deb7",
            HlsPlaylistParser.initializationVector(
                parsed.segments.first().encryption?.ivHex,
                parsed.segments.first().sequenceNumber,
            )?.joinToString("") { "%02x".format(it) },
        )
        assertEquals(
            "0000000000000000000000000000002a",
            HlsPlaylistParser.initializationVector(null, 42L)?.joinToString("") { "%02x".format(it) },
        )
    }
}
