package com.workshop.pricing

import com.workshop.common.FxRateRequest
import com.workshop.common.FxRateResponse
import com.workshop.common.RouteCandidate
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class FxClient(@Qualifier("fxWebClient") private val webClient: WebClient) {

    // Note: @WithSpan is intentionally not used — Spring-proxied beans get
    // duplicate spans from both proxy and bytecode instrumentation.
    // Manual spanBuilder gives a single, clean span.
    private val tracer = GlobalOpenTelemetry.getTracer("com.workshop.pricing", "0.0.1")

    suspend fun getRate(
        sourceCurrency: String,
        targetCurrency: String,
        candidate: RouteCandidate,
        parentContext: Context = Context.current()
    ): FxRateResponse {
        val fxSpan = tracer.spanBuilder("fx.call")
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(parentContext)
            .startSpan()
        fxSpan.setAttribute("fx.source_currency", sourceCurrency)
        fxSpan.setAttribute("fx.target_currency", targetCurrency)
        fxSpan.setAttribute("fx.provider", candidate.provider)
        fxSpan.setAttribute("route.type", candidate.routeType)

        val fxContext = parentContext.with(fxSpan)
        return try {
            withContext(fxContext.asContextElement()) {
                webClient.post()
                    .uri("/fx/rate")
                    .bodyValue(
                        FxRateRequest(
                            sourceCurrency = sourceCurrency,
                            targetCurrency = targetCurrency,
                            provider = candidate.provider,
                            routeType = candidate.routeType
                        )
                    )
                    .retrieve()
                    .awaitBody()
            }
        } finally {
            fxSpan.end()
        }
    }
}
