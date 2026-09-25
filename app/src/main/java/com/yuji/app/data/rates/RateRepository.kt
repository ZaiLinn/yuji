package com.yuji.app.data.rates

import com.yuji.app.data.db.RateEntity
import com.yuji.app.data.db.RateSource
import com.yuji.app.data.db.YujiDatabase
import com.yuji.app.domain.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext

/**
 * Fetches rates to CNY. Fiat from open.er-api.com, BTC/ETH from CoinGecko, USDT pegged to USD.
 * Manually overridden rates are never replaced.
 */
class RateRepository(
    private val db: YujiDatabase,
    private val http: OkHttpClient,
) {
    suspend fun fetch(): Map<String, BigDecimal> = withContext(Dispatchers.IO) {
        val out = linkedMapOf<String, BigDecimal>()
        val fx = getJson("https://open.er-api.com/v6/latest/CNY")["rates"]?.jsonObject
            ?: throw IOException("汇率接口没有返回数据")
        for (code in listOf("USD", "EUR", "GBP", "JPY", "HKD", "AUD", "CAD", "SGD")) {
            val perCny = fx[code]?.jsonPrimitive?.doubleOrNull ?: throw IOException("缺少 $code 汇率")
            if (perCny <= 0) throw IOException("$code 汇率无效")
            out[code] = BigDecimal.ONE.divide(BigDecimal(perCny.toString()), MathContext.DECIMAL64)
        }
        out["USDT"] = out.getValue("USD")
        val crypto = getJson("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=cny")
        for ((id, code) in listOf("bitcoin" to "BTC", "ethereum" to "ETH")) {
            val v = crypto[id]?.jsonObject?.get("cny")?.jsonPrimitive?.doubleOrNull
                ?: throw IOException("缺少 $code 价格")
            out[code] = BigDecimal(v.toString())
        }
        out
    }

    /** Fetches and stores; returns the number of rates written. */
    suspend fun refresh(now: Long = System.currentTimeMillis()): Int {
        val fresh = fetch()
        val manual = db.rates().getAll().filter { it.source == RateSource.MANUAL }.map { it.currency }.toSet()
        val rows = fresh.filterKeys { it !in manual }.map { (c, r) -> RateEntity(c, r, now, RateSource.AUTO) } +
            RateEntity(Currency.BASE, BigDecimal.ONE, now, RateSource.AUTO)
        db.rates().upsert(rows)
        return rows.size
    }

    private fun getJson(url: String): JsonObject {
        val req = Request.Builder().url(url).header("User-Agent", "Yuji/10 (Android)").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("汇率服务返回 ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("汇率服务无响应")
            return Json.parseToJsonElement(body).jsonObject
        }
    }
}
