package com.scanvault.core.network.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule
// Ktor client + E2E crypto bindings added in Phase 4B (4B.2 / 4B.9)
