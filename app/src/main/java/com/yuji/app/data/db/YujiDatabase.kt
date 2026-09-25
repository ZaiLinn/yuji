package com.yuji.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GroupEntity::class,
        AccountEntity::class,
        RateEntity::class,
        SnapshotEntity::class,
        SnapshotItemEntity::class,
        BalanceHistoryEntity::class,
        TransferEntity::class,
    ],
    version = 1,
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
    abstract fun maintenance(): MaintenanceDao

    companion object {
        const val NAME = "yuji.db"

        fun create(context: Context): YujiDatabase =
            Room.databaseBuilder(context, YujiDatabase::class.java, NAME).build()

        fun inMemory(context: Context): YujiDatabase =
            Room.inMemoryDatabaseBuilder(context, YujiDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
