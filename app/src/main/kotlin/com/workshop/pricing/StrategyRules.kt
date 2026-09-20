package com.workshop.pricing

enum class PricingStrategy { SINGLE_ROUTE, MULTI_ROUTE }

/**
 * Rule table that maps a transfer profile to a pricing strategy.
 * Reads as normal business logic — the MULTI_ROUTE rule is not highlighted
 * and does not reveal the performance bug.
 */
object StrategyRules {

    private data class RuleKey(
        val sourceCurrency: String,
        val targetCurrency: String,
        val transferType: String,
        val amountBucket: String
    )

    private val rules: Map<RuleKey, PricingStrategy> = mapOf(
        RuleKey("EUR", "BRL", "BANK_TRANSFER", "10000_PLUS") to PricingStrategy.MULTI_ROUTE
    )

    fun resolve(
        sourceCurrency: String,
        targetCurrency: String,
        transferType: String,
        amountBucket: String
    ): PricingStrategy =
        rules[RuleKey(sourceCurrency, targetCurrency, transferType, amountBucket)]
            ?: PricingStrategy.SINGLE_ROUTE
}
