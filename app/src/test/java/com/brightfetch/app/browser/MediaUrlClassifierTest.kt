package com.brightfetch.app.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlClassifierTest {
    @Test
    fun recognizesCommonDirectAndHlsUrls() {
        assertTrue(MediaUrlClassifier.isLikelyNetworkMedia("https://media.example.test/movie.mp4?token=abc"))
        assertTrue(MediaUrlClassifier.isLikelyNetworkMedia("https://media.example.test/master.M3U8?token=abc"))
    }

    @Test
    fun recognizesSignedTikTokStyleCdnUrlWithoutExtension() {
        val url = "https://v16-webapp-prime.us.tiktok.com/video/tos/useast2a/object-id/?mime_type=video_mp4&token=abc"
        assertTrue(MediaUrlClassifier.isLikelyNetworkMedia(url))
        assertTrue(MediaUrlClassifier.isUsableVideoElementUrl(url))
    }

    @Test
    fun doesNotMistakeTikTokPageOrSegmentsForDownloadableVideo() {
        assertFalse(MediaUrlClassifier.isLikelyNetworkMedia("https://www.tiktok.com/@creator/video/123456"))
        assertFalse(MediaUrlClassifier.isLikelyNetworkMedia("https://cdn.example.test/chunk-42.m4s"))
        assertFalse(MediaUrlClassifier.isLikelyNetworkMedia("blob:https://example.test/123"))
    }

    @Test
    fun rejectsTikTokMusicAndAudioUrls() {
        assertFalse(
            MediaUrlClassifier.isLikelyNetworkMedia(
                "https://sf9-ies-music-sg.tiktokcdn.com/obj/music/song.mp4?mime_type=audio_mp4"
            )
        )
        assertFalse(
            MediaUrlClassifier.isUsableVideoElementUrl(
                "https://music.tiktokcdn.com/song?id=1&mime_type=audio_mp4"
            )
        )
        assertTrue(
            MediaUrlClassifier.isLikelyNetworkMedia(
                "https://v16-webapp-prime.tiktok.com/video/tos/object/?mime_type=video_mp4"
            )
        )
    }
}
