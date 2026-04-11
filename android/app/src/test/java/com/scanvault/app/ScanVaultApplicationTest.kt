package com.scanvault.app

import org.junit.Test
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Unit tests for application class.
 *
 * Verifies the class is annotated with @HiltAndroidApp at the structural level.
 * Full DI injection tests run on device via androidTest.
 */
class ScanVaultApplicationTest {

    @Test
    fun `ScanVaultApplication has HiltAndroidApp annotation`() {
        val annotation = ScanVaultApplication::class.java
            .getAnnotation(dagger.hilt.android.HiltAndroidApp::class.java)
        assertNotNull("@HiltAndroidApp annotation must be present", annotation)
    }

    @Test
    fun `ScanVaultApplication extends Application`() {
        val isApplication = android.app.Application::class.java
            .isAssignableFrom(ScanVaultApplication::class.java)
        assertTrue("ScanVaultApplication must extend android.app.Application", isApplication)
    }
}
