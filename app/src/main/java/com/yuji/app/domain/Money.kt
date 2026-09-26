package com.yuji.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Money helpers. All amounts are BigDecimal; nothing goes through double except chart drawing. */
object Money {
    private val TEN_THOUSAND = BigDecimal(10_000)
    private val HUNDRED_MILLION = BigDecimal(100_000_000)
    private val symbols = DecimalFormatSymbols(Locale.US)

    fun toCny(balance: BigDecimal, rate: BigDecimal): BigDecimal =
        balance.multiply(rate).setScale(8, RoundingMode.HALF_UP)

    /** ¥1,234.56 / -¥1,234.56 */
    fun cny(value: BigDecimal): String {
        val sign = if (value.signum() < 0) "-" else ""
        return sign + "¥" + grouped(value.abs(), 2, 2)
    }

    /** +¥1,234.56 / −¥1,234.56 */
    fun signedCny(value: BigDecimal): String {
        val sign = when {
            value.signum() > 0 -> "+"
            value.signum() < 0 -> "−"
            else -> ""
        }
        return sign + "¥" + grouped(value.abs(), 2, 2)
    }

    /** ¥12.3万 / ¥1.23亿, used on chart axes and compact labels. */
    fun compactCny(value: BigDecimal): String {
        val sign = if (value.signum() < 0) "-" else ""
        val a = value.abs()
        return sign + "¥" + when {
            a >= HUNDRED_MILLION -> trimmed(a.divide(HUNDRED_MILLION, 2, RoundingMode.HALF_UP)) + "亿"
            a >= TEN_THOUSAND -> trimmed(a.divide(TEN_THOUSAND, 1, RoundingMode.HALF_UP)) + "万"
            else -> grouped(a, 0, 2)
        }
    }

    /** Amount in its own currency: "1,943.96 EUR", "0.01234567 BTC". */
    fun amount(value: BigDecimal, currency: String, withCode: Boolean = true): String {
        val d = Currency.decimalsOf(currency)
        val minDigits = if (d == 0) 0 else minOf(2, d)
        val text = (if (value.signum() < 0) "-" else "") + grouped(value.abs(), minDigits, d)
        return if (withCode) "$text $currency" else text
    }

    fun rate(value: BigDecimal): String {
        val digits = if (value >= BigDecimal.ONE) 4 else 6
        return grouped(value, 2, digits)
    }

    fun percent(part: BigDecimal, total: BigDecimal): String {
        if (total.signum() == 0) return "0.0%"
        return part.multiply(BigDecimal(100)).divide(total, 1, RoundingMode.HALF_UP).toPlainString() + "%"
    }

    /** Plain editable text: no grouping, no exponent, no trailing zeros. */
    fun toInput(value: BigDecimal): String {
        val s = value.stripTrailingZeros()
        return if (s.signum() == 0) "0" else s.toPlainString()
    }

    fun parse(text: String?): BigDecimal? {
        if (text == null) return null
        val cleaned = text.trim()
            .replace(",", "")
            .replace("，", "")
            .replace("¥", "")
            .replace(" ", "")
            .replace("−", "-")
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == ".") return null
        return cleaned.toBigDecimalOrNull()
    }

    private fun grouped(value: BigDecimal, minFraction: Int, maxFraction: Int): String {
        val f = DecimalFormat("#,##0", symbols)
        f.minimumFractionDigits = minFraction
        f.maximumFractionDigits = maxFraction
        f.roundingMode = RoundingMode.HALF_UP
        return f.format(value)
    }

    private fun trimmed(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}
