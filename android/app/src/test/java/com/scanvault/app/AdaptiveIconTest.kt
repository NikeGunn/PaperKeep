package com.scanvault.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for 1B.7: Adaptive app icon.
 *
 * Verifies that both foreground and background drawable resources exist,
 * which are required for a valid adaptive icon on API 26+.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdaptiveIconTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun iconForeground_drawableExists() {
        val resId = context.resources.getIdentifier(
            "ic_launcher_foreground", "drawable", context.packageName
        )
        assertNotEquals(
            "ic_launcher_foreground drawable must exist for adaptive icon",
            0, resId
        )
    }

    @Test
    fun iconBackground_colorExists() {
        val resId = context.resources.getIdentifier(
            "ic_launcher_background", "color", context.packageName
        )
        assertNotEquals(
            "ic_launcher_background color must exist for adaptive icon",
            0, resId
        )
    }

    @Test
    fun iconBackground_drawableExists() {
        val resId = context.resources.getIdentifier(
            "ic_launcher_background", "drawable", context.packageName
        )
        assertNotEquals(
            "ic_launcher_background drawable must exist as background layer",
            0, resId
        )
    }

    @Test
    fun launcherIcon_mipmapExists() {
        val resId = context.resources.getIdentifier(
            "ic_launcher", "mipmap", context.packageName
        )
        assertNotEquals(
            "ic_launcher mipmap must exist",
            0, resId
        )
    }
}
