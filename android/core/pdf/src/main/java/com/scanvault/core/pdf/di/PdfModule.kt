package com.scanvault.core.pdf.di

import com.scanvault.core.pdf.DocumentExporter
import com.scanvault.core.pdf.PdfExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PdfModule {

    @Provides
    @Singleton
    fun providePdfExporter(): PdfExporter = PdfExporter()

    @Provides
    @Singleton
    fun provideDocumentExporter(): DocumentExporter = DocumentExporter()
}
