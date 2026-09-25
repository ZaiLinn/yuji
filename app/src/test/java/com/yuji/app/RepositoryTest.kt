package com.yuji.app

import com.yuji.app.data.YujiRepository
import com.yuji.app.data.rates.RateRepository
import com.yuji.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private fun repo(db: com.yuji.app.data.db.YujiDatabase) =
        YujiRepository(db, SettingsStore(context), RateRepository(db, OkHttpClient()), CoroutineScope(Dispatchers.Unconfined))

    @Test fun deleteAccountTakesSnapshot() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("A", "CNY", "100"), Triple("B", "CNY", "50"))
        val repo = repo(db)
        repo.captureSnapshot("update")
        repo.deleteAccount(2)
        assertEquals(2, db.snapshots().count())
        assertEquals(0, BigDecimal("100").compareTo(db.snapshots().latest()!!.totalCny))
    }

    @Test fun quickUpdateConfirmsAndRecordsChanges() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("A", "CNY", "100"), Triple("B", "CNY", "50"))
        val repo = repo(db)
        val changed = repo.confirmBalances(mapOf(1L to BigDecimal("120"), 2L to BigDecimal("50")))
        assertEquals(1, changed)
        assertEquals(1, db.history().getAll().size)
        assertEquals(true, db.accounts().getAll().all { it.updatedAt > 0 })
        assertEquals(0, BigDecimal("170").compareTo(db.snapshots().latest()!!.totalCny))
    }

    @Test fun transferMayOverdraw() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("信用卡", "CNY", "0"), Triple("B", "CNY", "0"))
        val repo = repo(db)
        repo.transfer(1, 2, BigDecimal("100"), BigDecimal("100"), BigDecimal("1"), "")
        val a = db.accounts().getAll().associateBy { it.id }
        assertEquals(0, BigDecimal("-101").compareTo(a.getValue(1).balance))
        assertEquals(0, BigDecimal("100").compareTo(a.getValue(2).balance))
        assertEquals(1, db.transfers().getAll().size)
    }
}
