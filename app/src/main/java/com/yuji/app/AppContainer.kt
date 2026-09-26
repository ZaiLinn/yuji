package com.yuji.app

import android.content.Context
import android.util.Log
import com.yuji.app.data.YujiRepository
import com.yuji.app.data.backup.BackupManager
import com.yuji.app.data.db.GroupEntity
import com.yuji.app.data.db.RateEntity
import com.yuji.app.data.db.YujiDatabase
import com.yuji.app.data.icons.IconRepository
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
    val backup = BackupManager(context, db, settings, icons)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Shown once on the home screen, e.g. when startup failed. */
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
        if (db.groups().getAll().isEmpty()) {
            DEFAULT_GROUPS.forEachIndexed { i, name -> db.groups().insert(GroupEntity(name = name, sort = i)) }
        }
        if (db.rates().getAll().none { it.currency == Currency.BASE }) {
            db.rates().upsert(listOf(RateEntity(Currency.BASE, BigDecimal.ONE, System.currentTimeMillis())))
        }
        repository.mergeSameDaySnapshots()
    }

    private companion object {
        const val TAG = "Yuji"
        val DEFAULT_GROUPS = listOf("充值账户", "资金账户", "投资理财")
    }
}
