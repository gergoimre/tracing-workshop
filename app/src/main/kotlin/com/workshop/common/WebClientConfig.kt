package com.workshop.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
    @Value("\${workshop.pricing-url:http://localhost:8081}") val pricingUrl: String,
    @Value("\${workshop.fx-url:http://localhost:8082}") val fxUrl: String,
    @Value("\${workshop.support-url:http://localhost:8083}") val supportUrl: String
) {
    @Bean("pricingWebClient")
    fun pricingWebClient(builder: WebClient.Builder): WebClient =
        builder.baseUrl(pricingUrl).build()

    @Bean("fxWebClient")
    fun fxWebClient(builder: WebClient.Builder): WebClient =
        builder.baseUrl(fxUrl).build()

    @Bean("supportWebClient")
    fun supportWebClient(builder: WebClient.Builder): WebClient =
        builder.baseUrl(supportUrl).build()
}
