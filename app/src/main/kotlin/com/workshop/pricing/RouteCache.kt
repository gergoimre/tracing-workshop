package com.workshop.pricing

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "pricing")
class RouteCache {

    private data class CacheKey(
        val sourceCurrency: String,
        val targetCurrency: String,
        val transferType: String,
        val amountBucket: String,
        val requestId: String
    )

    private val store = ConcurrentHashMap<CacheKey, List<com.workshop.common.RouteCandidate>>()

    fun get(
        sourceCurrency: String,
        targetCurrency: String,
        transferType: String,
        amountBucket: String,
        requestId: String
    ): List<com.workshop.common.RouteCandidate>? =
        store[CacheKey(sourceCurrency, targetCurrency, transferType, amountBucket, requestId)]

    fun put(
        sourceCurrency: String,
        targetCurrency: String,
        transferType: String,
        amountBucket: String,
        requestId: String,
        candidates: List<com.workshop.common.RouteCandidate>
    ) {
        store[CacheKey(sourceCurrency, targetCurrency, transferType, amountBucket, requestId)] = candidates
    }
}
