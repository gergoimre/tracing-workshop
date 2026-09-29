package com.workshop.pricing

enum class PricingStrategy { SINGLE_ROUTE, MULTI_ROUTE }

object StrategyRules {

    fun resolve(
        sourceCurrency: String,
        targetCurrency: String,
        transferType: String,
        amount: Long
    ): PricingStrategy =
        if (sourceCurrency == "EUR" &&
            targetCurrency == "BRL" &&
            transferType == "BANK_TRANSFER" &&
            amount >= 10_000
        ) PricingStrategy.MULTI_ROUTE
        else PricingStrategy.SINGLE_ROUTE
}
