package com.workshop.transfer

import com.workshop.common.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class SupportClient(@Qualifier("supportWebClient") private val webClient: WebClient) {

    suspend fun getCustomer(customerId: String): CustomerResponse =
        webClient.get()
            .uri("/customer/$customerId")
            .retrieve()
            .awaitBody()

    suspend fun getLimits(customerId: String): LimitsResponse =
        webClient.get()
            .uri("/limits/$customerId")
            .retrieve()
            .awaitBody()

    suspend fun screenCompliance(beneficiaryId: String, beneficiaryIndex: Int): ComplianceResult =
        webClient.post()
            .uri("/compliance/screen")
            .bodyValue(ComplianceScreenRequest(beneficiaryId, beneficiaryIndex))
            .retrieve()
            .awaitBody()

    suspend fun screenComplianceBatch(beneficiaries: List<Beneficiary>): ComplianceBatchResult =
        webClient.post()
            .uri("/compliance/screenBatch")
            .bodyValue(ComplianceScreenBatchRequest(beneficiaries))
            .retrieve()
            .awaitBody()
}
