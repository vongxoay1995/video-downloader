package com.brightfetch.app.ui

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoLibraryFormattingTest {
    @Test
    fun `formats file sizes with up to two decimals`() {
        assertEquals("0 B", VideoLibraryFormatting.bytes(-20L))
        assertEquals("999 B", VideoLibraryFormatting.bytes(999L))
        assertEquals("1 KB", VideoLibraryFormatting.bytes(1_024L))
        assertEquals("72.15 MB", VideoLibraryFormatting.bytes(75_655_577L))
    }

    @Test
    fun `uses saved extension and falls back to mime type`() {
        assertEquals("MP4", VideoLibraryFormatting.extension("clip.Mp4", "video/webm"))
        assertEquals("WEBM", VideoLibraryFormatting.extension("clip", "video/webm"))
        assertEquals("MP4", VideoLibraryFormatting.extension("clip", "application/octet-stream"))
    }

    @Test
    fun `quality uses short edge for landscape and portrait video`() {
        assertEquals("720p", VideoLibraryFormatting.quality(1_280, 720))
        assertEquals("720p", VideoLibraryFormatting.quality(720, 1_280))
        assertEquals("Unknown quality", VideoLibraryFormatting.quality(0, 1_080))
        assertEquals("1920×1080", VideoLibraryFormatting.resolution(1_920, 1_080))
        assertEquals("Unknown resolution", VideoLibraryFormatting.resolution(0, 0))
    }

    @Test
    fun `formats duration for short and long videos`() {
        assertNull(VideoLibraryFormatting.duration(0L))
        assertEquals("03:12", VideoLibraryFormatting.duration(192_900L))
        assertEquals("1:02:03", VideoLibraryFormatting.duration(3_723_000L))
    }

    @Test
    fun `formats download date as fixed day month year`() {
        val instant = LocalDate.of(2026, 9, 8).atStartOfDay().toInstant(ZoneOffset.UTC)
        assertEquals(
            "08/09/2026",
            VideoLibraryFormatting.downloadedDate(instant.toEpochMilli(), ZoneOffset.UTC),
        )
        assertEquals("--/--/----", VideoLibraryFormatting.downloadedDate(0L, ZoneOffset.UTC))
    }
}
