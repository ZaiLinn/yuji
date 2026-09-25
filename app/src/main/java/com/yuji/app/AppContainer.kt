package com.yuji.app

import android.content.Context
import android.util.Log
import com.yuji.app.data.YujiRepository
import com.yuji.app.data.backup.BackupManager
import com.yuji.app.data.db.RateEntity
import com.yuji.app.data.db.YujiDatabase
import com.yuji.app.data.icons.IconRepository
import com.yuji.app.data.legacy.LegacyImporter
import com.yuji.app.data.rates.RateRepository
import com.yuji.app.data.settings.SettingsStore
import com.yuji.app.domain.Currency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "YujiApp/10.1 (Android)").build())
        }
        .build()
    val db = YujiDatabase.create(context)
    val settings = SettingsStore(context)
    val icons = IconRepository(context, http)
    private val rates = RateRepository(db, http)
    val repository = YujiRepository(db, settings, rates, scope)
    val importer = LegacyImporter(db, icons)
    val backup = BackupManager(context, db, settings, icons, importer)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Shown once on the home screen, e.g. the result of migrating the old app's data. */
    val notice = MutableStateFlow<String?>(null)

    fun start() {
        scope.launch {
            runCatching { bootstrap() }.onFailure {
                Log.e(TAG, "bootstrap failed", it)
                notice.value = "初始化失败：${it.message}"
            }
            _ready.value = true
            repository.refreshRates()
        }
    }

    private suspend fun bootstrap() {
        val legacy = context.getDatabasePath(LegacyImporter.LEGACY_DB)
        val empty = db.accounts().getAll().isEmpty() && db.snapshots().count() == 0
        if (legacy.exists() && empty) {
            try {
                val summary = importer.import(LegacyImporter.readLegacyDatabase(context, legacy))
                if (settings.settings.value.goal == null) summary.goal?.let(settings::setGoal)
                archiveLegacy(legacy)
                notice.value = "已迁移旧版数据：${summary.accounts} 个账户，${summary.snapshots} 条快照"
            } catch (e: Exception) {
                Log.e(TAG, "legacy migration failed", e)
                notice.value = "旧版数据迁移失败（${e.message}），旧数据仍完整保留，可在设置中导入备份"
            }
        }
        if (db.groups().getAll().isEmpty()) {
            LegacyImporter.defaultGroups().forEach { db.groups().insert(it.copy(id = 0)) }
        }
        if (db.rates().getAll().none { it.currency == Currency.BASE }) {
            db.rates().upsert(listOf(RateEntity(Currency.BASE, BigDecimal.ONE, System.currentTimeMillis())))
        }
    }

    /** Keeps the old database (renamed) instead of deleting it. */
    private fun archiveLegacy(db: File) {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val f = File(db.path + suffix)
            if (f.exists()) f.renameTo(File(db.path + ".migrated" + suffix))
        }
    }

    private companion object {
        const val TAG = "Yuji"
    }
}
