package com.scanvault.core.data.di

import android.content.Context
import androidx.room.Room
import com.scanvault.core.data.crypto.AesGcmImageStore
import com.scanvault.core.data.crypto.EncryptedImageStore
import com.scanvault.core.data.crypto.KeyProvider
import com.scanvault.core.data.crypto.KeyStoreKeyProvider
import com.scanvault.core.data.db.DocumentDao
import com.scanvault.core.data.db.ScanDao
import com.scanvault.core.data.db.ScanVaultDatabase
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
        fun provideDatabase(@ApplicationContext context: Context): ScanVaultDatabase =
            Room.databaseBuilder(
                context,
                ScanVaultDatabase::class.java,
                "scanvault.db",
            )
                .addMigrations(
                    ScanVaultDatabase.MIGRATION_1_2,
                    ScanVaultDatabase.MIGRATION_2_3,
                    ScanVaultDatabase.MIGRATION_3_4,
                )
                .build()

        @Provides
        fun provideScanDao(db: ScanVaultDatabase): ScanDao = db.scanDao()

        @Provides
        fun provideDocumentDao(db: ScanVaultDatabase): DocumentDao = db.documentDao()
    }
}
