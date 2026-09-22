package com.workshop.risk

import com.workshop.common.DeviceFingerprintRequest
import com.workshop.common.DeviceFingerprintResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "risk")
class DeviceClient(@Qualifier("deviceRestClient") private val restClient: RestClient) {

    fun fingerprint(customerId: String): DeviceFingerprintResponse =
        restClient.post().uri("/device/fingerprint")
            .body(DeviceFingerprintRequest(customerId))
            .retrieve().body<DeviceFingerprintResponse>()!!
}
