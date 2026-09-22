package com.workshop.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class WebClientConfig(
    @Value("\${workshop.pricing-url:http://localhost:8081}") val pricingUrl: String,
    @Value("\${workshop.fx-url:http://localhost:8082}") val fxUrl: String,
    @Value("\${workshop.support-url:http://localhost:8083}") val supportUrl: String,
    @Value("\${workshop.auth-url:http://localhost:8084}") val authUrl: String,
    @Value("\${workshop.session-url:http://localhost:8085}") val sessionUrl: String,
    @Value("\${workshop.risk-url:http://localhost:8086}") val riskUrl: String,
    @Value("\${workshop.device-url:http://localhost:8087}") val deviceUrl: String,
    @Value("\${workshop.ledger-url:http://localhost:8088}") val ledgerUrl: String,
    @Value("\${workshop.accounts-url:http://localhost:8089}") val accountsUrl: String,
    @Value("\${workshop.notification-url:http://localhost:8090}") val notificationUrl: String,
    @Value("\${workshop.audit-url:http://localhost:8091}") val auditUrl: String,
    @Value("\${workshop.screening-url:http://localhost:8092}") val screeningUrl: String
) {
    @Bean("pricingRestClient")
    fun pricingRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(pricingUrl).build()

    @Bean("fxRestClient")
    fun fxRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(fxUrl).build()

    @Bean("supportRestClient")
    fun supportRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(supportUrl).build()

    @Bean("authRestClient")
    fun authRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(authUrl).build()

    @Bean("sessionRestClient")
    fun sessionRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(sessionUrl).build()

    @Bean("riskRestClient")
    fun riskRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(riskUrl).build()

    @Bean("deviceRestClient")
    fun deviceRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(deviceUrl).build()

    @Bean("ledgerRestClient")
    fun ledgerRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(ledgerUrl).build()

    @Bean("accountsRestClient")
    fun accountsRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(accountsUrl).build()

    @Bean("notificationRestClient")
    fun notificationRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(notificationUrl).build()

    @Bean("auditRestClient")
    fun auditRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(auditUrl).build()

    @Bean("screeningRestClient")
    fun screeningRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl(screeningUrl).build()
}
