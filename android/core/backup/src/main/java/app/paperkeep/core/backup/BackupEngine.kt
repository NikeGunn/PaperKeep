package app.paperkeep.core.backup

import app.paperkeep.core.backup.crypto.BackupCipher
import app.paperkeep.core.backup.crypto.BackupKdf
import app.paperkeep.core.backup.format.BackupHeader
import app.paperkeep.core.backup.format.BackupManifest
import app.paperkeep.core.backup.format.BackupSettings
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure I/O backup engine — the only crypto-aware code path the user can
 * exfiltrate data through. Tested on JVM with Argon2id-jvm.
 *
 * Layout:
 *
 * ```
 *   header (62 B)           — see BackupHeader
 *   ciphertext              — AES-256-GCM(K, iv) encrypting:
 *     ZIP {
 *       manifest.json       — BackupManifest (plain JSON)
 *       paperkeep.db        — Room database snapshot bytes
 *       settings.json       — BackupSettings
 *       pages/<docId>/<pageIndex>.jpg  — plaintext JPEG bytes (one per page)
 *     }
 * ```
 *
 * Choosing ZIP-inside-GCM (rather than ZIP-with-AES entries):
 *   - GCM provides whole-stream authenticity in one tag — tampering anywhere in
 *     the ciphertext fails the doFinal() and we never expose plaintext.
 *   - Standard ZIP encryption (Zip4j AES) is per-entry; an attacker who flips a
 *     bit in entry N can leave entries 1..N-1 intact, weaker for our model.
 *
 * Choosing inner ZIP-stored (no compression) by default:
 *   - JPEG/AES ciphertext is already incompressible.
 *   - DB bytes ARE compressible — for those we set DEFLATED per-entry.
 */
@Singleton
class BackupEngine @Inject constructor() {

    private val secureRandom = SecureRandom()

    // ── WRITE ────────────────────────────────────────────────────────────────

