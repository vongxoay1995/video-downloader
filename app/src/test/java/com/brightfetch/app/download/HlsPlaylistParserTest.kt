package com.brightfetch.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(parsed.isVod)
        assertFalse(parsed.isEncrypted)
        assertFalse(parsed.usesByteRanges)
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
