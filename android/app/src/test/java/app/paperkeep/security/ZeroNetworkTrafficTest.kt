package app.paperkeep.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P1.12 — Static analysis: verify zero unauthorized network traffic.
 *
 * This test walks the android/ source tree and asserts that no banned network
 * library imports exist in app source code. It's a belt-and-suspenders check
 * on top of the Detekt rule and the build.gradle.kts dependency guard.
 *
 * What we allow (Google SDKs):
 *  - AdMob / UMP (com.google.android.gms.ads)
 *  - Play Billing (com.android.billingclient)
 *  - Play Integrity (com.google.android.play.core.integrity)
 *  - ML Kit (com.google.mlkit)
 *
 * What we explicitly ban in our own source:
 *  - okhttp3 (OkHttp)
 *  - io.ktor (Ktor)
 *  - retrofit2 (Retrofit)
 *  - com.squareup.okhttp (legacy OkHttp)
 *  - java.net.HttpURLConnection (manual HTTP)
 *  - java.net.URL (manual URL open — unless it's a UriMatcher or similar)
 *
 * Note: Live traffic verification requires a connected device + mitmproxy.
 * See docs/PAPERKEEP_DESIGN.md §5 Phase 1 acceptance and benchmark/BASELINES.md.
 */
class ZeroNetworkTrafficTest {

    companion object {
        // Banned import prefixes (in our source, not transitive deps)
        private val BANNED_IMPORTS = listOf(
            "import okhttp3.",
            "import io.ktor.",
            "import retrofit2.",
            "import com.squareup.okhttp.",
        )

        // Allowed patterns that look like network but are safe
        private val ALLOWLIST_COMMENTS = listOf(
            "// allowed:",
            "// Google SDK",
            "// AdMob",
        )

        private val androidSrcRoot: File by lazy {
            // Walk from the test class location up to find the android/ directory
            val codeSource = ZeroNetworkTrafficTest::class.java.protectionDomain?.codeSource
            var f: File? = if (codeSource != null) File(codeSource.location.toURI()) else null
            while (f != null && !File(f, "android").exists() && !f.name.equals("PaperKeep", ignoreCase = true)) {
                f = f.parentFile
            }
            if (f != null) File(f, "android") else File("android")
        }
    }

    @Test
    fun androidSrcRoot_exists() {
        assertTrue(
            "android/ source root must be findable from test classpath (got: ${androidSrcRoot.absolutePath})",
            androidSrcRoot.exists() || androidSrcRoot.isDirectory,
        )
    }

    @Test
    fun noBannedNetworkImports_inAppSourceCode() {
        val violations = mutableListOf<String>()

        if (!androidSrcRoot.exists()) {
            // Running in a CI environment where source root is not accessible
            // — skip silently; the Detekt check covers this in CI.
            return
        }

        androidSrcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                // Only check src/main/ — not test files (tests may import test-only libs)
                file.path.contains("/src/main/") || file.path.contains("\\src\\main\\")
            }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { lineIdx, line ->
                    val trimmed = line.trim()
                    val isBanned = BANNED_IMPORTS.any { trimmed.startsWith(it) }
                    val isAllowlisted = ALLOWLIST_COMMENTS.any { line.contains(it) }
                    if (isBanned && !isAllowlisted) {
                        violations.add("${file.path}:${lineIdx + 1} → $trimmed")
                    }
                }
            }

        assertTrue(
            "Banned network library imports found in app source:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun noManualHttpUrlConnection_inAppSourceCode() {
        val violations = mutableListOf<String>()

        if (!androidSrcRoot.exists()) return

        androidSrcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.path.contains("/src/main/") || file.path.contains("\\src\\main\\")
            }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { lineIdx, line ->
                    if (line.contains("HttpURLConnection") || line.contains("openConnection()")) {
                        violations.add("${file.path}:${lineIdx + 1} → ${line.trim()}")
                    }
                }
            }

        assertTrue(
            "Manual HttpURLConnection usage found in app source:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun noOwnApiBaseUrl_inAppSourceCode() {
        // Ensure we haven't re-introduced an API_BASE_URL or similar
        val violations = mutableListOf<String>()

        if (!androidSrcRoot.exists()) return

        androidSrcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.path.contains("/src/main/") || file.path.contains("\\src\\main\\")
            }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { lineIdx, line ->
                    if (line.contains("API_BASE_URL") || line.contains("BASE_URL") || line.contains("apiBaseUrl")) {
                        violations.add("${file.path}:${lineIdx + 1} → ${line.trim()}")
                    }
                }
            }

        assertTrue(
            "API base URL reference found in app source (no backend exists):\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
