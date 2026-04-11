package com.scanvault.core.data.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DataModule
// Room DB, DataStore, and EncryptedFile bindings added in 1B.15–1B.16
