package app.paperkeep.core.data.di

import android.content.Context
import androidx.room.Room
import app.paperkeep.core.data.crypto.AesGcmImageStore
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.data.crypto.KeyProvider
import app.paperkeep.core.data.crypto.KeyStoreKeyProvider
import app.paperkeep.core.data.db.DocumentDao
import app.paperkeep.core.data.db.ScanDao
import app.paperkeep.core.data.db.PaperkeepDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindKeyProvider(impl: KeyStoreKeyProvider): KeyProvider

    @Binds
    @Singleton
    abstract fun bindEncryptedImageStore(impl: AesGcmImageStore): EncryptedImageStore

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): PaperkeepDatabase =
            Room.databaseBuilder(
                context,
                PaperkeepDatabase::class.java,
                "Paperkeep.db",
            )
                .addMigrations(
                    PaperkeepDatabase.MIGRATION_1_2,
                    PaperkeepDatabase.MIGRATION_2_3,
                    PaperkeepDatabase.MIGRATION_3_4,
                    PaperkeepDatabase.MIGRATION_4_5,
                    PaperkeepDatabase.MIGRATION_5_6,
                )
                .build()

        @Provides
        fun provideScanDao(db: PaperkeepDatabase): ScanDao = db.scanDao()

        @Provides
        fun provideDocumentDao(db: PaperkeepDatabase): DocumentDao = db.documentDao()
    }
}
