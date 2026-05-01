package app.paperkeep.core.common.crash

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Sub-directory within [Context.filesDir] where encrypted crash logs are stored. */
const val CRASH_LOG_DIR = "crash"

private const val KEY_ALIAS = "Paperkeep_crash_log_key_v1"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_LEN = 12
private const val TAG_BITS = 128

/** 64 KB plaintext max per report — keeps files small enough to email. */
private const val MAX_LOG_CHARS = 64 * 1024

/**
 * Singleton helper for writing and reading AES-256-GCM encrypted crash logs.
 *
 * Key properties:
 * - Stored in the Android Keystore under [KEY_ALIAS].
 * - No user-authentication requirement (crash fires during screen lock).
 * - Fresh random IV per write (12 bytes prepended to the ciphertext blob).
 *
 * File format: `[12-byte IV][ciphertext + 16-byte GCM auth tag]`
 */
object CrashLogStore {

    /**
     * Write [plaintext] to an encrypted file in [CRASH_LOG_DIR]. Rotates to [maxFiles] entries.
     *
     * Silently swallows all exceptions — this is called from crash handlers and must never
     * itself throw. Encryption failures (e.g. Keystore unavailable in tests) are ignored;
     * no file is written in that case.
     */
    fun write(context: Context, plaintext: String, maxFiles: Int = 10) {
        try {
            val logDir = File(context.filesDir, CRASH_LOG_DIR).also { it.mkdirs() }

            // Rotate: delete oldest files beyond the cap
            val existing = logDir.listFiles()
                ?.filter { it.extension == "enc" }
                ?.sortedBy { it.lastModified() } ?: emptyList()
            if (existing.size >= maxFiles) {
                existing.take(existing.size - maxFiles + 1).forEach { it.delete() }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val logFile = File(logDir, "crash_$timestamp.enc")

            val bytes = plaintext.take(MAX_LOG_CHARS).toByteArray(Charsets.UTF_8)
            val key = getOrCreateKey()
            val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            }
            val ciphertext = cipher.doFinal(bytes)

            logFile.outputStream().use { out ->
                out.write(iv)
                out.write(ciphertext)
            }
        } catch (_: Exception) {
            // Swallow — must never throw from a crash handler context.
        }
    }

    /** Return all encrypted crash log files, newest first. */
    fun listFiles(context: Context): List<File> {
        val logDir = File(context.filesDir, CRASH_LOG_DIR)
        return (logDir.listFiles()?.filter { it.extension == "enc" } ?: emptyList())
            .sortedByDescending { it.lastModified() }
    }

    /**
     * Decrypt a crash log file and return its plaintext content.
     * Returns null if the file cannot be decrypted (missing key, tampered, etc.).
     */
    fun read(file: File): String? {
        return try {
            val bytes = file.readBytes()
            if (bytes.size <= IV_LEN) return null
            val iv = bytes.copyOfRange(0, IV_LEN)
            val ciphertext = bytes.copyOfRange(IV_LEN, bytes.size)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            }
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Delete all encrypted crash logs from this device. */
    fun clear(context: Context) {
        File(context.filesDir, CRASH_LOG_DIR).listFiles()?.forEach { it.delete() }
    }

    /**
     * Build a human-readable crash report string.
     */
    fun buildReport(throwable: Throwable, thread: Thread): String = buildString {
        appendLine("=== Paperkeep Crash Report ===")
        appendLine("Time: ${Date()}")
        appendLine("Thread: ${thread.name} (id=${thread.id})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("App: app.paperkeep")
        appendLine()
        appendLine("=== Exception ===")
        appendLine("${throwable.javaClass.name}: ${throwable.message}")
        appendLine()
        appendLine("=== Stack Trace ===")
        appendLine(throwable.stackTraceToString())

        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < 5) {
            appendLine()
            appendLine("=== Caused by ===")
            appendLine("${cause.javaClass.name}: ${cause.message}")
            appendLine(cause.stackTraceToString())
            cause = cause.cause
            depth++
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }.generateKey()

        return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }
}
