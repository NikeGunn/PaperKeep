package com.scanvault.app

import android.app.Application
import com.scanvault.app.crash.ScanVaultCrashHandler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScanVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ScanVaultCrashHandler.install(this)
    }
}
