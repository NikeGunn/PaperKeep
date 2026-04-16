package com.scanvault.feature.reader.signature

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists and retrieves the user's signature as raw PNG bytes (3B.6).
 *
 * Storage: an AES-GCM encrypted file in the app's internal files directory.
 * For Phase 3B the bytes are stored as plain bytes in a private file
 * (no external access). Phase 4B will wire this through [AesGcmImageStore].
 */
@Singleton
class SignatureRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val signatureFile: File
        get() = File(context.filesDir, SIGNATURE_FILE_NAME)

    /**
     * Persist [pngBytes] as the user's saved signature.
     *
     * @param pngBytes PNG-encoded byte array. Must not be empty.
     * @throws IllegalArgumentException if [pngBytes] is empty.
     */
    fun save(pngBytes: ByteArray) {
        require(pngBytes.isNotEmpty()) { "Signature bytes must not be empty" }
        signatureFile.parentFile?.mkdirs()
        signatureFile.writeBytes(pngBytes)
    }

    /**
     * Load the previously saved signature.
     *
     * @return The raw PNG bytes, or `null` if no signature has been saved yet.
     */
    fun load(): ByteArray? {
        if (!signatureFile.exists()) return null
        val bytes = signatureFile.readBytes()
        return if (bytes.isEmpty()) null else bytes
    }

    /** @return `true` if a saved signature exists. */
    fun exists(): Boolean = signatureFile.exists() && signatureFile.length() > 0

    /** Delete the saved signature. */
    fun delete() {
        signatureFile.delete()
    }

    companion object {
        private const val SIGNATURE_FILE_NAME = "signature.png"
    }
}
