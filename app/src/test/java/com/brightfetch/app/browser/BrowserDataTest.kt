package com.brightfetch.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserDataTest {
    @Test
    fun `resolve preserves a URL embedded in shared text`() {
        assertEquals(
            "https://example.com/watch?id=7",
            BrowserNavigation.resolve(
                "Watch this: https://example.com/watch?id=7, thanks",
                BrowserSearchEngine.GOOGLE,
            ),
        )
    }

    @Test
    fun `resolve adds https to host-like input`() {
        assertEquals(
            "https://example.com/video",
            BrowserNavigation.resolve("example.com/video", BrowserSearchEngine.GOOGLE),
        )
    }

    @Test
    fun `resolve uses selected engine and encodes query`() {
        assertEquals(
            "https://duckduckgo.com/?q=video+th%E1%BB%AD+nghi%E1%BB%87m",
            BrowserNavigation.resolve("video thử nghiệm", BrowserSearchEngine.DUCKDUCKGO),
        )
    }

    @Test
    fun `resolve rejects blank input`() {
        assertNull(BrowserNavigation.resolve("   ", BrowserSearchEngine.BING))
    }

    @Test
    fun `history moves matching URL to front without duplicates`() {
        val original = listOf(
            BrowserHistoryEntry("https://one.test", "Old", 1L),
            BrowserHistoryEntry("https://two.test", "Two", 2L),
        )
        val updated = BrowserHistoryRules.upsert(
            original,
            BrowserHistoryEntry("https://one.test", "New", 3L),
        )

        assertEquals(2, updated.size)
        assertEquals("New", updated.first().title)
        assertEquals(3L, updated.first().visitedAt)
        assertEquals("https://two.test", updated.last().url)
    }

    @Test
    fun `history keeps at most two hundred entries`() {
        val original = (0..BrowserHistoryRules.MAX_ENTRIES).map { index ->
            BrowserHistoryEntry("https://$index.test", "$index", index.toLong())
        }
        val updated = BrowserHistoryRules.upsert(
            original,
            BrowserHistoryEntry("https://new.test", "New", 999L),
        )

        assertEquals(BrowserHistoryRules.MAX_ENTRIES, updated.size)
        assertEquals("https://new.test", updated.first().url)
    }
}
