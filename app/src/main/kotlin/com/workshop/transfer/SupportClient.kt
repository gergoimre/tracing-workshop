package com.workshop.transfer

import com.workshop.common.*
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.context.Context
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class SupportClient(@Qualifier("supportWebClient") private val webClient: WebClient) {

    suspend fun getCustomer(customerId: String): CustomerResponse =
        withContext(Context.current().asContextElement()) {
            webClient.get()
                .uri("/customer/$customerId")
                .retrieve()
                .awaitBody()
        }

    suspend fun getLimits(customerId: String): LimitsResponse =
        withContext(Context.current().asContextElement()) {
            webClient.get()
                .uri("/limits/$customerId")
                .retrieve()
                .awaitBody()
        }

    suspend fun screenCompliance(beneficiaryId: String, beneficiaryIndex: Int): ComplianceResult =
        withContext(Context.current().asContextElement()) {
            webClient.post()
                .uri("/compliance/screen")
                .bodyValue(ComplianceScreenRequest(beneficiaryId, beneficiaryIndex))
                .retrieve()
                .awaitBody()
        }

    suspend fun screenComplianceBatch(beneficiaries: List<Beneficiary>): ComplianceBatchResult =
        withContext(Context.current().asContextElement()) {
            webClient.post()
                .uri("/compliance/screenBatch")
                .bodyValue(ComplianceScreenBatchRequest(beneficiaries))
                .retrieve()
                .awaitBody()
        }
}
