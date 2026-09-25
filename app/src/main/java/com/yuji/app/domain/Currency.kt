package com.yuji.app.domain

enum class Currency(
    val code: String,
    val symbol: String,
    val label: String,
    val decimals: Int,
    val isCrypto: Boolean = false,
) {
    CNY("CNY", "¥", "人民币", 2),
    USD("USD", "$", "美元", 2),
    EUR("EUR", "€", "欧元", 2),
    GBP("GBP", "£", "英镑", 2),
    JPY("JPY", "¥", "日元", 0),
    HKD("HKD", "HK$", "港币", 2),
    AUD("AUD", "A$", "澳元", 2),
    CAD("CAD", "C$", "加元", 2),
    SGD("SGD", "S$", "新加坡元", 2),
    USDT("USDT", "₮", "泰达币", 4, isCrypto = true),
    BTC("BTC", "₿", "比特币", 8, isCrypto = true),
    ETH("ETH", "Ξ", "以太坊", 8, isCrypto = true);

    companion object {
        const val BASE = "CNY"

        fun of(code: String?): Currency? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        fun decimalsOf(code: String): Int = of(code)?.decimals ?: 2
    }
}
