package com.scanvault.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for ScanVault.
 *
 * A Baseline Profile pre-compiles the critical hot paths (startup, camera launch, library scroll)
 * so the JIT compiler doesn't need to warm up on the first run.
 *
 * Run with:
 *   ./gradlew :benchmark:generateBaselineProfile
 *
 * The generated profile is written to:
 *   app/src/main/baseline-prof.txt
 *
 * Commit this file to version control — it ships with the release APK and reduces
 * cold-start time by 20-30% on first launch.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.scanvault.app",
    ) {
        // Critical user journey 1: cold launch to library
        pressHome()
        startActivityAndWait()

        // Critical user journey 2: navigate to camera
        // (UiAutomator finds the Scan FAB)
        val scanFab = device.findObject(
            androidx.test.uiautomator.By.desc("Scan document")
        )
        if (scanFab != null && scanFab.isClickable) {
            scanFab.click()
            device.waitForIdle()
        }

        // Critical user journey 3: return to library and scroll
        device.pressBack()
        device.waitForIdle()
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            10,
        )
    }
}
