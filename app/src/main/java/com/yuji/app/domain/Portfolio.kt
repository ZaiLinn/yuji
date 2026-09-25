package com.yuji.app.domain

import com.yuji.app.data.db.AccountEntity
import com.yuji.app.data.db.GroupEntity
import com.yuji.app.data.db.RateEntity
import java.math.BigDecimal

data class AccountValue(
    val account: AccountEntity,
    /** Rate to CNY, or null when this currency has never been fetched. */
    val rate: BigDecimal?,
    /** Balance in CNY, or null when the rate is missing. */
    val valueCny: BigDecimal?,
) {
    val counted: Boolean get() = account.includeInTotal && valueCny != null
}

data class GroupValue(
    val group: GroupEntity,
    val accounts: List<AccountValue>,
    val totalCny: BigDecimal,
)

data class Portfolio(
    val accounts: List<AccountValue>,
    val groups: List<GroupValue>,
    val rates: Map<String, RateEntity>,
    /** Net worth = assets − liabilities, counting only included accounts with a known rate. */
    val total: BigDecimal,
    val assets: BigDecimal,
    val liabilities: BigDecimal,
    /** Currencies used by included accounts that have no usable rate yet. */
    val missingRates: Set<String>,
) {
    fun rateOf(currency: String): BigDecimal? = rateFor(currency, rates)

    fun account(id: Long): AccountValue? = accounts.firstOrNull { it.account.id == id }

    companion object {
        val EMPTY = Portfolio(emptyList(), emptyList(), emptyMap(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptySet())

        fun rateFor(currency: String, rates: Map<String, RateEntity>): BigDecimal? {
            if (currency == Currency.BASE) return BigDecimal.ONE
            val r = rates[currency]?.rateToCny ?: return null
            return if (r.signum() > 0) r else null
        }

        fun compute(
            accounts: List<AccountEntity>,
            groups: List<GroupEntity>,
            rates: List<RateEntity>,
        ): Portfolio {
            val rateMap = rates.associateBy { it.currency }
            val values = accounts.map { a ->
                val rate = rateFor(a.currency, rateMap)
                AccountValue(a, rate, rate?.let { Money.toCny(a.balance, it) })
            }
            var assets = BigDecimal.ZERO
            var liabilities = BigDecimal.ZERO
            val missing = linkedSetOf<String>()
            for (v in values) {
                if (!v.account.includeInTotal) continue
                val cny = v.valueCny
                if (cny == null) {
                    missing += v.account.currency
                    continue
                }
                if (cny.signum() >= 0) assets += cny else liabilities += cny.negate()
            }
            val byGroup = values.groupBy { it.account.groupId }
            val groupValues = groups.map { g ->
                val list = byGroup[g.id].orEmpty()
                GroupValue(g, list, list.filter { it.counted }.fold(BigDecimal.ZERO) { s, v -> s + v.valueCny!! })
            }
            return Portfolio(values, groupValues, rateMap, assets - liabilities, assets, liabilities, missing)
        }
    }
}
