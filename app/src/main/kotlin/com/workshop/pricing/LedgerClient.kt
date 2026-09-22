package com.workshop.pricing

import com.workshop.common.LedgerReserveRequest
import com.workshop.common.LedgerReserveResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class LedgerClient(@Qualifier("ledgerRestClient") private val restClient: RestClient) {

    fun reserve(transferId: String, sourceCurrency: String, amount: Long, provider: String): LedgerReserveResponse =
        restClient.post().uri("/ledger/reserve")
            .body(LedgerReserveRequest(
                transferId = transferId,
                sourceCurrency = sourceCurrency,
                amount = amount,
                provider = provider
            ))
            .retrieve().body<LedgerReserveResponse>()!!
}
