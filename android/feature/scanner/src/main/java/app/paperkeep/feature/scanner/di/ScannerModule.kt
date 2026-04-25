package app.paperkeep.feature.scanner.di

import app.paperkeep.feature.scanner.IdCardCaptureMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object ScannerModule {

    @Provides
    @ViewModelScoped
    fun provideIdCardCaptureMode(): IdCardCaptureMode = IdCardCaptureMode()
}
