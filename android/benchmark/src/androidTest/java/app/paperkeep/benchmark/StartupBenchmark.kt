package app.paperkeep.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark tests for Paperkeep startup and library scroll performance.
 *
 * Run with:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 *
 * Results appear in Android Studio → Benchmark tab.
 * Target: cold start < 500ms, library scroll 60fps @ 1000 docs.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "app.paperkeep",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = "app.paperkeep",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun libraryScrollFrameTiming() = benchmarkRule.measureRepeated(
        packageName = "app.paperkeep",
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Full(),
    ) {
        pressHome()
        startActivityAndWait()
        // Scroll the document library grid
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            /* steps = */ 10,
        )
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight / 4,
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            10,
        )
    }
}
