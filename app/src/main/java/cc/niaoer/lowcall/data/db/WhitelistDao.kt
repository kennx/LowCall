package cc.niaoer.lowcall.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.niaoer.lowcall.data.model.WhitelistEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM whitelist ORDER BY created_at DESC")
    fun getAll(): Flow<List<WhitelistEntry>>

    @Query("SELECT EXISTS(SELECT 1 FROM whitelist WHERE normalized_number = :normalized)")
    suspend fun exists(normalized: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: WhitelistEntry): Long

    @Query("DELETE FROM whitelist WHERE id = :id")
    suspend fun deleteById(id: Long)

}
