package com.brightfetch.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPageIdentityTest {
    @Test
    fun canonicalizesMobileAndDesktopNewsHosts() {
        assertEquals(
            MediaPageIdentity.key("https://m.kenh14.vn/article.chn?utm_source=test"),
            MediaPageIdentity.key("https://kenh14.vn/article.chn"),
        )
        assertEquals(
            MediaPageIdentity.key("https://www.24h.com.vn/bong-da/article.html"),
            MediaPageIdentity.key("https://24h.com.vn/bong-da/article.html?ref=home"),
        )
    }
}
