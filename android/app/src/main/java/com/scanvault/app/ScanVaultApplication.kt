package com.scanvault.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScanVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
