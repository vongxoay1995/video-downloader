package com.brightfetch.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFileNamesTest {
    @Test
    fun replacesNonVideoExtensionUsingMimeType() {
        assertEquals("clip.mp4", VideoFileNames.normalize("clip.bin", "video/mp4"))
        assertEquals("download.webm", VideoFileNames.normalize("download.dat", "video/webm"))
    }

    @Test
    fun preservesSupportedVideoExtension() {
        assertEquals("movie.mkv", VideoFileNames.normalize("movie.mkv", "video/mp4"))
        assertTrue(VideoFileNames.hasSupportedExtension("movie.mkv"))
        assertFalse(VideoFileNames.hasSupportedExtension("movie.json"))
    }

    @Test
    fun remuxesHlsOutputToMp4Extension() {
        assertEquals("master.mp4", VideoFileNames.normalize("master.m3u8", isHls = true))
    }

    @Test
    fun preservesAReadableVietnameseArticleTitle() {
        val title = "3 triệu người xem video chưa từng công bố hé lộ câu nói cực gắt của trọng tài với Messi ở chung kết World Cup"
        assertEquals("$title.mp4", VideoFileNames.normalize(title, mimeType = "video/mp4"))
    }
}
