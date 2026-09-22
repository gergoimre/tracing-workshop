package com.workshop.accounts

import com.workshop.common.AccountBalanceResponse
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/accounts")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "accounts")
class AccountsController {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{accountId}/balance")
    fun getBalance(@PathVariable accountId: String): AccountBalanceResponse {
        Thread.sleep(12)

        val response = AccountBalanceResponse(
            accountId = accountId,
            currency = accountId.substringAfter("acc-").substringBefore("-").uppercase(),
            balanceMinorUnits = 5_000_000L,
            sufficient = true
        )

        Span.current().apply {
            setAttribute("accounts.account_id", accountId)
            setAttribute("accounts.balance_sufficient", response.sufficient)
        }

        log.info("Balance check: accountId={} sufficient={}", accountId, response.sufficient)

        return response
    }
}
