package app.paperkeep.core.data.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import app.paperkeep.core.common.DebugLog
import app.paperkeep.core.data.crypto.EncryptedImageStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface EncryptedImageStoreEntryPoint {
    fun encryptedImageStore(): EncryptedImageStore
}

/**
 * Renders an AES-256-GCM encrypted `.enc` image file directly — bypasses
 * Coil. The previous Coil-based path was silently failing in production
 * (Coil's default File fetcher matched first and tried to decode ciphertext
 * as JPEG, returning nothing). This composable does the decrypt + decode
 * itself on [Dispatchers.IO] and renders via [Image].
 *
 * The decoded [Bitmap] is held in composition memory only — when the
 * composable leaves the composition the bitmap is eligible for GC. Coil's
 * memory cache is therefore not used for these files. For a 200-doc library
 * this is fine; for >1000 docs we'd add a bounded LRU here.
 */
@Composable
fun EncryptedImage(
    file: File?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { Box(modifier = modifier) },
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store: EncryptedImageStore? = remember(context) {
        runCatching {
            EntryPointAccessors
                .fromApplication(context.applicationContext, EncryptedImageStoreEntryPoint::class.java)
                .encryptedImageStore()
        }.onFailure {
            // Robolectric / preview / no-Hilt contexts: render placeholder only.
            DebugLog.w("Paperkeep.EncryptedImage", "no Hilt graph: ${it.message}")
        }.getOrNull()
    }

    var bitmap by remember(file?.absolutePath, file?.length()) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(file?.absolutePath, file?.length()) {
        val current = file
        val activeStore = store
        if (current == null || activeStore == null ||
            !current.exists() || current.length() == 0L) {
            DebugLog.w(
                "Paperkeep.EncryptedImage",
                "skip: file=${current?.absolutePath} exists=${current?.exists()} " +
                    "len=${current?.length()} hasStore=${activeStore != null}",
            )
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            try {
                val plain = activeStore.read(current)
                val bmp = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                DebugLog.d(
                    "Paperkeep.EncryptedImage",
                    "decoded ${current.name} cipher=${current.length()}B " +
                        "plain=${plain.size}B " +
                        "${bmp?.width ?: -1}x${bmp?.height ?: -1}",
                )
                bmp
            } catch (e: Exception) {
                DebugLog.e(
                    "Paperkeep.EncryptedImage",
                    "FAILED ${current.absolutePath}",
                    e,
                )
                null
            }
        }
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        placeholder()
    }
}
