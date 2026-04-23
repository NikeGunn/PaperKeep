package app.paperkeep.core.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies Play Store prep artefacts exist and are non-empty (3B.13).
 *
 * These are pure JVM tests — no Android context needed.
 */
class PlayStorePrepTest {

    // Resolve relative to the project root (android/ parent)
    private val androidDir: File by lazy {
        // When running via Gradle the working directory is the module root
        // (android/core/common). Walk up to android/ then store/.
        var dir = File("").absoluteFile
        // Walk up until we find the "android" dir or its parent
        repeat(5) {
            if (dir.name == "android" || File(dir, "store").exists()) return@repeat
            dir = dir.parentFile ?: dir
        }
        dir
    }

    @Test
    fun `store-listing_txt exists and is non-empty`() {
        val file = File(androidDir, "store/listing.txt")
        assertTrue("store/listing.txt must exist", file.exists())
        assertTrue("store/listing.txt must be non-empty", file.length() > 0)
    }

    @Test
    fun `feature-graphic-spec_txt mentions 1024x500`() {
        val file = File(androidDir, "store/feature-graphic-spec.txt")
        assertTrue("store/feature-graphic-spec.txt must exist", file.exists())
        val content = file.readText()
        assertTrue(
            "feature-graphic-spec.txt must mention '1024' and '500'",
            content.contains("1024") && content.contains("500"),
        )
    }

    @Test
    fun `screenshots directory exists`() {
        val dir = File(androidDir, "store/screenshots")
        assertTrue("store/screenshots/ directory must exist", dir.isDirectory)
    }
}
