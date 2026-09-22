package com.workshop.transfer

import com.workshop.common.LedgerCommitRequest
import com.workshop.common.LedgerCommitResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class LedgerCommitClient(@Qualifier("ledgerRestClient") private val restClient: RestClient) {

    fun commit(transferId: String, sourceCurrency: String, amount: Long, provider: String): LedgerCommitResponse =
        restClient.post().uri("/ledger/commit")
            .body(LedgerCommitRequest(
                transferId = transferId,
                sourceCurrency = sourceCurrency,
                amount = amount,
                provider = provider
            ))
            .retrieve().body<LedgerCommitResponse>()!!
}