    fun write(
        input: BackupInput,
        sink: OutputStream,
        memoryKib: Int = BackupKdf.MEMORY_KIB,
        iterations: Int = BackupKdf.ITERATIONS,
        parallelism: Int = BackupKdf.PARALLELISM,
        password: CharArray,
    ): BackupOutput {
        require(password.isNotEmpty()) { "password must not be empty" }

        val salt = ByteArray(BackupKdf.SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = BackupCipher.newIv()
        val header = BackupHeader.new(memoryKib, iterations, parallelism, salt, iv)

        // Tee everything (header + ciphertext) into a SHA-256 digester so we
        // can store the file's hash without re-reading the SAF URI.
        val digest = MessageDigest.getInstance("SHA-256")
        val hashing = HashingOutputStream(sink, digest)

        var bytesWritten = 0L
        val countingSink = CountingOutputStream(hashing) { bytesWritten = it }

        // ── header (plain) ──
        header.writeTo(countingSink)

        // ── derive key & wrap with GCM ──
        val key = BackupKdf.deriveKey(password, salt, memoryKib, iterations, parallelism)

        var docCount = 0
        var pageCount = 0
        var schemaVersion = MANIFEST_SCHEMA_VERSION

        BackupCipher.encryptingStream(key, iv, countingSink).use { gcmOut ->
            ZipOutputStream(gcmOut).use { zip ->
                // 1. manifest goes first (deflate — small, but text compresses)
                val pages = mutableListOf<BackupManifest.PageRef>()
                for (doc in input.documents) {
                    docCount++
                    for (page in doc.pages) {
                        val src = input.resolveEncryptedFile(page.encryptedImagePath)
                        val plain = runCatching { input.decryptPage(src) }.getOrNull() ?: continue
                        val sha = sha256Hex(plain)
                        val zipPath = "pages/${doc.document.id}/${page.pageIndex}.jpg"
                        pages += BackupManifest.PageRef(
                            zipPath = zipPath,
                            documentId = doc.document.id,
                            pageId = page.id,
                            pageIndex = page.pageIndex,
                            sha256 = sha,
                            sizeBytes = plain.size.toLong(),
                        )
                        // we'll write the page entry below in a second pass
                    }
                }
                pageCount = pages.size

                val manifest = BackupManifest(
                    schemaVersion = schemaVersion,
                    createdAtMs = System.currentTimeMillis(),
                    appVersionName = input.appVersionName,
                    appVersionCode = input.appVersionCode,
                    documentCount = docCount,
                    pageCount = pageCount,
                    pages = pages,
                )
                writeDeflatedEntry(
                    zip,
                    BackupManifest.MANIFEST_FILENAME,
                    BackupManifest.encode(manifest).toByteArray(Charsets.UTF_8),
                )

                // 2. db bytes — compressible
                writeDeflatedEntry(zip, BackupManifest.DB_FILENAME, input.dbBytes)

                // 3. settings — compressible
                writeDeflatedEntry(
                    zip,
                    BackupManifest.SETTINGS_FILENAME,
                    BackupSettings.encode(input.settings).toByteArray(Charsets.UTF_8),
                )

                // 4. pages — JPEG bytes, store-only (incompressible)
                for (ref in pages) {
                    val src = input.resolveEncryptedFile(
                        input.documents
                            .first { it.document.id == ref.documentId }
                            .pages.first { it.id == ref.pageId }
                            .encryptedImagePath,
                    )
                    val plain = runCatching { input.decryptPage(src) }.getOrNull() ?: continue
                    writeStoredEntry(zip, ref.zipPath, plain)
                }
            }
        }

        return BackupOutput(
            bytesWritten = bytesWritten,
            sha256Hex = digest.digest().toHexString(),
            documentCount = docCount,
            pageCount = pageCount,
            schemaVersion = schemaVersion,
        )
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Pure read pass. Returns the decoded [BackupManifest] and a callback the
     * caller drives to stream each page into their own destination (typically
     * the new K_master-encrypted store after restore).
     *
     * Streams are positioned after [BackupHeader.FIXED_SIZE] when this returns —
     * caller MUST NOT advance them externally.
     */
    fun read(
        source: InputStream,
        password: CharArray,
        consume: BackupReadVisitor,
    ) {
        val header = BackupHeader.readFrom(source)
        val key = BackupKdf.deriveKey(
            password,
            header.salt,
            header.memoryKib,
            header.iterations,
            header.parallelism,
        )
        BackupCipher.decryptingStream(key, header.iv, source).use { gcmIn ->
            ZipInputStream(gcmIn).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val bytes = zip.readBytes()
                    when (entry.name) {
                        BackupManifest.MANIFEST_FILENAME ->
                            consume.onManifest(BackupManifest.decode(bytes.toString(Charsets.UTF_8)))

                        BackupManifest.DB_FILENAME ->
                            consume.onDatabase(bytes)

                        BackupManifest.SETTINGS_FILENAME ->
                            consume.onSettings(BackupSettings.decode(bytes.toString(Charsets.UTF_8)))

                        else -> if (entry.name.startsWith("pages/")) {
                            consume.onPage(entry.name, bytes)
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun writeDeflatedEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
            time = 0L
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeStoredEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val crc = java.util.zip.CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
            time = 0L
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    companion object {
        const val MANIFEST_SCHEMA_VERSION: Int = 1
    }
}

interface BackupReadVisitor {
    fun onManifest(manifest: BackupManifest)
    fun onDatabase(bytes: ByteArray)
    fun onSettings(settings: BackupSettings)
    fun onPage(zipPath: String, plaintextJpeg: ByteArray)
}

/** Counts written bytes and reports them via [onCount] after each flush/close. */
private class CountingOutputStream(
    private val delegate: OutputStream,
    private val onCount: (Long) -> Unit,
) : OutputStream() {
    private var count: Long = 0
    override fun write(b: Int) { delegate.write(b); count++; onCount(count) }
    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len); count += len; onCount(count)
    }
    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}

/** Tees writes through a [MessageDigest] without buffering. */
private class HashingOutputStream(
    private val delegate: OutputStream,
    private val digest: MessageDigest,
) : OutputStream() {
    override fun write(b: Int) {
        digest.update(b.toByte()); delegate.write(b)
    }
    override fun write(b: ByteArray, off: Int, len: Int) {
        digest.update(b, off, len); delegate.write(b, off, len)
    }
    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}
