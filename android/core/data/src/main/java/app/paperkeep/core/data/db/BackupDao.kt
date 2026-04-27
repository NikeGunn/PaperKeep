package app.paperkeep.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(backup: BackupEntity)

    @Query("SELECT * FROM backups ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BackupEntity>>

    @Query("SELECT * FROM backups WHERE id = :id")
    suspend fun getById(id: String): BackupEntity?

    @Query("SELECT * FROM backups ORDER BY createdAt DESC LIMIT 1")
    suspend fun getMostRecent(): BackupEntity?

    @Query("DELETE FROM backups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM backups")
    suspend fun count(): Int
}
