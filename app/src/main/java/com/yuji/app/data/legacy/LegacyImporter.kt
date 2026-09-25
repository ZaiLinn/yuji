package com.yuji.app.data.legacy

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.yuji.app.data.db.AccountEntity
import com.yuji.app.data.db.BalanceHistoryEntity
import com.yuji.app.data.db.GroupEntity
import com.yuji.app.data.db.IconType
import com.yuji.app.data.db.RateEntity
import com.yuji.app.data.db.RateSource
import com.yuji.app.data.db.SnapshotEntity
import com.yuji.app.data.db.SnapshotItemEntity
import com.yuji.app.data.db.SnapshotReason
import com.yuji.app.data.db.TransferEntity
import com.yuji.app.data.db.YujiDatabase
import com.yuji.app.data.icons.IconRepository
import com.yuji.app.domain.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

typealias Row = Map<String, Any?>

/** Raw rows of the legacy (≤ 9.6) schema, from either the old database file or a v2 backup. */
class LegacyData(
    val tables: Map<String, List<Row>>,
    val goal: String?,
    /** Resolves the bytes of an account icon given the account id and its legacy icon string. */
    val iconBytes: (Long, String) -> ByteArray?,
)

data class ImportSummary(
    val accounts: Int,
    val groups: Int,
    val snapshots: Int,
    val goal: BigDecimal?,
)

/**
 * Maps the legacy schema (yuji_v14.db, schema v5 + runtime extras) onto the new Room schema.
 * Replaces everything currently in [db]. Ids of groups, accounts and snapshots are preserved.
 */
