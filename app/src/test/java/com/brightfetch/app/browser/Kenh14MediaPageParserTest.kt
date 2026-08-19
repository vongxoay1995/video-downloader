package com.brightfetch.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Kenh14MediaPageParserTest {
    @Test
    fun extractsOriginalMp4AndArticleTitle() {
        val html = """
            <html><head>
              <title>3 triệu người xem video chưa từng công bố hé lộ câu nói cực gắt của trọng tài với Messi ở chung kết World Cup</title>
            </head><body>
              <div data-vid="kenh14cdn.com/203336854389633024/2026/8/16/tweeloadml1bh8ob-17868722333802087593946.mp4"></div>
            </body></html>
        """.trimIndent()

        val parsed = Kenh14MediaPageParser.extract(html)
        assertEquals(
            "https://kenh14cdn.com/203336854389633024/2026/8/16/tweeloadml1bh8ob-17868722333802087593946.mp4",
            parsed?.mediaUrl,
        )
        assertEquals(
            "3 triệu người xem video chưa từng công bố hé lộ câu nói cực gắt của trọng tài với Messi ở chung kết World Cup",
            parsed?.title,
        )
    }

    @Test
    fun limitsNativeResolutionToRealKenh14Hosts() {
        assertTrue(Kenh14PageResolver.supports("https://m.kenh14.vn/article.chn"))
        assertFalse(Kenh14PageResolver.supports("https://m.kenh14.vn.evil.test/article.chn"))
        assertNull(Kenh14MediaPageParser.extract("<html><title>No video</title></html>"))
    }
}
