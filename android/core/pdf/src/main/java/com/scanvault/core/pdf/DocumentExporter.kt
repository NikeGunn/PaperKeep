package com.scanvault.core.pdf

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles non-PDF export formats for Phase 2B.9:
 *  - JPEG (single page, quality 90)
 *  - PNG  (single page, lossless)
 *  - TXT  (OCR text of all pages concatenated)
 *  - Encrypted ZIP (AES-256-CBC, password-derived key via PBKDF2)
 *
 * All methods write to the supplied [destFile]. Parent directories must exist.
 */
@Singleton
class DocumentExporter @Inject constructor() {

    // ── Single-page image exports ─────────────────────────────────────────────

    /**
     * Write [bitmap] to [destFile] as a JPEG with quality 90.
     */
    fun exportJpeg(bitmap: Bitmap, destFile: File) {
        destFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
    }

    /**
     * Write [bitmap] to [destFile] as a lossless PNG.
     */
    fun exportPng(bitmap: Bitmap, destFile: File) {
        destFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    // ── Text export ───────────────────────────────────────────────────────────

    /**
     * Write all [ocrTexts] (in page order) to [destFile] as UTF-8 plain text.
     *
     * Pages are separated by a line of hyphens.
     */
    fun exportTxt(ocrTexts: List<String>, destFile: File) {
        val content = ocrTexts.joinToString(separator = "\n${PAGE_SEPARATOR}\n")
        destFile.writeText(content, Charsets.UTF_8)
    }

    // ── Encrypted ZIP export ──────────────────────────────────────────────────

    /**
     * Create an AES-256-CBC encrypted ZIP at [destFile] containing one JPEG
     * per page entry (`page_NNN.jpg`).
     *
     * Encryption envelope:
     *  - 16-byte random salt   (for PBKDF2)
     *  - 16-byte random IV     (for AES-CBC)
     *  - AES-256-CBC ciphertext of the raw ZIP bytes
     *
     * The key is derived via PBKDF2WithHmacSHA256:
     *   salt = random 16 bytes, iterations = 10_000, keyLen = 256 bits
     *
     * @throws IllegalArgumentException if [pages] is empty.
     */
    fun exportEncryptedZip(pages: List<Bitmap>, password: CharArray, destFile: File) {
        require(pages.isNotEmpty()) { "Cannot create a ZIP with zero pages." }

        // 1. Build in-memory ZIP of all pages as JPEGs
        val zipBytes = buildZipBytes(pages)

        // 2. Derive AES key from password
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val keyBytes = deriveKey(password, salt)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // 3. Encrypt the ZIP bytes
        val cipher = Cipher.getInstance(ZIP_CIPHER).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        }
        val ciphertext = cipher.doFinal(zipBytes)

        // 4. Write envelope: [salt][iv][ciphertext]
        FileOutputStream(destFile).use { fos ->
            fos.write(salt)
            fos.write(iv)
            fos.write(ciphertext)
        }
    }

    /**
     * Decrypt an encrypted ZIP produced by [exportEncryptedZip] and return the
     * raw ZIP bytes, or throw if the password is wrong / data is corrupted.
     *
     * @throws javax.crypto.BadPaddingException on wrong password or tampered data.
     */
    fun decryptZip(encFile: File, password: CharArray): ByteArray {
        val data = encFile.readBytes()
        require(data.size > SALT_LEN + IV_LEN) { "File too short to be a valid encrypted ZIP." }

        val salt = data.copyOfRange(0, SALT_LEN)
        val iv = data.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val ciphertext = data.copyOfRange(SALT_LEN + IV_LEN, data.size)

        val keyBytes = deriveKey(password, salt)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(ZIP_CIPHER).apply {
            init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        }
        return cipher.doFinal(ciphertext)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildZipBytes(pages: List<Bitmap>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            pages.forEachIndexed { index, bitmap ->
                val entryName = "page_%03d.jpg".format(index + 1)
                zip.putNextEntry(ZipEntry(entryName))
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, zip)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LEN_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    companion object {
        private const val JPEG_QUALITY = 90
        private const val PAGE_SEPARATOR = "---"
        private const val SALT_LEN = 16
        private const val IV_LEN = 16
        private const val KEY_LEN_BITS = 256
        private const val PBKDF2_ITERATIONS = 10_000
        private const val ZIP_CIPHER = "AES/CBC/PKCS5Padding"
    }
}
