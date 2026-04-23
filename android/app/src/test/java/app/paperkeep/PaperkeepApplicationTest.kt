package app.paperkeep

import org.junit.Test
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Unit tests for application class.
 *
 * Verifies the class is annotated with @HiltAndroidApp at the structural level.
 * Full DI injection tests run on device via androidTest.
 */
class PaperkeepApplicationTest {

    @Test
    fun `PaperkeepApplication has HiltAndroidApp annotation`() {
        val annotation = PaperkeepApplication::class.java
            .getAnnotation(dagger.hilt.android.HiltAndroidApp::class.java)
        assertNotNull("@HiltAndroidApp annotation must be present", annotation)
    }

    @Test
    fun `PaperkeepApplication extends Application`() {
        val isApplication = android.app.Application::class.java
            .isAssignableFrom(PaperkeepApplication::class.java)
        assertTrue("PaperkeepApplication must extend android.app.Application", isApplication)
    }
}
