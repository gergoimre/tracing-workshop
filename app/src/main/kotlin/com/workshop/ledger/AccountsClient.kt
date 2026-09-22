package com.workshop.ledger

import com.workshop.common.AccountBalanceResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "ledger")
class AccountsClient(@Qualifier("accountsRestClient") private val restClient: RestClient) {

    fun getBalance(accountId: String): AccountBalanceResponse =
        restClient.get().uri("/accounts/$accountId/balance").retrieve().body<AccountBalanceResponse>()!!
}
