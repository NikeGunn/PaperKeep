package com.scanvault.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InterstitialAdControllerTest {

    private lateinit var clock: FakeClock
    private lateinit var controller: InterstitialAdController

    @Before
    fun setUp() {
        clock = FakeClock(nowMs = 0L)
        controller = InterstitialAdController(clock)
    }

    @Test
    fun `interstitial not shown on 1st export`() {
        assertFalse(controller.onExport())
    }

    @Test
    fun `interstitial not shown on 2nd export`() {
        controller.onExport()
        assertFalse(controller.onExport())
    }

    @Test
    fun `interstitial not shown on 4th export`() {
        repeat(3) { controller.onExport() }
        assertFalse(controller.onExport())
    }

    @Test
    fun `interstitial IS shown on 5th export`() {
        repeat(4) { controller.onExport() }
        assertTrue("5th export must trigger interstitial", controller.onExport())
    }

    @Test
    fun `interstitial NOT shown again within 3-minute frequency cap`() {
        // Trigger first ad on export 5
        repeat(4) { controller.onExport() }
        controller.onExport() // 5th — shows ad, resets counter, records lastShownMs = 0

        // Immediately do 5 more exports (cap still active — time = 0ms)
        repeat(4) { controller.onExport() }
        assertFalse("frequency cap must block interstitial within 3 minutes", controller.onExport())
    }

    @Test
    fun `interstitial shown again after 3-minute cap expires`() {
        // First ad cycle
        repeat(4) { controller.onExport() }
        controller.onExport() // shows ad at t=0

        // Advance clock past 3 minutes
        clock.nowMs = InterstitialAdController.FREQUENCY_CAP_MS + 1

        // Second cycle of 5 exports
        repeat(4) { controller.onExport() }
        assertTrue("interstitial must be shown after frequency cap expires", controller.onExport())
    }

    @Test
    fun `banner ad is never created — BANNER_ADS_SUPPORTED is false`() {
        assertFalse(AdMobManager.BANNER_ADS_SUPPORTED)
    }
}
