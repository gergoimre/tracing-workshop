package com.workshop.transfer

import com.workshop.common.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class SupportClient(@Qualifier("supportRestClient") private val restClient: RestClient) {

    fun getCustomer(customerId: String): CustomerResponse =
        restClient.get().uri("/customer/$customerId").retrieve().body<CustomerResponse>()!!

    fun getLimits(customerId: String): LimitsResponse =
        restClient.get().uri("/limits/$customerId").retrieve().body<LimitsResponse>()!!

    fun screenCompliance(beneficiaryId: String, beneficiaryIndex: Int): ComplianceResult =
        restClient.post().uri("/compliance/screen")
            .body(ComplianceScreenRequest(beneficiaryId, beneficiaryIndex))
            .retrieve().body<ComplianceResult>()!!

    fun screenComplianceBatch(beneficiaries: List<Beneficiary>): ComplianceBatchResult =
        restClient.post().uri("/compliance/screenBatch")
            .body(ComplianceScreenBatchRequest(beneficiaries))
            .retrieve().body<ComplianceBatchResult>()!!
}
