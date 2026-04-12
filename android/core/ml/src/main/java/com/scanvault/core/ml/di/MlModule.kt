package com.scanvault.core.ml.di

import com.scanvault.core.ml.MlKitOcrEngine
import com.scanvault.core.ml.OcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: MlKitOcrEngine): OcrEngine
}
