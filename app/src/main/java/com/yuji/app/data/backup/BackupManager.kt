package com.yuji.app.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.yuji.app.data.db.AccountEntity
import com.yuji.app.data.db.BalanceHistoryEntity
import com.yuji.app.data.db.GroupEntity
import com.yuji.app.data.db.IconType
import com.yuji.app.data.db.RateEntity
import com.yuji.app.data.db.SnapshotEntity
import com.yuji.app.data.db.SnapshotItemEntity
import com.yuji.app.data.db.TransferEntity
import com.yuji.app.data.db.YujiDatabase
import com.yuji.app.data.icons.IconRepository
import com.yuji.app.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a backup file contains, shown to the user before anything is overwritten. */
class BackupPreview internal constructor(
    val version: Int,
    val createdAt: String,
    val accounts: Int,
    val snapshots: Int,
    /** false when the stored checksum does not match the content. */
    val checksumOk: Boolean,
    internal val apply: suspend () -> Unit,
)

/**
 * `.yuji` backups, format v3.
 * The SHA-256 checksum detects corruption; it is not a signature and cannot prove authorship.
 */
class BackupManager(
    private val context: Context,
    private val db: YujiDatabase,
    private val settings: SettingsStore,
    private val icons: IconRepository,
) {
    fun suggestedFileName(): String =
        "yuji-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".yuji"

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val bytes = buildBackup().toString().toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw IOException("无法创建备份文件")
        settings.setLastBackupAt(System.currentTimeMillis())
    }

    suspend fun inspect(uri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_SIZE) throw IOException("备份文件过大")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: throw IOException("无法读取备份文件")
        val text = bytes.toString(Charsets.UTF_8)
        val version = runCatching { JSONObject(text) }.getOrNull()
            ?.takeIf { it.optString("format") == FORMAT }
            ?.optInt("version", 0)
            ?: throw IOException("这不是余记备份文件")
        when (version) {
            3 -> inspectV3(text)
            2 -> throw IOException("旧版余记（9.x）的备份已不再支持")
            else -> throw IOException("不支持的备份版本：$version")
        }
    }

    suspend fun apply(preview: BackupPreview) = preview.apply()

    // ---------------- v3 ----------------

    private suspend fun buildBackup(): JsonObject {
        val accounts = db.accounts().getAll()
        val allSnapshots = db.snapshots().getAll()
        val payload = buildJsonObject {
            put("format", FORMAT)
            put("version", 3)
            put("created_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            put("settings", buildJsonObject { settings.settings.value.goal?.let { put("goal", it.toPlainString()) } })
            put(
                "data",
                buildJsonObject {
                    put("groups", JsonArray(db.groups().getAll().map(::groupJson)))
                    put("accounts", JsonArray(accounts.map(::accountJson)))
                    put("rates", JsonArray(db.rates().getAll().map(::rateJson)))
                    put("snapshots", JsonArray(allSnapshots.map(::snapshotJson)))
                    put("snapshot_items", JsonArray(allSnapshots.flatMap { db.snapshots().items(it.id) }.map(::itemJson)))
                    put("balance_history", JsonArray(db.history().getAll().map(::historyJson)))
                    put("transfers", JsonArray(db.transfers().getAll().map(::transferJson)))
                },
            )
            put(
                "icons",
                buildJsonObject {
                    for (a in accounts) {
                        if (a.iconType != IconType.IMAGE) continue
                        val png = icons.pngBytes(File(a.iconValue)) ?: continue
                        put(a.id.toString(), Base64.encodeToString(png, Base64.NO_WRAP))
                    }
                },
            )
        }
        return JsonObject(
            payload + ("integrity" to buildJsonObject {
                put("algorithm", "SHA-256")
                put("checksum", sha256(canonical(payload)))
            }),
        )
    }

    private suspend fun inspectV3(text: String): BackupPreview {
        val root = Json.parseToJsonElement(text).jsonObject
        val integrity = root["integrity"]?.jsonObject
        val payload = JsonObject(root - "integrity")
        val checksumOk = integrity?.get("checksum")?.jsonPrimitive?.contentOrNull == sha256(canonical(payload))
        val data = root["data"]?.jsonObject ?: throw IOException("备份缺少数据")
        fun arr(name: String) = data[name]?.jsonArray?.map { it.jsonObject } ?: throw IOException("备份数据不完整：$name")

        val groups = arr("groups").map(::groupOf)
        val accounts = arr("accounts").map(::accountOf)
        val rates = arr("rates").map(::rateOf)
        val snapshots = arr("snapshots").map(::snapshotOf)
        val items = arr("snapshot_items").map(::itemOf)
        val history = arr("balance_history").map(::historyOf)
        val transfers = arr("transfers").map(::transferOf)
        val iconData = root["icons"]?.jsonObject.orEmpty()
        val goal = root["settings"]?.jsonObject?.get("goal")?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()

        return BackupPreview(
            version = 3,
            createdAt = root["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            accounts = accounts.size,
            snapshots = snapshots.size,
            checksumOk = checksumOk,
        ) {
            val iconFiles = accounts.associate { a ->
                val b64 = iconData[a.id.toString()]?.jsonPrimitive?.contentOrNull
                val file = b64?.let { runCatching { icons.save(Base64.decode(it, Base64.DEFAULT), "backup") }.getOrNull() }
                a.id to file
            }
            val restored = accounts.map { a ->
                val f = iconFiles[a.id]
                when {
                    a.iconType != IconType.IMAGE -> a
                    f != null -> a.copy(iconValue = f.absolutePath)
                    else -> a.copy(iconType = IconType.NONE, iconValue = "")
                }
            }
            db.withTransaction {
                db.maintenance().clearAll()
                groups.forEach { db.groups().insert(it) }
                restored.forEach { db.accounts().insert(it) }
                db.rates().upsert(rates)
                snapshots.forEach { db.snapshots().insert(it) }
                db.snapshots().insertItems(items)
                db.history().insert(history.filter { h -> restored.any { it.id == h.accountId } })
                transfers.forEach { db.transfers().insert(it) }
            }
            settings.setGoal(goal)
        }
    }

    // ---------------- JSON mapping ----------------

    private fun canonical(e: JsonElement): String = when (e) {
        is JsonObject -> e.keys.sorted().joinToString(",", "{", "}") { JsonPrimitive(it).toString() + ":" + canonical(e.getValue(it)) }
        is JsonArray -> e.joinToString(",", "[", "]") { canonical(it) }
        else -> e.toString()
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun dec(v: BigDecimal?) = if (v == null) JsonNull else JsonPrimitive(v.toPlainString())
    private fun JsonObject.str(k: String) = get(k)?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.long(k: String) = get(k)?.jsonPrimitive?.longOrNull
    private fun JsonObject.dec(k: String) = get(k)?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()

    private fun groupJson(g: GroupEntity) = buildJsonObject { put("id", g.id); put("name", g.name); put("sort", g.sort) }
    private fun groupOf(o: JsonObject) = GroupEntity(o.long("id")!!, o.str("name"), o["sort"]?.jsonPrimitive?.intOrNull ?: 0)

    private fun accountJson(a: AccountEntity) = buildJsonObject {
        put("id", a.id); put("groupId", a.groupId); put("name", a.name); put("currency", a.currency)
        put("balance", dec(a.balance)); put("iconType", a.iconType)
        put("iconValue", if (a.iconType == IconType.IMAGE) "" else a.iconValue)
        put("note", a.note); put("includeInTotal", a.includeInTotal); put("sortOrder", a.sortOrder)
        put("createdAt", a.createdAt); put("updatedAt", a.updatedAt)
    }
    private fun accountOf(o: JsonObject) = AccountEntity(
        id = o.long("id")!!, groupId = o.long("groupId")!!, name = o.str("name"), currency = o.str("currency"),
        balance = o.dec("balance") ?: BigDecimal.ZERO, iconType = o.str("iconType").ifEmpty { IconType.NONE },
        iconValue = o.str("iconValue"), note = o.str("note"),
        includeInTotal = o["includeInTotal"]?.jsonPrimitive?.booleanOrNull ?: true,
        sortOrder = o["sortOrder"]?.jsonPrimitive?.intOrNull ?: 0,
        createdAt = o.long("createdAt") ?: 0, updatedAt = o.long("updatedAt") ?: 0,
    )

    private fun rateJson(r: RateEntity) = buildJsonObject {
        put("currency", r.currency); put("rateToCny", dec(r.rateToCny)); put("updatedAt", r.updatedAt); put("source", r.source)
    }
    private fun rateOf(o: JsonObject) = RateEntity(o.str("currency"), o.dec("rateToCny") ?: BigDecimal.ZERO, o.long("updatedAt") ?: 0, o.str("source"))

    private fun snapshotJson(s: SnapshotEntity) = buildJsonObject {
        put("id", s.id); put("createdAt", s.createdAt); put("totalCny", dec(s.totalCny)); put("note", s.note); put("reason", s.reason)
    }
    private fun snapshotOf(o: JsonObject) = SnapshotEntity(o.long("id")!!, o.long("createdAt") ?: 0, o.dec("totalCny") ?: BigDecimal.ZERO, o.str("note"), o.str("reason"))

    private fun itemJson(i: SnapshotItemEntity) = buildJsonObject {
        put("snapshotId", i.snapshotId); put("accountId", i.accountId); put("accountName", i.accountName)
        put("currency", i.currency); put("balance", dec(i.balance)); put("rate", dec(i.rate)); put("valueCny", dec(i.valueCny))
    }
    private fun itemOf(o: JsonObject) = SnapshotItemEntity(
        snapshotId = o.long("snapshotId")!!, accountId = o.long("accountId"), accountName = o.str("accountName"),
        currency = o.str("currency"), balance = o.dec("balance") ?: BigDecimal.ZERO,
        rate = o.dec("rate") ?: BigDecimal.ZERO, valueCny = o.dec("valueCny") ?: BigDecimal.ZERO,
    )

    private fun historyJson(h: BalanceHistoryEntity) = buildJsonObject {
        put("accountId", h.accountId); put("balance", dec(h.balance)); put("valueCny", dec(h.valueCny)); put("at", h.at)
    }
    private fun historyOf(o: JsonObject) = BalanceHistoryEntity(accountId = o.long("accountId")!!, balance = o.dec("balance") ?: BigDecimal.ZERO, valueCny = o.dec("valueCny"), at = o.long("at") ?: 0)

    private fun transferJson(t: TransferEntity) = buildJsonObject {
        put("fromId", t.fromId); put("toId", t.toId); put("outAmount", dec(t.outAmount)); put("inAmount", dec(t.inAmount))
        put("fee", dec(t.fee)); put("note", t.note); put("at", t.at)
    }
    private fun transferOf(o: JsonObject) = TransferEntity(
        fromId = o.long("fromId"), toId = o.long("toId"), outAmount = o.dec("outAmount") ?: BigDecimal.ZERO,
        inAmount = o.dec("inAmount") ?: BigDecimal.ZERO, fee = o.dec("fee") ?: BigDecimal.ZERO, note = o.str("note"), at = o.long("at") ?: 0,
    )

    companion object {
        const val FORMAT = "yuji-backup"
        const val MIME = "application/octet-stream"
        private const val MAX_SIZE = 50L * 1024 * 1024
    }
}
