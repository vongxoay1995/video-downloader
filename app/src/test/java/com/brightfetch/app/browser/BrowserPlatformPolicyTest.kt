package com.brightfetch.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPlatformPolicyTest {
    @Test
    fun `recognizes TikTok and Facebook deep links`() {
        assertEquals(
            ExternalBrowserApp.TIKTOK,
            BrowserPlatformPolicy.externalAppForScheme("snssdk1233"),
        )
        assertEquals(
            ExternalBrowserApp.FACEBOOK,
            BrowserPlatformPolicy.externalAppForScheme("fb-messenger"),
        )
        assertNull(BrowserPlatformPolicy.externalAppForScheme("javascript"))
    }

    @Test
    fun `matches only real platform web domains`() {
        assertEquals(
            ExternalBrowserApp.TIKTOK,
            BrowserPlatformPolicy.externalAppForWebUrl("https://www.tiktok.com/@creator/video/123"),
        )
        assertEquals(
            ExternalBrowserApp.FACEBOOK,
            BrowserPlatformPolicy.externalAppForWebUrl("https://m.facebook.com/watch/?v=123"),
        )
        assertNull(BrowserPlatformPolicy.externalAppForWebUrl("https://tiktok.com.evil.test/video/123"))
        assertNull(BrowserPlatformPolicy.externalAppForWebUrl("https://notfacebook.com/watch"))
    }

    @Test
    fun `ordinary social links stay in WebView but explicit app links may open externally`() {
        assertNull(
            BrowserPlatformPolicy.explicitWebAppLink("https://www.tiktok.com/@creator/video/123"),
        )
        assertEquals(
            ExternalBrowserApp.TIKTOK,
            BrowserPlatformPolicy.explicitWebAppLink("https://vm.tiktok.com/abc123/"),
        )
        assertEquals(
            ExternalBrowserApp.FACEBOOK,
            BrowserPlatformPolicy.explicitWebAppLink("https://fb.watch/abc123/"),
        )
        assertEquals(
            ExternalBrowserApp.TIKTOK,
            BrowserPlatformPolicy.explicitWebAppLink("https://www.tiktok.com/video?openapp=1"),
        )
        assertNull(
            BrowserPlatformPolicy.explicitWebAppLink("https://www.tiktok.com/video?not_openapp=1"),
        )
        assertNull(
            BrowserPlatformPolicy.explicitWebAppLink("https://www.tiktok.com/video?openapp=10"),
        )
    }

    @Test
    fun `YouTube pages are marked unsupported without false domain matches`() {
        assertEquals(
            "YouTube",
            BrowserPlatformPolicy.unsupportedDownloadFor("https://m.youtube.com/watch?v=abc")?.displayName,
        )
        assertEquals(
            "YouTube",
            BrowserPlatformPolicy.unsupportedDownloadFor("https://youtu.be/abc")?.displayName,
        )
        assertEquals(
            "YouTube",
            BrowserPlatformPolicy.unsupportedDownloadFor("https://www.youtube-nocookie.com/embed/abc")?.displayName,
        )
        assertNull(BrowserPlatformPolicy.unsupportedDownloadFor("https://youtube.com.evil.test/watch"))
    }

    @Test
    fun `fallback and app targets reject dangerous schemes and unrelated hosts`() {
        assertEquals(
            "https://www.tiktok.com/@creator/video/123",
            BrowserPlatformPolicy.safeHttpUrl("https://www.tiktok.com/@creator/video/123"),
        )
        assertNull(BrowserPlatformPolicy.safeHttpUrl("javascript:alert(1)"))
        assertNull(BrowserPlatformPolicy.safeHttpUrl("httpx://example.test"))
        assertTrue(
            BrowserPlatformPolicy.isSafeTargetFor(
                ExternalBrowserApp.TIKTOK,
                "snssdk1233://aweme/detail/123",
            ),
        )
        assertFalse(
            BrowserPlatformPolicy.isSafeTargetFor(
                ExternalBrowserApp.TIKTOK,
                "https://facebook.com/watch/123",
            ),
        )
    }
}
