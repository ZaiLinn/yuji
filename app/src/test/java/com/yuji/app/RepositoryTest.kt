package com.yuji.app

import com.yuji.app.data.AccountDraft
import com.yuji.app.data.YujiRepository
import com.yuji.app.data.db.SnapshotEntity
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
    private fun repo(db: com.yuji.app.data.db.YujiDatabase, clock: () -> Long = System::currentTimeMillis) =
        YujiRepository(db, SettingsStore(context), RateRepository(db, OkHttpClient()), CoroutineScope(Dispatchers.Unconfined), clock)

    @Test fun deleteAccountTakesSnapshot() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("A", "CNY", "100"), Triple("B", "CNY", "50"))
        var now = START
        val repo = repo(db) { now }
        repo.captureSnapshot("update")
        now += DAY
        repo.deleteAccount(2)
        assertEquals(2, db.snapshots().count())
        assertEquals(0, BigDecimal("100").compareTo(db.snapshots().latest()!!.totalCny))
    }

    @Test fun changesOnTheSameDayShareOneSnapshot() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("A", "CNY", "100"))
        var now = START
        val repo = repo(db) { now }
        repo.confirmBalances(mapOf(1L to BigDecimal("120")))
        val id = db.snapshots().latest()!!.id
        repo.updateSnapshotNote(id, "发工资")

        now += 60_000
        repo.confirmBalances(mapOf(1L to BigDecimal("130")))
        assertEquals(1, db.snapshots().count())
        val latest = db.snapshots().latest()!!
        assertEquals(id, latest.id)
        assertEquals("发工资", latest.note)
        assertEquals(now, latest.createdAt)
        assertEquals(0, BigDecimal("130").compareTo(latest.totalCny))
        assertEquals(0, BigDecimal("130").compareTo(db.snapshots().items(id).single().balance))

        now += DAY
        repo.confirmBalances(mapOf(1L to BigDecimal("140")))
        assertEquals(2, db.snapshots().count())
    }

    @Test fun renameDoesNotTakeSnapshot() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("A", "CNY", "100"))
        var now = START
        val repo = repo(db) { now }
        repo.captureSnapshot("update")
        now += DAY
        val a = db.accounts().get(1)!!
        repo.saveAccount(AccountDraft(a.id, a.groupId, "改名", a.currency, a.balance, a.iconType, a.iconValue, a.note, a.includeInTotal))
        assertEquals(1, db.snapshots().count())
        assertEquals("改名", db.accounts().get(1)!!.name)
    }

    @Test fun mergeKeepsLastSnapshotOfEachDay() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("A", "CNY", "100"))
        val day1 = listOf(START, START + 1_000, START + 2_000)
        val ids = day1.mapIndexed { i, at ->
            db.snapshots().insert(SnapshotEntity(createdAt = at, totalCny = BigDecimal(100 + i), note = if (i == 0) "早上" else "", reason = "update"))
        }
        db.snapshots().insert(SnapshotEntity(createdAt = START + DAY, totalCny = BigDecimal("200"), reason = "update"))

        assertEquals(2, repo(db).mergeSameDaySnapshots())
        val left = db.snapshots().getAll()
        assertEquals(listOf(ids.last(), left.last().id), left.map { it.id })
        assertEquals("早上", left.first().note)
        assertEquals(0, BigDecimal("102").compareTo(left.first().totalCny))
        assertEquals(0, repo(db).mergeSameDaySnapshots())
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

    private companion object {
        const val DAY = 86_400_000L
        /** Noon UTC, so small offsets from it never cross midnight in any time zone. */
        const val START = 30 * DAY + DAY / 2
    }
}
