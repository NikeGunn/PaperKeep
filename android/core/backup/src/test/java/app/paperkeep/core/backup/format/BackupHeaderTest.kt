package app.paperkeep.core.backup.format

import app.paperkeep.core.backup.crypto.BackupCipher
import app.paperkeep.core.backup.crypto.BackupKdf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupHeaderTest {

    private fun fixedHeader(): BackupHeader {
        val salt = ByteArray(BackupKdf.SALT_BYTES) { it.toByte() }
        val iv = ByteArray(BackupCipher.IV_BYTES) { (it + 100).toByte() }
        return BackupHeader.new(
            memoryKib = 65_536,
            iterations = 3,
            parallelism = 2,
            salt = salt,
            iv = iv,
        )
    }

    @Test
    fun roundTrip_preservesAllFields() {
        val h = fixedHeader()
        val baos = ByteArrayOutputStream()
        h.writeTo(baos)
        val bytes = baos.toByteArray()
        assertEquals(BackupHeader.FIXED_SIZE, bytes.size)

        val parsed = BackupHeader.readFrom(ByteArrayInputStream(bytes))
        assertEquals(h, parsed)
        assertArrayEquals(h.salt, parsed.salt)
        assertArrayEquals(h.iv, parsed.iv)
    }

    @Test
    fun magicMatchesAsciiPKBK() {
        val baos = ByteArrayOutputStream()
        fixedHeader().writeTo(baos)
        val bytes = baos.toByteArray()
        assertEquals(0x50.toByte(), bytes[0]) // P
        assertEquals(0x4B.toByte(), bytes[1]) // K
        assertEquals(0x42.toByte(), bytes[2]) // B
        assertEquals(0x4B.toByte(), bytes[3]) // K
    }

    @Test
    fun versionIsCurrent() {
        val baos = ByteArrayOutputStream()
        fixedHeader().writeTo(baos)
        // bytes 4-5 = uint16 version, big-endian
        val versionHigh = baos.toByteArray()[4].toInt() and 0xFF
        val versionLow = baos.toByteArray()[5].toInt() and 0xFF
        val version = (versionHigh shl 8) or versionLow
        assertEquals(BackupHeader.VERSION_CURRENT, version)
    }

    @Test(expected = BackupHeaderException.BadMagic::class)
    fun badMagic_throws() {
        val bytes = ByteArray(BackupHeader.FIXED_SIZE)
        BackupHeader.readFrom(ByteArrayInputStream(bytes))
    }

    @Test(expected = BackupHeaderException.UnsupportedVersion::class)
    fun unsupportedVersion_throws() {
        val baos = ByteArrayOutputStream()
        fixedHeader().writeTo(baos)
        val bytes = baos.toByteArray()
        // Force version = 999
        bytes[4] = 0x03; bytes[5] = 0xE7.toByte()
        BackupHeader.readFrom(ByteArrayInputStream(bytes))
    }

    @Test(expected = BackupHeaderException.InvalidParams::class)
    fun zeroParams_throw() {
        val baos = ByteArrayOutputStream()
        fixedHeader().writeTo(baos)
        val bytes = baos.toByteArray()
        // memoryKib = 0
        bytes[6] = 0; bytes[7] = 0; bytes[8] = 0; bytes[9] = 0
        BackupHeader.readFrom(ByteArrayInputStream(bytes))
    }

    @Test
    fun fixedSize_matches62() {
        // 4 magic + 2 ver + 4 mem + 4 it + 4 par + 32 salt + 12 iv = 62
        assertEquals(62, BackupHeader.FIXED_SIZE)
    }

    @Test
    fun manifestRoundTrip() {
        val m = BackupManifest(
            schemaVersion = 1,
            createdAtMs = 12345L,
            appVersionName = "2.0.0-alpha.1",
            appVersionCode = 1,
            documentCount = 2,
            pageCount = 3,
            pages = listOf(
                BackupManifest.PageRef("pages/d/0.jpg", "d", "p", 0, "abc", 100L),
                BackupManifest.PageRef("pages/d/1.jpg", "d", "q", 1, "def", 200L),
            ),
        )
        val s = BackupManifest.encode(m)
        val parsed = BackupManifest.decode(s)
        assertEquals(m, parsed)
    }

    @Test
    fun manifestDecode_ignoresUnknownKeys() {
        val s = """{"format":"paperkeep.backup","schemaVersion":1,"createdAtMs":1,"appVersionName":"x","appVersionCode":1,"documentCount":0,"pageCount":0,"pages":[],"futureField":"unknown"}"""
        val parsed = BackupManifest.decode(s)
        assertEquals(BackupManifest.FORMAT, parsed.format)
        assertTrue(parsed.pages.isEmpty())
    }

    @Test
    fun settingsRoundTrip() {
        val s = BackupSettings(
            biometricLockEnabled = true,
            lockTimeoutKey = "FIVE_MINUTES",
            screenshotProtectionEnabled = false,
            backupReminderCadenceKey = "WEEKLY",
        )
        val encoded = BackupSettings.encode(s)
        val decoded = BackupSettings.decode(encoded)
        assertEquals(s, decoded)
    }
}
