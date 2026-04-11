package com.scanvault.core.imaging.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ImagingModule
// OpenCV pipeline bindings added in 1B.11
