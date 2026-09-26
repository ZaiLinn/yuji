package com.yuji.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GroupEntity::class,
        AccountEntity::class,
        RateEntity::class,
        SnapshotEntity::class,
        SnapshotItemEntity::class,
        BalanceHistoryEntity::class,
        TransferEntity::class,
        RecurringEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class YujiDatabase : RoomDatabase() {
    abstract fun groups(): GroupDao
    abstract fun accounts(): AccountDao
    abstract fun rates(): RateDao
    abstract fun snapshots(): SnapshotDao
    abstract fun history(): HistoryDao
    abstract fun transfers(): TransferDao
    abstract fun recurring(): RecurringDao
    abstract fun maintenance(): MaintenanceDao

    companion object {
        const val NAME = "yuji.db"

        fun create(context: Context): YujiDatabase =
            Room.databaseBuilder(context, YujiDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()

        fun inMemory(context: Context): YujiDatabase =
            Room.inMemoryDatabaseBuilder(context, YujiDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        /** v2: fixed income / expense rules. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, `name` TEXT NOT NULL, `amount` TEXT NOT NULL, `income` INTEGER NOT NULL, " +
                        "`period` TEXT NOT NULL, `month` INTEGER NOT NULL, `day` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`nextAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_accountId` ON `recurring` (`accountId`)")
            }
        }
    }
}
