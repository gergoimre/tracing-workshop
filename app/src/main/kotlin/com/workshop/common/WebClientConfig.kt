package com.workshop.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class WebClientConfig(
    @Value("\${workshop.pricing-url:http://localhost:8081}") val pricingUrl: String,
    @Value("\${workshop.fx-url:http://localhost:8082}") val fxUrl: String,
    @Value("\${workshop.support-url:http://localhost:8083}") val supportUrl: String
) {
    // RestClient is the Spring MVC blocking equivalent of WebClient.
    // The OTel Java agent auto-instruments RestClient outgoing calls and
    // propagates the traceparent header automatically — no manual context
    // threading required.
    @Bean("pricingRestClient")
    fun pricingRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(pricingUrl).build()

    @Bean("fxRestClient")
    fun fxRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(fxUrl).build()

    @Bean("supportRestClient")
    fun supportRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(supportUrl).build()
}
