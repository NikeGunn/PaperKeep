package app.paperkeep.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P3.16 pre-flight — static manifest audit.
 *
 * Locks in the security guarantees the Play Pre-Launch Report and our own
 * spec (`docs/PAPERKEEP_DESIGN.md` §6) require:
 *
 *  - Every `android:exported="true"` element MUST have a justification comment
 *    immediately above it (CLAUDE.md ban rule).
 *  - `android:allowBackup` MUST be false (no autobackup of encrypted blobs).
 *  - `android:usesCleartextTraffic` MUST NOT be set to true.
 *  - No banned permissions (READ_LOGS, REQUEST_INSTALL_PACKAGES, etc.).
 *  - FileProvider authority MUST be `${applicationId}.fileprovider` and not
 *    exported.
 */
class ManifestAuditTest {

    private val manifest: File by lazy {
        // Walk up to find the project's app manifest.
        val codeSource = ManifestAuditTest::class.java.protectionDomain?.codeSource
        var f: File? = if (codeSource != null) File(codeSource.location.toURI()) else null
        while (f != null && !File(f, "android/app/src/main/AndroidManifest.xml").exists()) {
            f = f.parentFile
        }
        f?.let { File(it, "android/app/src/main/AndroidManifest.xml") }
            ?: File("android/app/src/main/AndroidManifest.xml")
    }

    private val text: String by lazy {
        if (manifest.exists()) manifest.readText() else ""
    }

    @Test
    fun manifest_isReachable() {
        assertTrue(
            "AndroidManifest.xml must be reachable from test classpath (got: ${manifest.absolutePath})",
            manifest.exists(),
        )
    }

    @Test
    fun allowBackup_isFalse() {
        if (!manifest.exists()) return
        assertTrue(
            "android:allowBackup must be \"false\" (encrypted data must not autobackup)",
            text.contains("android:allowBackup=\"false\""),
        )
    }

    @Test
    fun usesCleartextTraffic_isNotEnabled() {
        if (!manifest.exists()) return
        assertTrue(
            "android:usesCleartextTraffic=\"true\" is banned",
            !text.contains("android:usesCleartextTraffic=\"true\""),
        )
    }

    @Test
    fun bannedPermissions_areNotRequested() {
        if (!manifest.exists()) return
        val banned = listOf(
            "android.permission.READ_LOGS",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.WRITE_SETTINGS",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.QUERY_ALL_PACKAGES",
        )
        val violations = banned.filter { text.contains(it) }
        assertTrue(
            "Banned permissions present in manifest: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun every_exportedTrue_hasJustificationComment() {
        if (!manifest.exists()) return
        // For each `android:exported="true"` line, walk back to the nearest
        // `<` opening of the element and assert there is a comment block (-->)
        // somewhere between that element and the previous element. This is a
        // reasonable static proxy for "justified".
        val lines = manifest.readLines()
        val violations = mutableListOf<Int>()
        lines.forEachIndexed { idx, line ->
            if (line.contains("android:exported=\"true\"")) {
                // Walk backward up to 12 lines looking for a `-->` (end of an
                // explanatory comment) before encountering another exported tag.
                val window = (idx - 12).coerceAtLeast(0) until idx
                val hasComment = window.any { lines[it].contains("-->") }
                if (!hasComment) violations.add(idx + 1)
            }
        }
        assertTrue(
            "android:exported=\"true\" must be preceded by a justification comment. " +
                "Violations on lines: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun fileProvider_isNotExported() {
        if (!manifest.exists()) return
        // The FileProvider block must include exported="false".
        val providerBlockStart = text.indexOf("androidx.core.content.FileProvider")
        assertTrue("FileProvider must be declared", providerBlockStart >= 0)
        val providerBlockEnd = text.indexOf("</provider>", providerBlockStart)
        val providerBlock = text.substring(providerBlockStart, providerBlockEnd)
        assertTrue(
            "FileProvider must declare android:exported=\"false\"",
            providerBlock.contains("android:exported=\"false\""),
        )
        assertTrue(
            "FileProvider authority must be \${applicationId}.fileprovider",
            providerBlock.contains("\${applicationId}.fileprovider"),
        )
    }

    @Test
    fun applicationId_andVersion_areCorrect() {
        // Verify build.gradle.kts holds the expected applicationId + versionName.
        val codeSource = ManifestAuditTest::class.java.protectionDomain?.codeSource
        var f: File? = if (codeSource != null) File(codeSource.location.toURI()) else null
        while (f != null && !File(f, "android/app/build.gradle.kts").exists()) f = f.parentFile
        val gradle = f?.let { File(it, "android/app/build.gradle.kts") } ?: return
        if (!gradle.exists()) return
        val gradleText = gradle.readText()
        assertTrue(
            "applicationId must be \"app.paperkeep\"",
            gradleText.contains("applicationId = \"app.paperkeep\""),
        )
        // Version-agnostic: assert a well-formed semver versionName exists, not a
        // specific value — otherwise every release bump breaks this test.
        assertTrue(
            "versionName must be a valid semver string",
            VERSION_NAME_REGEX.containsMatchIn(gradleText),
        )
    }

    @Test
    fun versionFile_matches_buildGradle() {
        val codeSource = ManifestAuditTest::class.java.protectionDomain?.codeSource
        var f: File? = if (codeSource != null) File(codeSource.location.toURI()) else null
        while (f != null && !File(f, "VERSION").exists()) f = f.parentFile
        val root = f ?: return
        val versionFile = File(root, "VERSION")
        val gradle = File(root, "android/app/build.gradle.kts")
        if (!versionFile.exists() || !gradle.exists()) return
        // The real invariant: the VERSION file and build.gradle.kts versionName must
        // agree. We don't pin a literal version, so releases never break this test.
        val versionFromFile = versionFile.readText().trim()
        val versionFromGradle = VERSION_NAME_REGEX
            .find(gradle.readText())?.groupValues?.get(1)
        assertEquals(
            "VERSION file must equal build.gradle.kts versionName",
            versionFromFile,
            versionFromGradle,
        )
    }

    private companion object {
        // Matches: versionName = "2.0.0" or "2.0.0-alpha.2" etc., capturing the value.
        val VERSION_NAME_REGEX =
            Regex("""versionName\s*=\s*"(\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?)"""")
    }
}
