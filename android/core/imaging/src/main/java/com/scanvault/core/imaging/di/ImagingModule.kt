package com.scanvault.core.imaging.di

import com.scanvault.core.imaging.EdgeDetector
import com.scanvault.core.imaging.OpenCvEdgeDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImagingModule {

    @Binds
    @Singleton
    abstract fun bindEdgeDetector(impl: OpenCvEdgeDetector): EdgeDetector
}
