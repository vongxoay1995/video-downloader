package com.brightfetch.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokMediaPageParserTest {
    @Test
    fun extractsAndDecodesSignedPlayAddressFromApiData() {
        val html = """
            <script id="api-data" type="application/json">
            {"videoDetail":{"itemInfo":{"itemStruct":{"video":{
              "height":1024,"width":576,"duration":60,
              "playAddr":"https:\u002F\u002Fv16-webapp-prime.tiktok.com\u002Fvideo\u002Ftos\u002Falisg\u002Fobject-id\u002F?a=1988&mime_type=video_mp4&signature=abc"
            }}}}}
            </script>
        """.trimIndent()

        assertEquals(
            "https://v16-webapp-prime.tiktok.com/video/tos/alisg/object-id/?a=1988&mime_type=video_mp4&signature=abc",
            TikTokMediaPageParser.extractPreferredMediaUrl(html),
        )
        val parsed = TikTokMediaPageParser.extractPreferredMedia(html)
        assertEquals(576, parsed?.width)
        assertEquals(1024, parsed?.height)
        assertEquals(60L, parsed?.durationSeconds)
    }

    @Test
    fun prefersPlaybackAddressOverDownloadAddress() {
        val html = """
            {"downloadAddr":"https://v16.tiktokcdn.com/video/tos/watermarked/?mime_type=video_mp4",
             "playAddr":"https://v16.tiktokcdn.com/video/tos/playback/?mime_type=video_mp4"}
        """.trimIndent()

        assertEquals(
            "https://v16.tiktokcdn.com/video/tos/playback/?mime_type=video_mp4",
            TikTokMediaPageParser.extractPreferredMediaUrl(html),
        )
    }

    @Test
    fun ignoresNonMediaJsonValuesAndRecognizesTikTokHosts() {
        assertNull(TikTokMediaPageParser.extractPreferredMediaUrl("{\"playAddr\":\"https://www.tiktok.com/@a/video/1\"}"))
        assertTrue(TikTokPageResolver.supports("https://www.tiktok.com/@a/video/1"))
        assertTrue(TikTokPageResolver.supports("https://vt.tiktok.com/abc/"))
        assertFalse(TikTokPageResolver.supports("https://example.com/video/1"))
    }

    @Test
    fun identifiesOnlySingleVideoPagesForCandidateCollapsing() {
        assertEquals(
            "7672405231522696456",
            TikTokPageResolver.videoPageId("https://www.tiktok.com/@creator/video/7672405231522696456?region=VN"),
        )
        assertEquals(
            "7672405231522696456",
            TikTokPageResolver.videoPageId("https://m.tiktok.com/v/7672405231522696456.html?share_item_id=7672405231522696456"),
        )
        assertNull(TikTokPageResolver.videoPageId("https://www.tiktok.com/@creator"))
    }
}
