package com.example

import com.example.ui.ThikanaTab
import com.example.voice.GeminiVoiceService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNavigationTest {

    private val service = GeminiVoiceService()

    @Test
    fun `test feed navigation intent`() {
        val result = service.checkLocalNavigationIntent("Take me to the feed")
        assertNotNull(result)
        assertTrue(result is GeminiVoiceService.VoiceCommandResult.Navigate)
        assertEquals(ThikanaTab.FEED, (result as GeminiVoiceService.VoiceCommandResult.Navigate).tab)
    }

    @Test
    fun `test discover navigation intent`() {
        val result = service.checkLocalNavigationIntent("Find waterfall spots")
        assertNotNull(result)
        assertTrue(result is GeminiVoiceService.VoiceCommandResult.Navigate)
        assertEquals(ThikanaTab.DISCOVER, (result as GeminiVoiceService.VoiceCommandResult.Navigate).tab)
    }

    @Test
    fun `test create post navigation intent`() {
        val result = service.checkLocalNavigationIntent("Drop a new post")
        assertNotNull(result)
        assertTrue(result is GeminiVoiceService.VoiceCommandResult.Navigate)
        assertEquals(ThikanaTab.CREATE, (result as GeminiVoiceService.VoiceCommandResult.Navigate).tab)
    }

    @Test
    fun `test messages navigation intent`() {
        val result = service.checkLocalNavigationIntent("Check my messages and alerts")
        assertNotNull(result)
        assertTrue(result is GeminiVoiceService.VoiceCommandResult.Navigate)
        assertEquals(ThikanaTab.MESSAGES, (result as GeminiVoiceService.VoiceCommandResult.Navigate).tab)
    }

    @Test
    fun `test profile navigation intent`() {
        val result = service.checkLocalNavigationIntent("Open my profile aesthetic")
        assertNotNull(result)
        assertTrue(result is GeminiVoiceService.VoiceCommandResult.Navigate)
        assertEquals(ThikanaTab.PROFILE, (result as GeminiVoiceService.VoiceCommandResult.Navigate).tab)
    }

    @Test
    fun `test scroll down action intent`() {
        val result = service.checkLocalNavigationIntent("Scroll down for more")
        assertNotNull(result)
        assertTrue(result is GeminiVoiceService.VoiceCommandResult.ExecuteAction)
        assertEquals("SCROLL_DOWN", (result as GeminiVoiceService.VoiceCommandResult.ExecuteAction).action)
    }
}
