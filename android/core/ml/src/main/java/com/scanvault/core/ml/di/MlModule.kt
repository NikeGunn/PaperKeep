package com.scanvault.core.ml.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object MlModule
// ML Kit + TFLite bindings added in Phase 2B (1B.7 / 2B.7)
