package com.workshop.ledger

import com.workshop.common.LedgerReserveRequest
import com.workshop.common.LedgerReserveResponse
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/ledger")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "ledger")
class LedgerController(private val accountsClient: AccountsClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/reserve")
    fun reserve(@RequestBody request: LedgerReserveRequest): LedgerReserveResponse = reserveFunds(request)

    @WithSpan("ledger.reserve")
    fun reserveFunds(request: LedgerReserveRequest): LedgerReserveResponse {
        Thread.sleep(18)

        val accountId = "acc-${request.sourceCurrency.lowercase()}-primary"
        val balance = accountsClient.getBalance(accountId)

        val reservationId = "res-${UUID.randomUUID().toString().take(8)}"

        Span.current().apply {
            setAttribute("ledger.account_id", accountId)
            setAttribute("ledger.reserved", true)
            setAttribute("ledger.balance_sufficient", balance.sufficient)
        }

        log.info("Funds reserved: transferId={} account={} reservationId={}", request.transferId, accountId, reservationId)

        return LedgerReserveResponse(
            reservationId = reservationId,
            accountId = accountId,
            reserved = true
        )
    }
}
