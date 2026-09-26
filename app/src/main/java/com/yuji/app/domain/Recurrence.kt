package com.yuji.app.domain

import com.yuji.app.data.db.RecurringEntity
import com.yuji.app.data.db.RecurringPeriod
import java.util.Calendar

/** Schedule math for fixed income / expense rules. Occurrences fall at 00:00 local time. */
object Recurrence {
    /** First occurrence strictly after [after]. */
    fun nextAfter(period: String, month: Int, day: Int, after: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = after }
        val year = cal.get(Calendar.YEAR)
        return if (period == RecurringPeriod.YEARLY) {
            occurrence(year, month - 1, day).takeIf { it > after } ?: occurrence(year + 1, month - 1, day)
        } else {
            val m = cal.get(Calendar.MONTH)
            occurrence(year, m, day).takeIf { it > after } ?: occurrence(year, m + 1, day)
        }
    }

    fun nextAfter(rule: RecurringEntity, after: Long): Long = nextAfter(rule.period, rule.month, rule.day, after)

    /** Start of [day] in the given month (0-based, may overflow into the next year), clamped to the month's last day. */
    private fun occurrence(year: Int, month0: Int, day: Int): Long = Calendar.getInstance().run {
        clear()
        set(year, month0, 1)
        set(Calendar.DAY_OF_MONTH, day.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
        timeInMillis
    }

    fun describe(period: String, month: Int, day: Int): String =
        if (period == RecurringPeriod.YEARLY) "每年 ${month} 月 ${day} 日" else "每月 ${day} 日"
}
