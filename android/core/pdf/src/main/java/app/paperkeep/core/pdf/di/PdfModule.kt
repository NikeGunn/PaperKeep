package app.paperkeep.core.pdf.di

import app.paperkeep.core.pdf.DocumentExporter
import app.paperkeep.core.pdf.PdfExporter
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
