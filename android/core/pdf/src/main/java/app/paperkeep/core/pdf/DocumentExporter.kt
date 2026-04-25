package app.paperkeep.core.pdf

import android.graphics.Bitmap
import de.mkammerer.argon2.Argon2Advanced
import de.mkammerer.argon2.Argon2Factory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles non-PDF export formats for P2.10:
 *  - JPEG (single page, quality [JPEG_QUALITY])
 *  - PNG  (single page, lossless)
 *  - JPEG ZIP (all pages in a plain ZIP)
 *  - TXT  (OCR text of all pages, UTF-8)
 *  - Encrypted ZIP (AES-256-GCM, Argon2id(m=65536, t=3, p=4) KDF per §6.1)
 *
 * All methods write to the supplied [destFile]. Parent directories must exist.
 *
 * Security note on encrypted ZIP:
 *   salt 32B | IV 12B | GCM tag is embedded in the ciphertext output (last 16B)
 *   The final layout written to disk is: [salt(32)][iv(12)][ciphertext+tag]
 */
@Singleton
class DocumentExporter @Inject constructor() {

    private val random = SecureRandom()

    // ── JPEG ──────────────────────────────────────────────────────────────────

    fun exportJpeg(bitmap: Bitmap, destFile: File) {
        destFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    }

    // ── PNG ───────────────────────────────────────────────────────────────────

    fun exportPng(bitmap: Bitmap, destFile: File) {
        destFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ── Plain ZIP of JPEGs ────────────────────────────────────────────────────

    /**
     * Write a ZIP containing one JPEG per page (`page_001.jpg`, `page_002.jpg`, …).
     */
    fun exportJpegZip(pages: List<Bitmap>, destFile: File) {
        require(pages.isNotEmpty()) { "Cannot export ZIP with zero pages." }
        destFile.outputStream().use { fos ->
            ZipOutputStream(fos).use { zip ->
                pages.forEachIndexed { i, bmp ->
                    zip.putNextEntry(ZipEntry("page_%03d.jpg".format(i + 1)))
                    bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, zip)
                    zip.closeEntry()
                }
            }
        }
    }

    // ── Plain text (OCR dump) ─────────────────────────────────────────────────

    fun exportTxt(ocrTexts: List<String>, destFile: File) {
        val content = ocrTexts.joinToString(separator = "\n$PAGE_SEPARATOR\n")
        destFile.writeText(content, Charsets.UTF_8)
    }

    // ── Encrypted ZIP (Argon2id + AES-256-GCM) ───────────────────────────────

    /**
     * Create an AES-256-GCM encrypted ZIP at [destFile].
     *
     * Encryption envelope:
     *  - 32-byte random Argon2id salt
     *  - 12-byte random GCM IV
     *  - AES-256-GCM ciphertext of the raw ZIP bytes (tag appended by JCE)
     *
     * Key derivation: Argon2id(password, salt, m=65536 KiB, t=3 iterations, p=4 lanes)
     * Per OWASP recommendation for interactive login (§6.1 uses m=128 MiB for backup;
     * export uses lighter params because it runs on the UI thread during export).
     *
     * @throws IllegalArgumentException if [pages] is empty.
     */
    fun exportEncryptedZip(pages: List<Bitmap>, password: CharArray, destFile: File) {
        require(pages.isNotEmpty()) { "Cannot create encrypted ZIP with zero pages." }

        val zipBytes = buildZipBytes(pages)

        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LEN).also { random.nextBytes(it) }
        val keyBytes = deriveKeyArgon2id(password, salt)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(GCM_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(zipBytes)

        FileOutputStream(destFile).use { fos ->
            fos.write(salt)
            fos.write(iv)
            fos.write(ciphertext)
        }
    }

    /**
     * Decrypt an encrypted ZIP produced by [exportEncryptedZip] and return the
     * raw ZIP bytes.
     *
     * @throws javax.crypto.BadPaddingException on wrong password or tampered data.
     */
    fun decryptZip(encFile: File, password: CharArray): ByteArray {
        val data = encFile.readBytes()
        require(data.size > SALT_LEN + GCM_IV_LEN) { "File too short to be a valid encrypted ZIP." }

        val salt = data.copyOfRange(0, SALT_LEN)
        val iv = data.copyOfRange(SALT_LEN, SALT_LEN + GCM_IV_LEN)
        val ciphertext = data.copyOfRange(SALT_LEN + GCM_IV_LEN, data.size)

        val keyBytes = deriveKeyArgon2id(password, salt)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(GCM_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildZipBytes(pages: List<Bitmap>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            pages.forEachIndexed { i, bmp ->
                zip.putNextEntry(ZipEntry("page_%03d.jpg".format(i + 1)))
                bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, zip)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    /**
     * Derive a 256-bit AES key using Argon2id.
     *
     * Parameters (export profile — lighter than backup profile):
     *  - memory:      65536 KiB (64 MiB)
     *  - iterations:  3
     *  - parallelism: 4
     *  - hash length: 32 bytes (256 bits)
     */
    internal fun deriveKeyArgon2id(password: CharArray, salt: ByteArray): ByteArray {
        val argon2 = Argon2Factory.createAdvanced(
            Argon2Factory.Argon2Types.ARGON2id,
            ARGON2_SALT_LEN,
            ARGON2_KEY_LEN,
        ) as Argon2Advanced
        return try {
            argon2.rawHash(
                ARGON2_ITERATIONS,
                ARGON2_MEMORY_KIB,
                ARGON2_PARALLELISM,
                password,
                Charsets.UTF_8,
                salt,
            )
        } finally {
            argon2.wipeArray(password)
        }
    }

    companion object {
        private const val JPEG_QUALITY = 90
        private const val PAGE_SEPARATOR = "---"
        private const val SALT_LEN = 32
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        // Argon2id parameters (export profile)
        private const val ARGON2_ITERATIONS = 3
        private const val ARGON2_MEMORY_KIB = 65536  // 64 MiB
        private const val ARGON2_PARALLELISM = 4
        private const val ARGON2_KEY_LEN = 32  // 256-bit output
        private const val ARGON2_SALT_LEN = 32 // must match SALT_LEN
    }
}
