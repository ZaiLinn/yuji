package com.yuji.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.yuji.app.data.db.IconType
import com.yuji.app.data.db.SnapshotReason
import com.yuji.app.data.icons.IconRepository
import com.yuji.app.data.legacy.LegacyImporter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.math.BigDecimal

/** Builds a database with the exact schema of the old app (DB.java v5 + MainActivity extras). */
fun createLegacyDb(file: File) {
    file.delete()
    SQLiteDatabase.openOrCreateDatabase(file, null).use { d ->
        d.execSQL("CREATE TABLE groups(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,icon TEXT DEFAULT '',sort INTEGER DEFAULT 0)")
        d.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,balance REAL NOT NULL,currency TEXT NOT NULL,group_id INTEGER NOT NULL,icon TEXT DEFAULT '',icon_path TEXT DEFAULT '',icon_type TEXT DEFAULT '',icon_cache TEXT DEFAULT '',remark TEXT DEFAULT '',include_asset INTEGER DEFAULT 1,created_time TEXT,update_time TEXT,sort_order INTEGER DEFAULT 0)")
        d.execSQL("CREATE TABLE exchange_rates(from_currency TEXT PRIMARY KEY,to_currency TEXT,rate REAL,update_time TEXT)")
        d.execSQL("CREATE TABLE account_snapshots(id INTEGER PRIMARY KEY AUTOINCREMENT,account_id INTEGER,balance REAL,base_value REAL,date TEXT)")
        d.execSQL("CREATE TABLE asset_snapshots(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,total_asset REAL,currency TEXT,note TEXT DEFAULT '',created_time TEXT)")
        d.execSQL("CREATE TABLE snapshot_exchange_rates(snapshot_id INTEGER NOT NULL,currency TEXT NOT NULL,rate REAL NOT NULL,PRIMARY KEY(snapshot_id,currency))")
        d.execSQL("CREATE TABLE snapshot_accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,snapshot_id INTEGER NOT NULL,account_id INTEGER,account_name TEXT,balance REAL,currency TEXT,rate REAL,base_value REAL)")
        d.execSQL("CREATE TABLE transfer_records(id INTEGER PRIMARY KEY AUTOINCREMENT,from_account_id INTEGER,to_account_id INTEGER,from_amount REAL,to_amount REAL,fee REAL,remark TEXT,created_time TEXT)")
        d.execSQL("INSERT INTO groups(id,name,sort) VALUES (1,'充值账户',0),(2,'资金账户',1),(3,'投资理财',2)")
        d.execSQL("INSERT INTO accounts(id,name,balance,currency,group_id,icon,remark,include_asset,created_time,update_time,sort_order) VALUES " +
            "(1,'支付宝',2667.69,'CNY',2,'emoji:💳','',1,'2026-07-01 10:00:00','2026-07-12 09:30:00',0)," +
            "(2,'Bybit EU',15098.66,'EUR',3,'cache:/nonexistent/icon.png','1943.96 EUR',1,'2026-07-01 10:00:00','2026-07-12 09:30:00',0)," +
            "(3,'Nexo',3167122,'USDT',3,'',' ',0,'2026-07-01 10:00:00','2026-07-12 09:30:00',1)," +
            "(4,'孤儿账户',10,'CNY',99,'','',1,NULL,NULL,0)")
        d.execSQL("INSERT INTO exchange_rates VALUES ('CNY','CNY',1,'2026-07-12 09:00:00'),('EUR','CNY',7.85,'2026-07-12 09:00:00'),('USD','CNY',0,'1970-01-01 00:00:00'),('USDT','CNY',7.2,'2026-07-12 09:00:00')")
        d.execSQL("INSERT INTO account_snapshots(account_id,balance,base_value,date) VALUES (1,2000,2000,'2026-07-01 10:00:00'),(1,2667.69,2667.69,'2026-07-12 09:30:00'),(2,15098.66,0,'2026-07-01 10:00:00'),(77,5,5,'2026-07-01 10:00:00')")
        d.execSQL("INSERT INTO asset_snapshots(id,date,total_asset,currency,note,created_time) VALUES (1,'2026-07-01',300000,'CNY','',NULL),(2,'2026-07-15',322456.5,'CNY','发工资','2026-07-15 20:10:00')")
        d.execSQL("INSERT INTO snapshot_accounts(snapshot_id,account_id,account_name,balance,currency,rate,base_value) VALUES (2,1,'支付宝',2667.69,'CNY',1,2667.69),(2,2,'Bybit EU',15098.66,'EUR',7.85,118524.481),(99,1,'x',1,'CNY',1,1)")
        d.execSQL("INSERT INTO transfer_records(from_account_id,to_account_id,from_amount,to_amount,fee,remark,created_time) VALUES (1,2,100,12.5,0.5,'换汇','2026-07-10 12:00:00')")
    }
}

@RunWith(RobolectricTestRunner::class)
class LegacyImporterTest {
    @Test fun migratesLegacyDatabase() = runBlocking {
        val file = context.getDatabasePath(LegacyImporter.LEGACY_DB).apply { parentFile?.mkdirs() }
        createLegacyDb(file)
        context.getSharedPreferences(LegacyImporter.LEGACY_PREFS, Context.MODE_PRIVATE).edit().putString("asset_goal", "500000.0").commit()

        val db = memoryDb()
        val importer = LegacyImporter(db, IconRepository(context, OkHttpClient()))
        val summary = importer.import(LegacyImporter.readLegacyDatabase(context, file))

        assertEquals(4, summary.accounts)
        assertEquals(2, summary.snapshots)
        assertEquals(0, BigDecimal("500000").compareTo(summary.goal))

        val groups = db.groups().getAll()
        assertEquals(listOf("充值账户", "资金账户", "投资理财", "未分组"), groups.map { it.name })

        val accounts = db.accounts().getAll().associateBy { it.id }
        val alipay = accounts.getValue(1)
        assertEquals("2667.69", alipay.balance.toPlainString())
        assertEquals(IconType.EMOJI, alipay.iconType)
        assertEquals("💳", alipay.iconValue)
        assertEquals(2L, alipay.groupId)
        // Missing icon file falls back to the default icon instead of failing the migration.
        assertEquals(IconType.NONE, accounts.getValue(2).iconType)
        assertEquals("1943.96 EUR", accounts.getValue(2).note)
        assertEquals("3167122", accounts.getValue(3).balance.stripTrailingZeros().toPlainString())
        assertEquals(false, accounts.getValue(3).includeInTotal)
        assertEquals(groups.last().id, accounts.getValue(4).groupId)

        val rates = db.rates().getAll().associateBy { it.currency }
        assertEquals("7.85", rates.getValue("EUR").rateToCny.toPlainString())
        assertNull("rate 0 means never fetched", rates["USD"])

        val history = db.history().getAll()
        assertEquals(3, history.size) // orphan row for account 77 dropped
        assertNull("base 0 for a foreign account was a missing rate", history.first { it.accountId == 2L }.valueCny)

        val snaps = db.snapshots().getAll()
        assertEquals(listOf(1L, 2L), snaps.map { it.id })
        assertEquals(SnapshotReason.LEGACY, snaps[0].reason)
        assertEquals("发工资", snaps[1].note)
        assertEquals(2, db.snapshots().items(2).size)
        assertEquals(1, db.transfers().getAll().size)
        assertEquals("12.5", db.transfers().getAll().single().inAmount.toPlainString())
    }
}
