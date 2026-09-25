package com.yuji.app

import android.net.Uri
import com.yuji.app.data.backup.BackupManager
import com.yuji.app.data.db.AccountEntity
import com.yuji.app.data.db.IconType
import com.yuji.app.data.icons.IconRepository
import com.yuji.app.data.legacy.LegacyImporter
import com.yuji.app.data.settings.SettingsStore
import com.yuji.app.domain.SnapshotService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private fun manager(db: com.yuji.app.data.db.YujiDatabase): BackupManager {
        val icons = IconRepository(context, OkHttpClient())
        return BackupManager(context, db, SettingsStore(context), icons, LegacyImporter(db, icons))
    }

    @Test fun v3RoundTrip() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("支付宝", "CNY", "2667.69"), Triple("BTC 钱包", "BTC", "0.01234567"), rates = mapOf("CNY" to "1", "BTC" to "450000.5"))
        db.accounts().update(db.accounts().get(1)!!.copy(iconType = IconType.EMOJI, iconValue = "💳"))
        SnapshotService(db, FakeFlag()).capture("update")
        val backup = manager(db)

        val file = File(context.cacheDir, "t.yuji")
        backup.export(Uri.fromFile(file))
        assertTrue(file.readText().contains("\"version\":3"))

        val target = memoryDb()
        val preview = manager(target).inspect(Uri.fromFile(file))
        assertTrue(preview.checksumOk)
        assertEquals(2, preview.accounts)
        manager(target).apply(preview)

        val restored = target.accounts().getAll()
        assertEquals(listOf("支付宝", "BTC 钱包"), restored.map { it.name })
        assertEquals("0.01234567", restored[1].balance.toPlainString())
        assertEquals("💳", restored[0].iconValue)
        assertEquals(1, target.snapshots().count())
        assertEquals(2, target.snapshots().items(target.snapshots().latest()!!.id).size)
    }

    @Test fun tamperedV3IsFlagged() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("A", "CNY", "1"))
        val file = File(context.cacheDir, "t2.yuji")
        manager(db).export(Uri.fromFile(file))
        file.writeText(file.readText().replace("\"name\":\"A\"", "\"name\":\"B\""))
        assertFalse(manager(memoryDb()).inspect(Uri.fromFile(file)).checksumOk)
    }

    @Test fun importsOldV2Backup() = runBlocking {
        val tables = JSONObject()
            .put("groups", JSONArray().put(JSONObject().put("id", 1).put("name", "资金账户").put("icon", "").put("sort", 0)))
            .put("accounts", JSONArray().put(JSONObject().put("id", 5).put("name", "微信").put("balance", 88.5).put("currency", "CNY").put("group_id", 1)
                .put("icon", "").put("remark", "").put("include_asset", 1).put("created_time", "2026-07-01 10:00:00").put("update_time", "2026-07-02 10:00:00").put("sort_order", 0)))
            .put("exchange_rates", JSONArray()).put("account_snapshots", JSONArray()).put("asset_snapshots", JSONArray())
            .put("snapshot_exchange_rates", JSONArray()).put("snapshot_accounts", JSONArray()).put("transfer_records", JSONArray())
        val root = JSONObject().put("format", "yuji-backup").put("version", 2).put("created_at", "2026-07-13 21:00:00")
            .put("asset_goal", "0").put("tables", tables).put("icons", JSONObject())
            .put("integrity", JSONObject().put("algorithm", "SHA-256").put("checksum", "bogus"))
        val file = File(context.cacheDir, "old.yuji").apply { writeText(root.toString()) }

        val db = memoryDb()
        val preview = manager(db).inspect(Uri.fromFile(file))
        assertEquals(2, preview.version)
        assertFalse(preview.checksumOk)
        manager(db).apply(preview)
        val a: AccountEntity = db.accounts().getAll().single()
        assertEquals("微信", a.name)
        assertEquals(0, BigDecimal("88.5").compareTo(a.balance))
    }
}
