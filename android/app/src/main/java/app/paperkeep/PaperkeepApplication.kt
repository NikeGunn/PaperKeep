package app.paperkeep

import android.app.Application
import app.paperkeep.core.common.DebugLog
import app.paperkeep.core.imaging.OpenCvEdgeDetector
import app.paperkeep.crash.PaperkeepCrashHandler
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader
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
        initOpenCv()
    }

    /**
     * Initialise OpenCV's bundled native libs. When this succeeds the Magic
     * Scan pipeline switches from the pure-Kotlin fallback to real
     * findContours / warpPerspective / CLAHE.
     *
     * Failure is silently swallowed — the pure-Kotlin fallback still gives a
     * usable (if lower-quality) capture experience.
     */
    private fun initOpenCv() {
        try {
            if (OpenCVLoader.initLocal()) {
                OpenCvEdgeDetector.markLoaded()
                DebugLog.d("Paperkeep.OpenCV", "OpenCV native libs loaded")
            } else {
                DebugLog.d("Paperkeep.OpenCV", "OpenCV initLocal returned false — using Kotlin fallback")
            }
        } catch (t: Throwable) {
            DebugLog.e("Paperkeep.OpenCV", "OpenCV load failed", t)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader = imageLoader
}
