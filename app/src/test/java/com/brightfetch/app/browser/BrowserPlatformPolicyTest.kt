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
            ExternalBrowserApp.TIKTOK,
            BrowserPlatformPolicy.externalAppForScheme("snssdkonly1180"),
        )
        assertEquals(
            ExternalBrowserApp.TIKTOK,
            BrowserPlatformPolicy.externalAppForPackage("com.ss.android.ugc.tiktok.lite"),
        )
        assertEquals(
            ExternalBrowserApp.TIKTOK,
            BrowserPlatformPolicy.externalAppForPackage("com.tiktok.lite.go"),
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
    fun `TikTok OneLink resolves its encoded native deep link`() {
        val request = BrowserPlatformPolicy.externalAppLinkRequest(
            "https://snssdk1180.onelink.me/BAuo" +
                "?pid=tiktokweb&af_dp=snssdk1180%3A%2F%2Faweme%2Fdetail%2F7687603190069464340",
        )

        assertEquals(ExternalBrowserApp.TIKTOK, request?.app)
        assertEquals(
            "snssdk1180://aweme/detail/7687603190069464340",
            request?.targetUrl,
        )
        assertEquals(
            "snssdk1180://aweme/detail/7687603190069464340" +
                "?refer=web&params_url=https%3A%2F%2Fwww.tiktok.com%2F%40creator%2Fvideo%2F123",
            BrowserPlatformPolicy.externalAppLinkRequest(
                "https://snssdk1180.onelink.me/BAuo" +
                    "?pid=tiktok_share" +
                    "&af_dp=snssdk1180%3A%2F%2Faweme%2Fdetail%2F7687603190069464340" +
                    "%3Frefer%3Dweb%26params_url%3Dhttps%253A%252F%252Fwww.tiktok.com" +
                    "%252F%2540creator%252Fvideo%252F123",
            )?.targetUrl,
        )
        assertNull(
            BrowserPlatformPolicy.externalAppLinkRequest(
                "https://snssdk1180.onelink.me.evil.test/BAuo" +
                    "?af_dp=snssdk1180%3A%2F%2Faweme%2Fdetail%2F123",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppLinkRequest(
                "http://snssdk1180.onelink.me/BAuo" +
                    "?af_dp=snssdk1180%3A%2F%2Faweme%2Fdetail%2F123",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppLinkRequest(
                "https://user@snssdk1180.onelink.me/BAuo" +
                    "?af_dp=snssdk1180%3A%2F%2Faweme%2Fdetail%2F123",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppLinkRequest(
                "https://snssdk1180.onelink.me/BAuo" +
                    "?af_dp=fb%3A%2F%2Fprofile%2F123",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppLinkRequest(
                "https://snssdk1180.onelink.me/BAuo?af_dp=snssdk1180%ZZ",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppLinkRequest(
                "https://snssdk1180.onelink.me/BAuo" +
                    "?af_dp=snssdk1180%3A%2F%2Faweme%2Fdetail%2F1" +
                    "&af_dp=snssdk1180%3A%2F%2Faweme%2Fdetail%2F2",
            ),
        )
    }

    @Test
    fun `recognizes only exact allowlisted Play Store install URLs`() {
        val playRequest = BrowserPlatformPolicy.storeInstallRequest(
            "https://play.google.com/store/apps/details" +
                "?id=com.ss.android.ugc.trill&hl=vi&gl=VN",
        )
        assertEquals(ExternalBrowserApp.TIKTOK, playRequest?.app)
        assertEquals("com.ss.android.ugc.trill", playRequest?.requestedPackage)
        assertEquals(
            ExternalBrowserApp.TIKTOK,
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "market://details?id=com.zhiliaoapp.musically&referrer=tiktok_web",
            ),
        )
        assertEquals(
            ExternalBrowserApp.FACEBOOK,
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "https://play.google.com/store/apps/details?id=com.facebook.katana",
            ),
        )

        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "https://play.google.com.evil.test/store/apps/details?id=com.ss.android.ugc.trill",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "http://play.google.com/store/apps/details?id=com.ss.android.ugc.trill",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "https://user@play.google.com/store/apps/details?id=com.ss.android.ugc.trill",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "https://play.google.com:444/store/apps/details?id=com.ss.android.ugc.trill",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "https://play.google.com/store/apps/details/extra?id=com.ss.android.ugc.trill",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "market://details?id=com.ss.android.ugc.trill.evil",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "market://search?q=pname:com.ss.android.ugc.trill",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "market://details?not_id=com.ss.android.ugc.trill",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "market://details#id=com.ss.android.ugc.trill",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "market://details?id=com.ss.android.ugc.trill&id=com.zhiliaoapp.musically",
            ),
        )
        assertNull(
            BrowserPlatformPolicy.externalAppForStoreUrl(
                "market://details?id=%ZZ&id=com.ss.android.ugc.trill",
            ),
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
        assertFalse(
            BrowserPlatformPolicy.isSafeTargetFor(
                ExternalBrowserApp.TIKTOK,
                "https://play.google.com/store/apps/details?id=com.ss.android.ugc.trill",
            ),
        )
    }
}
