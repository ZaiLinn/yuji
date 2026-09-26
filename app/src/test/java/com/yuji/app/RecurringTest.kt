package com.yuji.app

import com.yuji.app.data.RecurringDraft
import com.yuji.app.data.YujiRepository
import com.yuji.app.data.db.RecurringPeriod
import com.yuji.app.data.db.YujiDatabase
import com.yuji.app.data.rates.RateRepository
import com.yuji.app.data.settings.SettingsStore
import com.yuji.app.domain.Recurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class RecurringTest {
    private fun repo(db: YujiDatabase, clock: () -> Long) =
        YujiRepository(db, SettingsStore(context), RateRepository(db, OkHttpClient()), CoroutineScope(Dispatchers.Unconfined), clock)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0): Long = Calendar.getInstance(Recurrence.ZONE).run {
        clear()
        set(year, month - 1, day, hour, 0)
        timeInMillis
    }

    private fun draft(income: Boolean, amount: String, day: Int, period: String = RecurringPeriod.MONTHLY, month: Int = 1) =
        RecurringDraft(null, 1, if (income) "工资" else "房租", BigDecimal(amount), income, period, month, day, enabled = true)

    private fun assertBalance(db: YujiDatabase, expected: String) = runBlocking {
        assertEquals(0, BigDecimal(expected).compareTo(db.accounts().get(1)!!.balance))
    }

    @Test fun monthlyScheduleClampsToMonthEnd() {
        assertEquals(at(2026, 2, 28), Recurrence.nextAfter(RecurringPeriod.MONTHLY, 1, 31, at(2026, 2, 10)))
        assertEquals(at(2026, 3, 31), Recurrence.nextAfter(RecurringPeriod.MONTHLY, 1, 31, at(2026, 2, 28)))
        // The same day counts only if it has not started yet.
        assertEquals(at(2026, 10, 26), Recurrence.nextAfter(RecurringPeriod.MONTHLY, 1, 26, at(2026, 9, 26, 10)))
        assertEquals(at(2027, 1, 5), Recurrence.nextAfter(RecurringPeriod.MONTHLY, 1, 5, at(2026, 12, 20)))
    }

    @Test fun scheduleUsesUtcPlus8RegardlessOfDeviceZone() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            // 00:00 on Oct 26 in UTC+8 is Oct 25 16:00 UTC.
            assertEquals(Calendar.getInstance(TimeZone.getTimeZone("UTC")).run { clear(); set(2026, 9, 25, 16, 0); timeInMillis }, Recurrence.nextAfter(RecurringPeriod.MONTHLY, 1, 26, at(2026, 9, 26, 10)))
            assertEquals("2026年10月26日", Recurrence.date(at(2026, 10, 26)))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test fun yearlySchedule() {
        assertEquals(at(2026, 12, 1), Recurrence.nextAfter(RecurringPeriod.YEARLY, 12, 1, at(2026, 9, 26)))
        assertEquals(at(2027, 3, 15), Recurrence.nextAfter(RecurringPeriod.YEARLY, 3, 15, at(2026, 9, 26)))
        assertEquals(at(2028, 2, 29), Recurrence.nextAfter(RecurringPeriod.YEARLY, 2, 29, at(2027, 3, 1)))
    }

    @Test fun newRuleStartsAfterToday() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("工资卡", "CNY", "1000"))
        var now = at(2026, 9, 26, 10)
        val repo = repo(db) { now }
        val id = repo.saveRecurring(draft(income = true, amount = "8000", day = 26))
        assertEquals(at(2026, 10, 26), db.recurring().get(id)!!.nextAt)
        assertEquals(0, repo.applyRecurring())
        assertBalance(db, "1000")

        now = at(2026, 10, 26, 8)
        assertEquals(1, repo.applyRecurring())
        assertBalance(db, "9000")
        assertEquals(at(2026, 11, 26), db.recurring().get(id)!!.nextAt)
        // Running again the same day changes nothing.
        assertEquals(0, repo.applyRecurring())
        assertBalance(db, "9000")
    }

    @Test fun catchesUpMissedDatesAndLogsEach() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("银行卡", "CNY", "10000"))
        var now = at(2026, 1, 15)
        val repo = repo(db) { now }
        repo.saveRecurring(draft(income = false, amount = "3000", day = 1))
        repo.saveRecurring(draft(income = true, amount = "500.5", day = 20))

        now = at(2026, 4, 25)
        // Expense on Feb 1, Mar 1, Apr 1; income on Jan 20, Feb 20, Mar 20, Apr 20.
        assertEquals(7, repo.applyRecurring())
        assertBalance(db, "3002")
        val logs = db.transfers().getAll()
        assertEquals(7, logs.size)
        assertEquals(3, logs.count { it.fromId == 1L && it.toId == null })
        assertEquals(4, logs.count { it.toId == 1L && it.fromId == null })
        assertEquals(1, db.snapshots().count())
    }

    @Test fun pausedRuleDoesNotRunAndResumesFromNow() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("A", "CNY", "100"))
        var now = at(2026, 1, 15)
        val repo = repo(db) { now }
        val id = repo.saveRecurring(draft(income = false, amount = "10", day = 1))
        repo.setRecurringEnabled(id, false)
        now = at(2026, 5, 10)
        assertEquals(0, repo.applyRecurring())
        repo.setRecurringEnabled(id, true)
        assertEquals(0, repo.applyRecurring())
        assertEquals(at(2026, 6, 1), db.recurring().get(id)!!.nextAt)
        assertBalance(db, "100")
    }

    @Test fun deletingAccountRemovesItsRules() = runBlocking {
        val db = memoryDb()
        db.seed(Triple("A", "CNY", "100"))
        val repo = repo(db) { at(2026, 1, 15) }
        val id = repo.saveRecurring(draft(income = true, amount = "10", day = 1))
        repo.deleteAccount(1)
        assertNull(db.recurring().get(id))
    }
}
