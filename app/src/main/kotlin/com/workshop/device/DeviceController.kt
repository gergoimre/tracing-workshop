package com.workshop.device

import com.workshop.common.DeviceFingerprintRequest
import com.workshop.common.DeviceFingerprintResponse
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/device")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "device")
class DeviceController {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/fingerprint")
    fun fingerprint(@RequestBody request: DeviceFingerprintRequest): DeviceFingerprintResponse {
        Thread.sleep(10)

        val response = DeviceFingerprintResponse(
            deviceId = "dev-${request.customerId.takeLast(6)}",
            trusted = true,
            os = "iOS"
        )

        Span.current().apply {
            setAttribute("device.trusted", response.trusted)
            setAttribute("device.os", response.os)
        }

        log.info("Device fingerprint: customerId={} trusted={} os={}", request.customerId, response.trusted, response.os)

        return response
    }
}
