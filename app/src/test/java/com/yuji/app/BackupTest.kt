package com.yuji.app

import android.net.Uri
import com.yuji.app.data.backup.BackupManager
import com.yuji.app.data.db.IconType
import com.yuji.app.data.icons.IconRepository
import com.yuji.app.data.settings.SettingsStore
import com.yuji.app.domain.SnapshotService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private fun manager(db: com.yuji.app.data.db.YujiDatabase): BackupManager =
        BackupManager(context, db, SettingsStore(context), IconRepository(context, OkHttpClient()))

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

    @Test fun rejectsOldV2Backup() = runBlocking {
        val file = File(context.cacheDir, "old.yuji").apply { writeText("""{"format":"yuji-backup","version":2}""") }
        val error = runCatching { manager(memoryDb()).inspect(Uri.fromFile(file)) }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}
