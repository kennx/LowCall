package cc.niaoer.lowcall.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.niaoer.lowcall.data.model.CallLog
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllOrdered(): Flow<List<CallLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CallLog): Long

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM call_logs WHERE action = 'BLOCKED'")
    suspend fun getTotalBlockedCount(): Int

    @Query("SELECT COUNT(*) FROM call_logs WHERE action = 'BLOCKED' AND timestamp >= :startOfDay")
    suspend fun getBlockedCountSince(startOfDay: Long): Int

    @Query("SELECT * FROM call_logs WHERE action = 'BLOCKED' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentBlocked(limit: Int): List<CallLog>
}
