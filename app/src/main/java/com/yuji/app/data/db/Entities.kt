package com.yuji.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int = 0,
)

object IconType {
    const val NONE = "none"
    const val EMOJI = "emoji"
    const val IMAGE = "image"
}

@Entity(tableName = "accounts", indices = [Index("groupId")])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val currency: String,
    val balance: BigDecimal,
    val iconType: String = IconType.NONE,
    /** Emoji text, or absolute path of a PNG under files/icons. */
    val iconValue: String = "",
    val note: String = "",
    val includeInTotal: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

object RateSource {
    const val AUTO = "auto"
    const val MANUAL = "manual"
}

@Entity(tableName = "rates")
data class RateEntity(
    @PrimaryKey val currency: String,
    val rateToCny: BigDecimal,
    val updatedAt: Long,
    val source: String = RateSource.AUTO,
)

object SnapshotReason {
    const val UPDATE = "update"
    const val EDIT = "edit"
    const val TRANSFER = "transfer"
    const val DELETE = "delete"
    const val IMPORT = "import"
    const val RATES_READY = "rates"
    const val RECURRING = "recurring"
}

@Entity(tableName = "snapshots", indices = [Index("createdAt")])
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val totalCny: BigDecimal,
    val note: String = "",
    val reason: String,
)

@Entity(
    tableName = "snapshot_items",
    indices = [Index("snapshotId"), Index("accountId")],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SnapshotItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snapshotId: Long,
    /** Not a foreign key: snapshots keep describing accounts that were later deleted. */
    val accountId: Long?,
    val accountName: String,
    val currency: String,
    val balance: BigDecimal,
    val rate: BigDecimal,
    val valueCny: BigDecimal,
)

@Entity(
    tableName = "balance_history",
    indices = [Index("accountId")],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BalanceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val balance: BigDecimal,
    @ColumnInfo(name = "valueCny") val valueCny: BigDecimal?,
    val at: Long,
)

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long?,
    val toId: Long?,
    val outAmount: BigDecimal,
    val inAmount: BigDecimal,
    val fee: BigDecimal,
    val note: String = "",
    val at: Long,
)

object RecurringPeriod {
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
}

/**
 * A fixed income or expense that changes one account's balance on a schedule.
 * Not a foreign key, so the table stays simple; rules are removed together with their account.
 */
@Entity(tableName = "recurring", indices = [Index("accountId")])
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val name: String,
    /** Always positive, in the account's currency; [income] decides the direction. */
    val amount: BigDecimal,
    val income: Boolean,
    val period: String,
    /** 1–12, used only by yearly rules. */
    val month: Int,
    /** 1–31; months that are shorter use their last day. */
    val day: Int,
    val enabled: Boolean = true,
    /** Start of the day of the next occurrence that has not been applied yet. */
    val nextAt: Long,
    val createdAt: Long,
)
