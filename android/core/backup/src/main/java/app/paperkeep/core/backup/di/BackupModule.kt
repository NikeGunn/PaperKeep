package app.paperkeep.core.backup.di

import android.content.Context
import app.paperkeep.core.backup.saf.ContentResolverSafGateway
import app.paperkeep.core.backup.saf.SafBackupGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    fun provideSafGateway(@ApplicationContext context: Context): SafBackupGateway =
        ContentResolverSafGateway(context.contentResolver)
}
