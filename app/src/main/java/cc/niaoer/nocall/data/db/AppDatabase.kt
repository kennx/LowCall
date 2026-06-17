package cc.niaoer.nocall.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.CallLog

@Database(
    entities = [BlockRule::class, CallLog::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "nocall.db"
            ).build()
        }
    }
}