class LegacyImporter(
    private val db: YujiDatabase,
    private val icons: IconRepository,
) {
    suspend fun import(data: LegacyData): ImportSummary {
        val t = data.tables
        val groups = t["groups"].orEmpty().map {
            GroupEntity(id = it.long("id")!!, name = it.str("name").ifBlank { "未命名分组" }, sort = it.int("sort") ?: 0)
        }.toMutableList()
        val groupIds = groups.map { it.id }.toMutableSet()

        val rawAccounts = t["accounts"].orEmpty()
        if (rawAccounts.any { it.long("group_id") !in groupIds }) {
            val fallback = (groupIds.maxOrNull() ?: 0) + 1
            groups += GroupEntity(id = fallback, name = "未分组", sort = groups.size)
            groupIds += fallback
        }
        if (groups.isEmpty()) groups += defaultGroups()

        val now = System.currentTimeMillis()
        val accounts = rawAccounts.map { r ->
            val id = r.long("id")!!
            val (iconType, iconValue) = migrateIcon(id, r.str("icon"), data.iconBytes)
            AccountEntity(
                id = id,
                groupId = r.long("group_id")?.takeIf { it in groupIds } ?: groups.last().id,
                name = r.str("name").ifBlank { "账户" },
                currency = Currency.of(r.str("currency"))?.code ?: r.str("currency").ifBlank { Currency.BASE },
                balance = r.dec("balance") ?: BigDecimal.ZERO,
                iconType = iconType,
                iconValue = iconValue,
                note = r.str("remark"),
                includeInTotal = (r.int("include_asset") ?: 1) == 1,
                sortOrder = r.int("sort_order") ?: 0,
                createdAt = parseTime(r.str("created_time")) ?: now,
                updatedAt = parseTime(r.str("update_time")) ?: parseTime(r.str("created_time")) ?: now,
            )
        }
        val accountIds = accounts.map { it.id }.toSet()

        val rates = t["exchange_rates"].orEmpty().mapNotNull { r ->
            val cur = r.str("from_currency")
            val rate = r.dec("rate")
            if (cur.isBlank() || rate == null || rate.signum() <= 0) null
            else RateEntity(cur, rate, parseTime(r.str("update_time")) ?: 0L, RateSource.AUTO)
        }.filter { it.currency != Currency.BASE } + RateEntity(Currency.BASE, BigDecimal.ONE, now, RateSource.AUTO)

        val history = t["account_snapshots"].orEmpty().mapNotNull { r ->
            val accountId = r.long("account_id") ?: return@mapNotNull null
            if (accountId !in accountIds) return@mapNotNull null
            val balance = r.dec("balance") ?: return@mapNotNull null
            val base = r.dec("base_value")
            val currency = accounts.first { it.id == accountId }.currency
            // Old builds stored 0 when the rate had not been fetched yet; treat that as unknown.
            val value = if (base != null && base.signum() == 0 && balance.signum() != 0 && currency != Currency.BASE) null else base
            BalanceHistoryEntity(accountId = accountId, balance = balance, valueCny = value, at = parseTime(r.str("date")) ?: now)
        }

        val snapshots = t["asset_snapshots"].orEmpty().mapNotNull { r ->
            val id = r.long("id") ?: return@mapNotNull null
            SnapshotEntity(
                id = id,
                createdAt = parseTime(r.str("created_time")) ?: parseTime(r.str("date")) ?: now,
                totalCny = r.dec("total_asset") ?: BigDecimal.ZERO,
                note = r.str("note"),
                reason = SnapshotReason.LEGACY,
            )
        }
        val snapshotIds = snapshots.map { it.id }.toSet()
        val items = t["snapshot_accounts"].orEmpty().mapNotNull { r ->
            val sid = r.long("snapshot_id") ?: return@mapNotNull null
            if (sid !in snapshotIds) return@mapNotNull null
            SnapshotItemEntity(
                snapshotId = sid,
                accountId = r.long("account_id"),
                accountName = r.str("account_name"),
                currency = r.str("currency").ifBlank { Currency.BASE },
                balance = r.dec("balance") ?: BigDecimal.ZERO,
                rate = r.dec("rate") ?: BigDecimal.ZERO,
                valueCny = r.dec("base_value") ?: BigDecimal.ZERO,
            )
        }

        val transfers = t["transfer_records"].orEmpty().map { r ->
            TransferEntity(
                fromId = r.long("from_account_id"),
                toId = r.long("to_account_id"),
                outAmount = r.dec("from_amount") ?: BigDecimal.ZERO,
                inAmount = r.dec("to_amount") ?: BigDecimal.ZERO,
                fee = r.dec("fee") ?: BigDecimal.ZERO,
                note = r.str("remark"),
                at = parseTime(r.str("created_time")) ?: now,
            )
        }

        db.withTransaction {
            db.maintenance().clearAll()
            groups.forEach { db.groups().insert(it) }
            accounts.forEach { db.accounts().insert(it) }
            db.rates().upsert(rates)
            db.history().insert(history)
            snapshots.forEach { db.snapshots().insert(it) }
            db.snapshots().insertItems(items)
            transfers.forEach { db.transfers().insert(it) }
        }

        return ImportSummary(
            accounts = accounts.size,
            groups = groups.size,
            snapshots = snapshots.size,
            goal = data.goal?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
        )
    }

    private suspend fun migrateIcon(accountId: Long, icon: String, bytesOf: (Long, String) -> ByteArray?): Pair<String, String> {
        if (icon.startsWith("emoji:")) return IconType.EMOJI to icon.removePrefix("emoji:")
        if (icon.startsWith("uri:") || icon.startsWith("cache:") || icon.startsWith("svgcache:")) {
            val bytes = runCatching { bytesOf(accountId, icon) }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                val file = runCatching { icons.save(bytes, "legacy") }
                    .onFailure { Log.w(TAG, "icon of account $accountId not migrated", it) }
                    .getOrNull()
                if (file != null) return IconType.IMAGE to file.absolutePath
            }
        }
        return IconType.NONE to ""
    }

    companion object {
        private const val TAG = "LegacyImporter"
        const val LEGACY_DB = "yuji_v14.db"
        const val LEGACY_PREFS = "yuji_settings"
        val TABLES = listOf(
            "groups", "accounts", "exchange_rates", "account_snapshots", "asset_snapshots",
            "snapshot_exchange_rates", "snapshot_accounts", "transfer_records",
        )

        fun defaultGroups() = listOf("充值账户", "资金账户", "投资理财")
            .mapIndexed { i, n -> GroupEntity(id = (i + 1).toLong(), name = n, sort = i) }

        /**
         * Reads the old app's database. Opened read-write only so SQLite can recover a pending WAL;
         * nothing is written to it.
         */
        suspend fun readLegacyDatabase(context: Context, file: File): LegacyData = withContext(Dispatchers.IO) {
            val sqlite = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
            val tables = sqlite.use { d ->
                val existing = d.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(0)) }
                }
                TABLES.filter { it in existing }.associateWith { table ->
                    d.rawQuery("SELECT * FROM $table", null).use { it.rows() }
                }
            }
            val goal = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).getString("asset_goal", null)
            LegacyData(tables, goal) { _, icon -> readLegacyIcon(context, icon) }
        }

        fun readLegacyIcon(context: Context, icon: String): ByteArray? = when {
            icon.startsWith("cache:") -> File(icon.removePrefix("cache:")).takeIf { it.exists() }?.readBytes()
            icon.startsWith("svgcache:") -> File(icon.removePrefix("svgcache:")).takeIf { it.exists() }?.readBytes()
            icon.startsWith("uri:") -> context.contentResolver.openInputStream(Uri.parse(icon.removePrefix("uri:")))?.use { it.readBytes() }
            else -> null
        }

        private fun Cursor.rows(): List<Row> {
            val out = ArrayList<Row>(count)
            val names = columnNames
            while (moveToNext()) {
                val row = HashMap<String, Any?>(names.size)
                for (i in names.indices) {
                    row[names[i]] = when (getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
                        else -> getString(i)
                    }
                }
                out += row
            }
            return out
        }

        private val timeFormats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd")

        fun parseTime(text: String?): Long? {
            if (text.isNullOrBlank()) return null
            for (f in timeFormats) {
                val parsed = runCatching { SimpleDateFormat(f, Locale.US).apply { isLenient = false }.parse(text.trim()) }.getOrNull()
                if (parsed != null) return parsed.time
            }
            return null
        }

        private fun Row.str(key: String): String = this[key]?.toString() ?: ""
        private fun Row.long(key: String): Long? = when (val v = this[key]) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        }
        private fun Row.int(key: String): Int? = long(key)?.toInt()

        /** Doubles go through their shortest decimal form, so 2667.69 stays 2667.69. */
        private fun Row.dec(key: String): BigDecimal? = when (val v = this[key]) {
            is Double -> if (v.isFinite()) BigDecimal(v.toString()) else null
            is Float -> if (v.isFinite()) BigDecimal(v.toString()) else null
            is Number -> BigDecimal(v.toString())
            is String -> v.toBigDecimalOrNull()
            else -> null
        }
    }
}
