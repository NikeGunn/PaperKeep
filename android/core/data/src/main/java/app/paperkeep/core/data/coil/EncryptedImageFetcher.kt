package app.paperkeep.core.data.coil

import android.graphics.BitmapFactory
import app.paperkeep.core.common.DebugLog
import app.paperkeep.core.data.crypto.EncryptedImageStore
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import java.io.File

/**
 * Coil 3 [Fetcher] that decrypts a `.enc` file before handing bytes to the
 * image decoder. This keeps the decryption logic in `:core:data` (the only
 * layer that owns the key store) and lets Coil handle memory caching,
 * LRU eviction, and request cancellation transparently.
 *
 * Registration: [EncryptedImageFetcherFactory] is installed via a custom
 * [ImageLoader] provided by Hilt (see [DataModule]).
 */
class EncryptedImageFetcher(
    private val file: File,
    private val store: EncryptedImageStore,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        return try {
            val plainBytes = store.read(file)  // AES-256-GCM decryption
            val bitmap = BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size)
                ?: error("Failed to decode bitmap from ${file.name}")
            DebugLog.d(
                "Paperkeep.Coil",
                "decoded ${file.name} cipher=${file.length()}B plain=${plainBytes.size}B " +
                    "${bitmap.width}x${bitmap.height}",
            )
            ImageFetchResult(
                image       = bitmap.asImage(),
                isSampled   = false,
                dataSource  = DataSource.DISK,
            )
        } catch (e: Exception) {
            DebugLog.e(
                "Paperkeep.Coil",
                "FAILED to decode ${file.absolutePath} (exists=${file.exists()} " +
                    "size=${file.length()})",
                e,
            )
            throw e
        }
    }

    class Factory(private val store: EncryptedImageStore) : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Only handle files with the .enc extension produced by AesGcmImageStore
            if (!data.name.endsWith(".enc")) {
                DebugLog.w(
                    "Paperkeep.Coil",
                    "skipping non-.enc file passed to encrypted fetcher: ${data.name}",
                )
                return null
            }
            return EncryptedImageFetcher(data, store)
        }
    }
}
