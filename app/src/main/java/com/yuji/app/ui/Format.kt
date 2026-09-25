package com.yuji.app.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    private const val DAY = 86_400_000L

    fun age(ts: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysBetween(ts, now)
        return when {
            days <= 0 -> "今天"
            days == 1 -> "昨天"
            days < 30 -> "$days 天前"
            days < 365 -> "${days / 30} 个月前"
            else -> "${days / 365} 年前"
        }
    }

    fun ageDays(ts: Long, now: Long = System.currentTimeMillis()): Int = daysBetween(ts, now)

    fun date(ts: Long): String = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(ts))
    fun dateTime(ts: Long): String = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(ts))
    fun monthDay(ts: Long): String = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(ts))
    fun shortDate(ts: Long): String = SimpleDateFormat("M/d", Locale.CHINA).format(Date(ts))
    fun yearMonth(ts: Long): String = SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date(ts))
    fun time(ts: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))

    fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun daysBetween(from: Long, to: Long): Int = ((startOfDay(to) - startOfDay(from)) / DAY).toInt()
}
