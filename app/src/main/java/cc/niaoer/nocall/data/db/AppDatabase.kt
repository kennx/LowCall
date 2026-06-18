package cc.niaoer.nocall.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.CallLog
import cc.niaoer.nocall.data.model.WhitelistEntry

@Database(
    entities = [BlockRule::class, CallLog::class, WhitelistEntry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun callLogDao(): CallLogDao
    abstract fun whitelistDao(): WhitelistDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `whitelist` (
                        `id` INTEGER NOT NULL,
                        `phone_number` TEXT NOT NULL,
                        `normalized_number` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_whitelist_normalized_number` ON `whitelist` (`normalized_number`)"
                )
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "nocall.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
