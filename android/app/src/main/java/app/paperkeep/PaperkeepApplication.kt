package app.paperkeep

import android.app.Application
import app.paperkeep.core.common.DebugLog
import app.paperkeep.crash.PaperkeepCrashHandler
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PaperkeepApplication : Application(), SingletonImageLoader.Factory {

    // Hilt injects the ImageLoader built in DataModule — it has the
    // EncryptedImageFetcher registered for .enc files.
    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        SingletonImageLoader.setSafe(this)
        PaperkeepCrashHandler.install(this)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader = imageLoader
}
