package com.yuji.app.domain

import androidx.annotation.WorkerThread
import com.yuji.app.data.db.SnapshotEntity
import com.yuji.app.data.db.SnapshotItemEntity
import com.yuji.app.data.db.YujiDatabase
import java.math.BigDecimal

/** Remembers that a snapshot was skipped because a rate was missing. */
interface PendingSnapshotFlag {
    var pending: Boolean
}

/**
 * The only place that writes asset snapshots. Call it inside the same transaction as the
 * change that caused it.
 */
class SnapshotService(
    private val db: YujiDatabase,
    private val flag: PendingSnapshotFlag,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data class Created(val id: Long) : Result
        data object Unchanged : Result
        data class WaitingForRates(val currencies: Set<String>) : Result
    }

    @WorkerThread
    suspend fun capture(reason: String): Result {
        val portfolio = Portfolio.compute(db.accounts().getAll(), emptyList(), db.rates().getAll())
        if (portfolio.missingRates.isNotEmpty()) {
            flag.pending = true
            return Result.WaitingForRates(portfolio.missingRates)
        }
        val items = portfolio.accounts
            .filter { it.account.includeInTotal }
            .map {
                SnapshotItemEntity(
                    snapshotId = 0,
                    accountId = it.account.id,
                    accountName = it.account.name,
                    currency = it.account.currency,
                    balance = it.account.balance,
                    rate = it.rate!!,
                    valueCny = it.valueCny!!,
                )
            }
        val latest = db.snapshots().latest()
        if (latest != null &&
            signature(latest.totalCny, db.snapshots().items(latest.id)) == signature(portfolio.total, items)
        ) {
            flag.pending = false
            return Result.Unchanged
        }
        val id = db.snapshots().insert(
            SnapshotEntity(createdAt = clock(), totalCny = portfolio.total, reason = reason),
        )
        db.snapshots().insertItems(items.map { it.copy(snapshotId = id) })
        flag.pending = false
        return Result.Created(id)
    }

    private fun signature(total: BigDecimal, items: List<SnapshotItemEntity>): String = buildString {
        append(key(total)).append(';')
        items.sortedBy { it.accountId ?: -1 }.forEach {
            append(it.accountId).append('|').append(it.accountName).append('|').append(it.currency)
                .append('|').append(key(it.balance)).append('|').append(key(it.rate)).append(';')
        }
    }

    private fun key(v: BigDecimal): String = v.stripTrailingZeros().toPlainString()
}
