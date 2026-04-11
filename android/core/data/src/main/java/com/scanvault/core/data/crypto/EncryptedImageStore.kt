package com.scanvault.core.data.crypto

import java.io.File

/**
 * Contract for AES-256-GCM encrypted image persistence.
 *
 * All paths are relative to the app's private files directory.
 * The implementation uses a single master key that never leaves the keystore.
 */
interface EncryptedImageStore {
    /**
     * Encrypt [data] and write it to [destFile].
     * The file will contain: [IV (12 bytes)] + [ciphertext + auth tag].
     */
    fun write(destFile: File, data: ByteArray)

    /**
     * Decrypt [srcFile] and return the plaintext bytes.
     * @throws SecurityException if authentication fails (tampered data).
     */
    fun read(srcFile: File): ByteArray

    /** Delete the file and zero any in-memory state associated with it. */
    fun delete(file: File)
}
