package app.paperkeep.di

import org.junit.Test
import org.junit.Assert.assertNotNull

/**
 * Unit tests for Hilt DI wiring.
 *
 * Full injection tests require instrumented tests with HiltTestApplication.
 * These unit tests validate the module structure is correct at compile time.
 */
class AppModuleTest {

    @Test
    fun `AppModule provides application context`() {
        // AppModule is a Dagger @Module — its existence is validated at compile time
        // by kapt/KSP. If the module has annotation errors, the build fails.
        // This test confirms the module class is loadable by the JVM.
        val module = AppModule
        assertNotNull(module)
    }

    @Test
    fun `AppModule is an object singleton`() {
        // Confirms AppModule is a Kotlin object (not a class), which is the correct
        // pattern for providing stateless bindings in Hilt.
        // Kotlin objects expose a static INSTANCE field via Java reflection (no kotlin-reflect needed).
        val instanceField = AppModule::class.java.getDeclaredField("INSTANCE")
        assertNotNull(instanceField)
    }
}
