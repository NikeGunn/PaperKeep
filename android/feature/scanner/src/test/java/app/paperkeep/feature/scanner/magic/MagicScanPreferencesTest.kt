package app.paperkeep.feature.scanner.magic

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MagicScanPreferencesTest {

    private lateinit var prefs: MagicScanPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // DataStore preferences live under <filesDir>/datastore — wipe before each run
        // so previous Robolectric sessions don't bleed state.
        File(context.filesDir.parentFile, "files/datastore").deleteRecursively()
        prefs = MagicScanPreferences(context)
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir.parentFile, "files/datastore").deleteRecursively()
    }

    @Test
    fun defaultIsEnabled() = runTest {
        assertTrue("Magic Scan must default to enabled", prefs.isEnabled.first())
        assertTrue(MagicScanPreferences.DEFAULT_ENABLED)
    }

    @Test
    fun setEnabled_persistsValue() = runTest {
        prefs.setEnabled(false)
        assertFalse(prefs.isEnabled.first())
        prefs.setEnabled(true)
        assertTrue(prefs.isEnabled.first())
    }
}
