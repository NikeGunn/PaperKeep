package com.scanvault.core.pdf.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PdfModule
// PdfDocument + PDFBox bindings added in 2B.8
