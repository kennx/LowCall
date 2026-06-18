package cc.niaoer.nocall.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "whitelist",
    indices = [Index(value = ["normalized_number"], unique = true)]
)
data class WhitelistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "normalized_number") val normalizedNumber: String,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
