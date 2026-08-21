package com.brightfetch.app.ui

import com.brightfetch.app.browser.BrowserSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserStateTest {
    @Test
    fun `new tab becomes active and close keeps at least one tab`() {
        val state = BrowserState()
        val firstTabId = state.currentTab.id

        state.newTab()

        assertEquals(2, state.tabCount)
        assertTrue(state.currentTab.id != firstTabId)

        state.closeTab(state.currentTab.id)
        assertEquals(1, state.tabCount)
        assertEquals(firstTabId, state.currentTab.id)

        state.closeTab(firstTabId)
        assertEquals(1, state.tabCount)
        assertTrue(state.showLanding)
    }

    @Test
    fun `select tab restores its independent address`() {
        val state = BrowserState()
        val firstTabId = state.currentTab.id
        state.navigate("first.example", BrowserSearchEngine.GOOGLE)
        state.newTab()
        state.navigate("second.example", BrowserSearchEngine.GOOGLE)
        val secondTabId = state.currentTab.id

        state.selectTab(firstTabId)
        assertEquals("https://first.example", state.address)

        state.selectTab(secondTabId)
        assertEquals("https://second.example", state.address)
    }
}
