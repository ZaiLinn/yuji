package com.yuji.app.data

import androidx.room.withTransaction
import com.yuji.app.data.db.AccountEntity
import com.yuji.app.data.db.BalanceHistoryEntity
import com.yuji.app.data.db.GroupEntity
import com.yuji.app.data.db.RateEntity
import com.yuji.app.data.db.RateSource
import com.yuji.app.data.db.RecurringEntity
import com.yuji.app.data.db.SnapshotEntity
import com.yuji.app.data.db.SnapshotItemEntity
import com.yuji.app.data.db.SnapshotReason
import com.yuji.app.data.db.TransferEntity
import com.yuji.app.data.db.YujiDatabase
import com.yuji.app.data.rates.RateRepository
import com.yuji.app.data.settings.SettingsStore
import com.yuji.app.domain.Currency
import com.yuji.app.domain.Money
import com.yuji.app.domain.Portfolio
import com.yuji.app.domain.Recurrence
import com.yuji.app.domain.SnapshotService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal

data class AccountDraft(
    val id: Long?,
    val groupId: Long,
    val name: String,
    val currency: String,
    val balance: BigDecimal,
    val iconType: String,
    val iconValue: String,
    val note: String,
    val includeInTotal: Boolean,
)

data class RecurringDraft(
    val id: Long?,
    val accountId: Long,
    val name: String,
    val amount: BigDecimal,
    val income: Boolean,
    val period: String,
    val month: Int,
    val day: Int,
    val enabled: Boolean,
)

