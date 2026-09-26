package com.yuji.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.yuji.app.domain.PendingSnapshotFlag
import com.yuji.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

data class Settings(
    val goal: BigDecimal? = null,
    val hideAmounts: Boolean = false,
    /** true: 绿涨红跌 (default, same as the old app); false: 红涨绿跌. */
    val greenUp: Boolean = true,
    val lastBackupAt: Long? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
)

/**
 * Small synchronous settings backed by SharedPreferences, exposed as a StateFlow.
 * The file name differs from the legacy "yuji_settings" so the old values stay untouched.
 */
class SettingsStore(context: Context) : PendingSnapshotFlag {
    private val prefs: SharedPreferences = context.getSharedPreferences("yuji_v10", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state.asStateFlow()

    override var pending: Boolean
        get() = prefs.getBoolean(KEY_PENDING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PENDING, value).apply()
        }

    fun setGoal(goal: BigDecimal?) = edit { putString(KEY_GOAL, goal?.toPlainString()) }
    fun setHideAmounts(hide: Boolean) = edit { putBoolean(KEY_HIDE, hide) }
    fun setGreenUp(greenUp: Boolean) = edit { putBoolean(KEY_GREEN_UP, greenUp) }
    fun setLastBackupAt(at: Long) = edit { putLong(KEY_LAST_BACKUP, at) }
    fun setThemeMode(mode: ThemeMode) = edit { putString(KEY_THEME, mode.name) }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        state.value = read()
    }

    private fun read() = Settings(
        goal = prefs.getString(KEY_GOAL, null)?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
        hideAmounts = prefs.getBoolean(KEY_HIDE, false),
        greenUp = prefs.getBoolean(KEY_GREEN_UP, true),
        lastBackupAt = prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 },
        themeMode = prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
    )

    private companion object {
        const val KEY_GOAL = "goal"
        const val KEY_HIDE = "hide_amounts"
        const val KEY_GREEN_UP = "green_up"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_PENDING = "pending_snapshot"
        const val KEY_THEME = "theme_mode"
    }
}
