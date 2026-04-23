package app.paperkeep

import android.app.Application
import app.paperkeep.crash.PaperkeepCrashHandler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaperkeepApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PaperkeepCrashHandler.install(this)
    }
}