class YujiRepository(
    private val db: YujiDatabase,
    private val settings: SettingsStore,
    private val rateSource: RateRepository,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val snapshotService = SnapshotService(db, settings, clock)

    val groups: StateFlow<List<GroupEntity>> =
        db.groups().observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val portfolio: StateFlow<Portfolio> =
        combine(db.accounts().observeAll(), db.groups().observeAll(), db.rates().observeAll(), Portfolio::compute)
            .stateIn(scope, SharingStarted.Eagerly, Portfolio.EMPTY)

    val snapshots: StateFlow<List<SnapshotEntity>> =
        db.snapshots().observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val recurring: StateFlow<List<RecurringEntity>> =
        db.recurring().observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun history(accountId: Long): Flow<List<BalanceHistoryEntity>> = db.history().observeFor(accountId)
    fun transfersOf(accountId: Long): Flow<List<TransferEntity>> = db.transfers().observeFor(accountId)
    fun snapshot(id: Long): Flow<SnapshotEntity?> = db.snapshots().observe(id)
    fun snapshotItems(id: Long): Flow<List<SnapshotItemEntity>> = db.snapshots().observeItems(id)
    suspend fun snapshotItemsOnce(id: Long): List<SnapshotItemEntity> = db.snapshots().items(id)

    // ---- accounts ----

    suspend fun saveAccount(d: AccountDraft): Long = db.withTransaction {
        val now = clock()
        val old = d.id?.let { db.accounts().get(it) }
        val valueChanged = old == null ||
            old.balance.compareTo(d.balance) != 0 ||
            old.currency != d.currency ||
            old.includeInTotal != d.includeInTotal
        val id = if (old == null) {
            db.accounts().insert(
                AccountEntity(
                    groupId = d.groupId,
                    name = d.name,
                    currency = d.currency,
                    balance = d.balance,
                    iconType = d.iconType,
                    iconValue = d.iconValue,
                    note = d.note,
                    includeInTotal = d.includeInTotal,
                    sortOrder = db.accounts().nextSort(d.groupId),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            val movedGroup = old.groupId != d.groupId
            db.accounts().update(
                old.copy(
                    groupId = d.groupId,
                    name = d.name,
                    currency = d.currency,
                    balance = d.balance,
                    iconType = d.iconType,
                    iconValue = d.iconValue,
                    note = d.note,
                    includeInTotal = d.includeInTotal,
                    sortOrder = if (movedGroup) db.accounts().nextSort(d.groupId) else old.sortOrder,
                    updatedAt = if (valueChanged) now else old.updatedAt,
                ),
            )
            old.id
        }
        if (old == null || old.balance.compareTo(d.balance) != 0 || old.currency != d.currency) {
            recordHistory(listOf(id to d.balance), d.currency.let { mapOf(id to it) }, now)
        }
        if (valueChanged) snapshotService.capture(SnapshotReason.EDIT)
        id
    }

    suspend fun deleteAccount(id: Long) = db.withTransaction {
        db.recurring().deleteForAccount(id)
        db.accounts().delete(id)
        snapshotService.capture(SnapshotReason.DELETE)
    }

    /**
     * Quick update: every listed account is confirmed (its update time moves to now), changed
     * balances get a history entry, then one snapshot is taken. Returns the number changed.
     */
    suspend fun confirmBalances(values: Map<Long, BigDecimal>): Int = db.withTransaction {
        val now = clock()
        val accounts = db.accounts().getAll().filter { it.id in values }
        val changed = accounts.filter { it.balance.compareTo(values.getValue(it.id)) != 0 }
        db.accounts().updateAll(accounts.map { it.copy(balance = values.getValue(it.id), updatedAt = now) })
        recordHistory(changed.map { it.id to values.getValue(it.id) }, changed.associate { it.id to it.currency }, now)
        snapshotService.capture(SnapshotReason.UPDATE)
        changed.size
    }

    suspend fun transfer(fromId: Long, toId: Long, out: BigDecimal, received: BigDecimal, fee: BigDecimal, note: String) {
        require(fromId != toId) { "转出和转入账户不能相同" }
        db.withTransaction {
            val now = clock()
            val from = db.accounts().get(fromId) ?: error("转出账户不存在")
            val to = db.accounts().get(toId) ?: error("转入账户不存在")
            val newFrom = from.copy(balance = from.balance - out - fee, updatedAt = now)
            val newTo = to.copy(balance = to.balance + received, updatedAt = now)
            db.accounts().updateAll(listOf(newFrom, newTo))
            recordHistory(
                listOf(from.id to newFrom.balance, to.id to newTo.balance),
                mapOf(from.id to from.currency, to.id to to.currency),
                now,
            )
            db.transfers().insert(
                TransferEntity(fromId = fromId, toId = toId, outAmount = out, inAmount = received, fee = fee, note = note, at = now),
            )
            snapshotService.capture(SnapshotReason.TRANSFER)
        }
    }

    private suspend fun recordHistory(entries: List<Pair<Long, BigDecimal>>, currencies: Map<Long, String>, at: Long) {
        if (entries.isEmpty()) return
        val rates = db.rates().getAll().associateBy { it.currency }
        db.history().insert(
            entries.map { (id, balance) ->
                val rate = Portfolio.rateFor(currencies.getValue(id), rates)
                BalanceHistoryEntity(accountId = id, balance = balance, valueCny = rate?.let { Money.toCny(balance, it) }, at = at)
            },
        )
    }

    // ---- fixed income / expenses ----

    suspend fun recurringRule(id: Long): RecurringEntity? = db.recurring().get(id)

    /**
     * A new rule first runs on its next date after today, so it never repeats a change the user
     * already made by hand. Changing the schedule or turning a rule back on also starts from now
     * instead of catching up on skipped dates.
     */
    suspend fun saveRecurring(d: RecurringDraft): Long = db.withTransaction {
        require(d.amount.signum() > 0) { "金额必须大于 0" }
        requireNotNull(db.accounts().get(d.accountId)) { "账户不存在" }
        val now = clock()
        val old = d.id?.let { db.recurring().get(it) }
        val restart = old == null || !old.enabled ||
            old.period != d.period || old.month != d.month || old.day != d.day
        val nextAt = if (restart) Recurrence.nextAfter(d.period, d.month, d.day, now) else old!!.nextAt
        val rule = RecurringEntity(
            id = old?.id ?: 0,
            accountId = d.accountId,
            name = d.name,
            amount = d.amount,
            income = d.income,
            period = d.period,
            month = d.month,
            day = d.day,
            enabled = d.enabled,
            nextAt = nextAt,
            createdAt = old?.createdAt ?: now,
        )
        if (old == null) db.recurring().insert(rule) else { db.recurring().update(rule); old.id }
    }

    suspend fun setRecurringEnabled(id: Long, enabled: Boolean) = db.withTransaction {
        val old = db.recurring().get(id) ?: return@withTransaction
        if (old.enabled == enabled) return@withTransaction
        val nextAt = if (enabled) Recurrence.nextAfter(old, clock()) else old.nextAt
        db.recurring().update(old.copy(enabled = enabled, nextAt = nextAt))
    }

    suspend fun deleteRecurring(id: Long) = db.recurring().delete(id)

    /**
     * Applies every due occurrence, including ones missed while the app was closed. Each one is
     * logged as a one-sided transfer on the account; one snapshot covers the whole run.
     * Returns the number of occurrences applied.
     */
    suspend fun applyRecurring(): Int = db.withTransaction {
        val now = clock()
        val due = db.recurring().due(now)
        if (due.isEmpty()) return@withTransaction 0
        val balances = mutableMapOf<Long, AccountEntity>()
        var applied = 0
        for (rule in due) {
            val account = balances[rule.accountId] ?: db.accounts().get(rule.accountId)
            if (account == null) {
                db.recurring().delete(rule.id)
                continue
            }
            var next = rule.nextAt
            var balance = account.balance
            var runs = 0
            while (next <= now && runs < MAX_CATCH_UP) {
                balance = if (rule.income) balance + rule.amount else balance - rule.amount
                db.transfers().insert(
                    TransferEntity(
                        fromId = if (rule.income) null else account.id,
                        toId = if (rule.income) account.id else null,
                        outAmount = rule.amount,
                        inAmount = rule.amount,
                        fee = BigDecimal.ZERO,
                        note = RECURRING_NOTE_PREFIX + rule.name,
                        at = next,
                    ),
                )
                next = Recurrence.nextAfter(rule, next)
                runs++
            }
            // Past the catch-up limit, skip ahead instead of applying years of changes at once.
            if (next <= now) next = Recurrence.nextAfter(rule, now)
            // updatedAt stays: an automatic change is not the user confirming the real balance.
            balances[account.id] = account.copy(balance = balance)
            db.recurring().update(rule.copy(nextAt = next))
            applied += runs
        }
        if (balances.isNotEmpty()) {
            db.accounts().updateAll(balances.values.toList())
            recordHistory(balances.values.map { it.id to it.balance }, balances.values.associate { it.id to it.currency }, now)
        }
        if (applied > 0) snapshotService.capture(SnapshotReason.RECURRING)
        applied
    }

    // ---- groups & ordering ----

    suspend fun addGroup(name: String): Long =
        db.groups().insert(GroupEntity(name = name, sort = db.groups().nextSort()))

    suspend fun renameGroup(id: Long, name: String) = db.groups().rename(id, name)

    /** Returns false when the group still has accounts. */
    suspend fun deleteGroup(id: Long): Boolean = db.withTransaction {
        if (db.accounts().countInGroup(id) > 0) return@withTransaction false
        db.groups().delete(id)
        true
    }

    suspend fun reorderGroups(ids: List<Long>) {
        val byId = db.groups().getAll().associateBy { it.id }
        db.groups().update(ids.mapIndexedNotNull { i, id -> byId[id]?.copy(sort = i) })
    }

    /** Full layout: for each group, its account ids in display order. */
    suspend fun arrangeAccounts(layout: List<Pair<Long, List<Long>>>) = db.withTransaction {
        val byId = db.accounts().getAll().associateBy { it.id }
        db.accounts().updateAll(
            layout.flatMap { (groupId, ids) ->
                ids.mapIndexedNotNull { i, id -> byId[id]?.copy(groupId = groupId, sortOrder = i) }
            },
        )
    }

    // ---- rates ----

    suspend fun refreshRates(): Result<Int> = runCatching {
        val n = rateSource.refresh(clock())
        if (settings.pending) db.withTransaction { snapshotService.capture(SnapshotReason.RATES_READY) }
        n
    }

    /** null rate returns the currency to automatic updates (keeping the last value until refreshed). */
    suspend fun setManualRate(currency: String, rate: BigDecimal?) {
        if (currency == Currency.BASE) return
        val current = db.rates().getAll().firstOrNull { it.currency == currency }
        if (rate == null) {
            if (current != null) db.rates().upsert(listOf(current.copy(source = RateSource.AUTO)))
        } else {
            db.rates().upsert(listOf(RateEntity(currency, rate, clock(), RateSource.MANUAL)))
            if (settings.pending) db.withTransaction { snapshotService.capture(SnapshotReason.RATES_READY) }
        }
    }

    // ---- snapshots ----

    suspend fun updateSnapshotNote(id: Long, note: String) = db.snapshots().updateNote(id, note)
    suspend fun deleteSnapshot(id: Long) = db.snapshots().delete(id)

    suspend fun captureSnapshot(reason: String) = db.withTransaction { snapshotService.capture(reason) }

    suspend fun mergeSameDaySnapshots(): Int = db.withTransaction { snapshotService.mergeSameDay() }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val groups = db.groups().getAll().associateBy { it.id }
        val accounts = db.accounts().getAll().associateBy { it.id }
        val history = db.history().getAll().sortedWith(compareBy({ it.accountId }, { it.at }))
        buildString {
            appendLine("账户名,分组,货币,余额,人民币折合,记录时间")
            for (h in history) {
                val a = accounts[h.accountId] ?: continue
                val g = groups[a.groupId]?.name ?: "未分组"
                val cny = h.valueCny?.toPlainString() ?: ""
                appendLine("${csvCell(a.name)},${csvCell(g)},${a.currency},${h.balance.toPlainString()},$cny,${csvDate(h.at)}")
            }
        }
    }

    private fun csvCell(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun csvDate(at: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))

    companion object {
        /** Marks transfers written by fixed income / expense rules; the rule name follows. */
        const val RECURRING_NOTE_PREFIX = "定期·"
        private const val MAX_CATCH_UP = 120
    }
}
