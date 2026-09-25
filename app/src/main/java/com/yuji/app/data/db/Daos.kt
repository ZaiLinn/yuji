package com.yuji.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY sort, id")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY sort, id")
    suspend fun getAll(): List<GroupEntity>

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(groups: List<GroupEntity>)

    @Query("UPDATE groups SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(sort), -1) + 1 FROM groups")
    suspend fun nextSort(): Int
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE groupId = :groupId")
    suspend fun countInGroup(groupId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM accounts WHERE groupId = :groupId")
    suspend fun nextSort(groupId: Long): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Update
    suspend fun updateAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates")
    fun observeAll(): Flow<List<RateEntity>>

    @Query("SELECT * FROM rates")
    suspend fun getAll(): List<RateEntity>

    @Upsert
    suspend fun upsert(rates: List<RateEntity>)

    @Query("DELETE FROM rates WHERE currency = :currency")
    suspend fun delete(currency: String)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY createdAt, id")
    fun observeAll(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots ORDER BY createdAt, id")
    suspend fun getAll(): List<SnapshotEntity>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    fun observe(id: Long): Flow<SnapshotEntity?>

    @Query("SELECT * FROM snapshots ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latest(): SnapshotEntity?

    @Query("SELECT * FROM snapshot_items WHERE snapshotId = :snapshotId ORDER BY id")
    suspend fun items(snapshotId: Long): List<SnapshotItemEntity>

    @Query("SELECT * FROM snapshot_items WHERE snapshotId = :snapshotId ORDER BY id")
    fun observeItems(snapshotId: Long): Flow<List<SnapshotItemEntity>>

    @Insert
    suspend fun insert(snapshot: SnapshotEntity): Long

    @Insert
    suspend fun insertItems(items: List<SnapshotItemEntity>)

    @Query("UPDATE snapshots SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM snapshots")
    suspend fun count(): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM balance_history WHERE accountId = :accountId ORDER BY at, id")
    fun observeFor(accountId: Long): Flow<List<BalanceHistoryEntity>>

    @Insert
    suspend fun insert(entries: List<BalanceHistoryEntity>)

    @Query("SELECT * FROM balance_history ORDER BY id")
    suspend fun getAll(): List<BalanceHistoryEntity>
}

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: TransferEntity): Long

    @Query("SELECT * FROM transfers ORDER BY at DESC, id DESC")
    suspend fun getAll(): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE fromId = :accountId OR toId = :accountId ORDER BY at DESC, id DESC")
    fun observeFor(accountId: Long): Flow<List<TransferEntity>>
}

@Dao
interface MaintenanceDao {
    @Query("DELETE FROM snapshot_items")
    suspend fun clearSnapshotItems()

    @Query("DELETE FROM snapshots")
    suspend fun clearSnapshots()

    @Query("DELETE FROM balance_history")
    suspend fun clearHistory()

    @Query("DELETE FROM transfers")
    suspend fun clearTransfers()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM groups")
    suspend fun clearGroups()

    @Query("DELETE FROM rates")
    suspend fun clearRates()

    /** Call inside a transaction. */
    suspend fun clearAll() {
        clearSnapshotItems()
        clearSnapshots()
        clearHistory()
        clearTransfers()
        clearAccounts()
        clearGroups()
        clearRates()
    }
}
