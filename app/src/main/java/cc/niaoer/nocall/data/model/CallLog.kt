package cc.niaoer.nocall.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CallAction { BLOCKED, ALLOWED }

@Entity(
    tableName = "call_logs",
    indices = [Index(value = ["timestamp"])]
)
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "matched_rule_id") val matchedRuleId: Long? = null,
    @ColumnInfo(name = "matched_rule_pattern") val matchedRulePattern: String? = null,
    @ColumnInfo(name = "action") val action: CallAction,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)
