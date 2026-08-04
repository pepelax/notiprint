package ru.notiprint.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PrintJob::class, BlockedSender::class], version = 3, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun printJobDao(): PrintJobDao
    abstract fun blockedSenderDao(): BlockedSenderDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_senders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `normalized` TEXT NOT NULL,
                        `label` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_blocked_senders_normalized` ON `blocked_senders` (`normalized`)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                insertDefaultBlockedSender(database)
            }
        }

        private val populateDefaultBlockedSender = object : RoomDatabase.Callback() {
            override fun onCreate(database: SupportSQLiteDatabase) {
                insertDefaultBlockedSender(database)
            }
        }

        private fun insertDefaultBlockedSender(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                INSERT OR IGNORE INTO `blocked_senders` (`normalized`, `label`)
                VALUES ('${SenderIdentifier.DEFAULT_BLOCKED_SENDER_NORMALIZED}', '${SenderIdentifier.DEFAULT_BLOCKED_SENDER_LABEL}')
                """.trimIndent(),
            )
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "notiprint.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(populateDefaultBlockedSender)
                .build()
                .also { instance = it }
        }
    }
}
