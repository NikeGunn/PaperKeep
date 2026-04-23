package app.paperkeep.core.imaging.di

import app.paperkeep.core.imaging.EdgeDetector
import app.paperkeep.core.imaging.OpenCvEdgeDetector
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
