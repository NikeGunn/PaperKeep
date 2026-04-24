package app.paperkeep.core.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.data.db.DocumentDao
import app.paperkeep.core.data.db.DocumentWithPages
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.spec.KeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

private const val PBKDF2_ITERATIONS = 120_000
private const val KEY_LENGTH_BITS = 256
private const val MANIFEST_FILE = "manifest.json"
private const val BACKUP_PREFIX = "Paperkeep_Backup_"

data class BackupResult(
    val file: File,
    val documentCount: Int,
    val pageCount: Int,
)

data class RestoreResult(
    val documentCount: Int,
    val pageCount: Int,
)

/**
 * Creates and restores encrypted ZIP backups of all local documents.
 *
 * Backup format: AES-256-CBC encrypted outer ZIP containing:
 *   - manifest.json  (document metadata list)
 *   - pages/0001_p0.jpg, pages/0001_p1.jpg, ...  (decrypted page images)
 *
 * The password is supplied by the user; a random salt is derived per backup.
 * Salt (16 bytes) + IV (16 bytes) are written as an unencrypted header.
 */
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    private val imageStore: EncryptedImageStore,
) {

    suspend fun createBackup(password: CharArray): BackupResult = withContext(Dispatchers.IO) {
        val documents = documentDao.observeAllWithPages().first()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val backupFile = File(context.getExternalFilesDir("backups"), "$BACKUP_PREFIX$timestamp.svbak")
        backupFile.parentFile?.mkdirs()

        val salt = generateSalt()
        val secretKey = deriveKey(password, salt)

        var pageCount = 0
        backupFile.outputStream().use { fos ->
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            fos.write(salt)
            fos.write(cipher.iv)
            CipherOutputStream(fos, cipher).use { cos ->
                ZipOutputStream(cos).use { zos ->
                    val manifest = buildManifest(documents)
                    zos.putNextEntry(ZipEntry(MANIFEST_FILE))
                    zos.write(manifest.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    documents.forEachIndexed { docIdx, docWithPages ->
                        docWithPages.pages.forEachIndexed { pageIdx, page ->
                            try {
                                val imageBytes = imageStore.read(File(context.filesDir, page.encryptedImagePath))
                                val entryName = "pages/${"%04d".format(docIdx)}_p$pageIdx.jpg"
                                zos.putNextEntry(ZipEntry(entryName))
                                zos.write(imageBytes)
                                zos.closeEntry()
                                pageCount++
                            } catch (_: Exception) {
                                // Skip pages that can't be decrypted (corrupted)
                            }
                        }
                    }
                }
            }
        }

        BackupResult(
            file = backupFile,
            documentCount = documents.size,
            pageCount = pageCount,
        )
    }

    suspend fun restoreBackup(backupUri: Uri, password: CharArray): RestoreResult = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(backupUri)
            ?: throw IllegalArgumentException("Cannot open backup file")

        stream.use { fis ->
            val salt = ByteArray(16).also { fis.read(it) }
            val iv = ByteArray(16).also { fis.read(it) }
            val secretKey = deriveKey(password, salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

            var docCount = 0
            var pageCount = 0

            CipherInputStream(fis, cipher).use { cis ->
                ZipInputStream(cis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == MANIFEST_FILE -> {
                                val text = zis.readBytes().toString(Charsets.UTF_8)
                                docCount = text.lines().count { it.contains("\"id\"") }
                            }
                            entry.name.startsWith("pages/") -> {
                                val imageBytes = zis.readBytes()
                                if (imageBytes.isNotEmpty()) {
                                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                    if (bitmap != null) {
                                        val destPath = "restored_${entry.name.replace("/", "_")}"
                                        imageStore.write(File(context.filesDir, destPath), imageBytes)
                                        pageCount++
                                    }
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            RestoreResult(documentCount = docCount, pageCount = pageCount)
        }
    }

    private fun buildManifest(documents: List<DocumentWithPages>): String {
        return buildString {
            append("[")
            documents.forEachIndexed { i, doc ->
                if (i > 0) append(",")
                append("""{"id":"${doc.document.id}","title":"${doc.document.title}","pageCount":${doc.pages.size}}""")
            }
            append("]")
        }
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        return salt
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
