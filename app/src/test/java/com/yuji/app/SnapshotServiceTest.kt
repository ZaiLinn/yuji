package com.yuji.app

import com.yuji.app.data.db.RateEntity
import com.yuji.app.domain.Portfolio
import com.yuji.app.domain.SnapshotService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class SnapshotServiceTest {
    @Test fun createsThenDeduplicates() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("支付宝", "CNY", "2667.69"), Triple("Bybit", "EUR", "100"), rates = mapOf("CNY" to "1", "EUR" to "7.8"))
        val flag = FakeFlag()
        val service = SnapshotService(db, flag) { 1000L }

        val first = service.capture("update")
        assertTrue(first is SnapshotService.Result.Created)
        assertEquals(0, BigDecimal("3447.69").compareTo(db.snapshots().latest()!!.totalCny))
        assertEquals(2, db.snapshots().items(db.snapshots().latest()!!.id).size)

        assertEquals(SnapshotService.Result.Unchanged, service.capture("update"))
        assertEquals(1, db.snapshots().count())
    }

    /** Old app counted foreign accounts as 0 before rates arrived, producing a fake drop. */
    @Test fun waitsForMissingRates() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("支付宝", "CNY", "100"), Triple("Nexo", "USDT", "50"))
        val flag = FakeFlag()
        val service = SnapshotService(db, flag)

        val r = service.capture("update")
        assertEquals(SnapshotService.Result.WaitingForRates(setOf("USDT")), r)
        assertEquals(0, db.snapshots().count())
        assertTrue(flag.pending)

        db.rates().upsert(listOf(RateEntity("USDT", BigDecimal("7.2"), 0)))
        assertTrue(service.capture("rates") is SnapshotService.Result.Created)
        assertEquals(false, flag.pending)
        assertEquals(0, BigDecimal("460").compareTo(db.snapshots().latest()!!.totalCny))
    }

    @Test fun liabilitiesReduceNetWorth() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("工资卡", "CNY", "1000"), Triple("信用卡", "CNY", "-300"))
        val p = Portfolio.compute(db.accounts().getAll(), db.groups().getAll(), db.rates().getAll())
        assertEquals(0, BigDecimal("700").compareTo(p.total))
        assertEquals(0, BigDecimal("1000").compareTo(p.assets))
        assertEquals(0, BigDecimal("300").compareTo(p.liabilities))
    }
}
