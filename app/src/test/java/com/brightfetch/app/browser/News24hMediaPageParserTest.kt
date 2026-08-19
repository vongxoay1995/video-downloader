package com.brightfetch.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class News24hMediaPageParserTest {
    @Test
    fun extractsHlsContentUrlAndTitleFromArticleMetadata() {
        val html = """
            <html><head>
              <meta property="og:title" content="Video bóng đá Messi &amp; đồng đội">
              <script type="application/ld+json">
                {"contentUrl":"https:\/\/cdn.24h.com.vn\/upload\/video\/messi_720p.m3u8"}
              </script>
            </head></html>
        """.trimIndent()

        val parsed = News24hMediaPageParser.extract(html)
        assertEquals("https://cdn.24h.com.vn/upload/video/messi_720p.m3u8", parsed?.mediaUrl)
        assertEquals("Video bóng đá Messi & đồng đội", parsed?.title)
    }

    @Test
    fun fallsBackToVideoSourceAndRejectsNon24hPagesForNativeFetch() {
        val html = """<video><source src="https://cdn.24h.com.vn/video/highlight.mp4" type="video/mp4"></video>"""
        assertEquals("https://cdn.24h.com.vn/video/highlight.mp4", News24hMediaPageParser.extract(html)?.mediaUrl)
        assertTrue(News24hPageResolver.supports("https://www.24h.com.vn/bong-da/article.html"))
        assertFalse(News24hPageResolver.supports("https://24h.com.vn.evil.test/article.html"))
        assertNull(News24hMediaPageParser.extract("<html>No video</html>"))
    }
}
