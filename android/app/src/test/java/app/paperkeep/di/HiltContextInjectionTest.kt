package app.paperkeep.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HiltContextInjectionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var injectedApplicationContext: Context

    @Test
    fun applicationContext_isInjectedViaHilt() {
        hiltRule.inject()

        val appContext = ApplicationProvider.getApplicationContext<Context>()

        assertNotNull(injectedApplicationContext)
        assertEquals(appContext.packageName, injectedApplicationContext.packageName)
    }
}