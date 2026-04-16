package com.scanvault.core.ads

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AdMobManagerTest {

    @Test
    fun `AdMob is NOT initialized at construction time — lazy init`() {
        val manager = AdMobManager()
        assertFalse("AdMob must not be initialized at construction", manager.initialized)
    }

    @Test
    fun `AdMob initializes on first initialize() call`() {
        val manager = AdMobManager()
        manager.initialize(ApplicationProvider.getApplicationContext())
        assertTrue("AdMob should be initialized after initialize()", manager.initialized)
    }

    @Test
    fun `AdMob BANNER_ADS_SUPPORTED is false — no banner anywhere`() {
        assertFalse(
            "Banner ads must never be supported",
            AdMobManager.BANNER_ADS_SUPPORTED,
        )
    }

    @Test
    fun `second initialize call is a no-op — still initialized`() {
        val manager = AdMobManager()
        manager.initialize(ApplicationProvider.getApplicationContext())
        manager.initialize(ApplicationProvider.getApplicationContext())
        assertTrue(manager.initialized)
    }
}
