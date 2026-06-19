package cc.niaoer.nocall.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.niaoer.nocall.data.model.BlockRule
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules ORDER BY created_at DESC")
    fun getAll(): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE enabled = 1 ORDER BY created_at DESC")
    suspend fun getEnabledList(): List<BlockRule>

    @Query("SELECT * FROM block_rules WHERE id = :id")
    suspend fun getById(id: Long): BlockRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BlockRule): Long

    @Update
    suspend fun update(rule: BlockRule)

    @Delete
    suspend fun delete(rule: BlockRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<BlockRule>)
}
