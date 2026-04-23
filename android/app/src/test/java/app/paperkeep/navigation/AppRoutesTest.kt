package app.paperkeep.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for 1B.18: Type-safe navigation routes.
 *
 * Navigation Compose 2.8+ uses @Serializable routes. We verify:
 *  - Routes serialize/deserialize correctly (the key requirement for type-safe nav)
 *  - Routes with parameters carry their data through serialization
 *  - All declared routes are non-null (compile-time check via object instantiation)
 */
class AppRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── 1B.18: Singleton routes exist ─────────────────────────────────────────

    @Test
    fun scannerRoute_isNotNull() {
        assertNotNull(ScannerRoute)
    }

    @Test
    fun libraryRoute_isNotNull() {
        assertNotNull(LibraryRoute)
    }

    @Test
    fun settingsRoute_isNotNull() {
        assertNotNull(SettingsRoute)
    }

    // ── 1B.18: Parameterised routes serialize/deserialize correctly ───────────

    @Test
    fun cropRoute_serializesAndDeserializes() {
        val original = CropRoute(capturedImagePath = "/storage/emulated/0/DCIM/capture.jpg")

        val serialized = json.encodeToString(original)
        val restored = json.decodeFromString<CropRoute>(serialized)

        assertEquals(original.capturedImagePath, restored.capturedImagePath)
    }

    @Test
    fun readerRoute_serializesAndDeserializes() {
        val original = ReaderRoute(scanId = "uuid-1234-abcd")

        val serialized = json.encodeToString(original)
        val restored = json.decodeFromString<ReaderRoute>(serialized)

        assertEquals(original.scanId, restored.scanId)
    }

    @Test
    fun cropRoute_preservesSpecialCharsInPath() {
        val path = "/data/user/0/app.paperkeep.debug/files/scans/scan with spaces & symbols!.jpg"
        val route = CropRoute(capturedImagePath = path)

        val serialized = json.encodeToString(route)
        val restored = json.decodeFromString<CropRoute>(serialized)

        assertEquals(path, restored.capturedImagePath)
    }

    @Test
    fun readerRoute_preservesUuidFormat() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val route = ReaderRoute(scanId = uuid)

        val serialized = json.encodeToString(route)
        val restored = json.decodeFromString<ReaderRoute>(serialized)

        assertEquals(uuid, restored.scanId)
    }

    // ── 1B.18: Routes are distinct types ──────────────────────────────────────

    @Test
    fun scannerAndLibraryRoute_areDifferentTypes() {
        val scanner: Any = ScannerRoute
        val library: Any = LibraryRoute
        assert(scanner.javaClass != library.javaClass) {
            "ScannerRoute and LibraryRoute must be distinct types for type-safe navigation"
        }
    }
}
