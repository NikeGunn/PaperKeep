package app.paperkeep.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.paperkeep.core.data.coil.EncryptedImageFetcher
import app.paperkeep.core.data.crypto.AesGcmImageStore
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.data.crypto.KeyProvider
import app.paperkeep.core.data.crypto.KeyStoreKeyProvider
import app.paperkeep.core.data.db.DocumentDao
import app.paperkeep.core.data.db.ScanDao
import app.paperkeep.core.data.db.PaperkeepDatabase
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
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
                    PaperkeepDatabase.MIGRATION_6_7,
                )
                // FTS virtual tables are not in @Database(entities=[]) so Room does not
                // create them on a fresh install (only migrations run for existing DBs).
                // This callback ensures the FTS tables always exist after onCreate.
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // FTS4 reserves 'docid' — use explicit CREATE (no IF NOT EXISTS)
                        // since onCreate only fires for brand-new databases.
                        db.execSQL(
                            "CREATE VIRTUAL TABLE documents_fts USING fts4(docId, title, ocrText)"
                        )
                        db.execSQL(
                            "CREATE VIRTUAL TABLE page_ocr_fts USING fts4(pageId, tokens)"
                        )
                    }
                })
                .build()

        @Provides
        fun provideScanDao(db: PaperkeepDatabase): ScanDao = db.scanDao()

        @Provides
        fun provideDocumentDao(db: PaperkeepDatabase): DocumentDao = db.documentDao()

        /**
         * Provides the app-wide [ImageLoader] with the [EncryptedImageFetcher]
         * registered for `.enc` files. All Coil [AsyncImage] calls in the app
         * use this loader automatically when set as the singleton via
         * [coil3.SingletonImageLoader].
         */
        @Provides
        @Singleton
        fun provideImageLoader(
            @ApplicationContext context: Context,
            imageStore: EncryptedImageStore,
        ): ImageLoader = ImageLoader.Builder(context)
            .components {
                add(EncryptedImageFetcher.Factory(imageStore))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                // Thumbnails are encrypted originals — no unencrypted disk cache
                null
            }
            .build()
    }
}
