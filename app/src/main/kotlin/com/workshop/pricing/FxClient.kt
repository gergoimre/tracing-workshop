package com.workshop.pricing

import com.workshop.common.FxRateRequest
import com.workshop.common.FxRateResponse
import com.workshop.common.RouteCandidate
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class FxClient(@Qualifier("fxWebClient") private val webClient: WebClient) {

    private val tracer = GlobalOpenTelemetry.getTracer("com.workshop.pricing", "0.0.1")

    suspend fun getRate(
        sourceCurrency: String,
        targetCurrency: String,
        candidate: RouteCandidate,
        parentContext: Context = Context.current()
    ): FxRateResponse {
        // Create an explicit INTERNAL span for each FX call so it appears
        // as a named child under pricing.calculate in the waterfall.
        // The HTTP CLIENT span for the actual WebClient call is created
        // automatically by the OTel agent, nested under this span.
        val fxSpan = tracer.spanBuilder("fx.call")
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(parentContext)
            .startSpan()
        fxSpan.setAttribute("fx.source_currency", sourceCurrency)
        fxSpan.setAttribute("fx.target_currency", targetCurrency)
        fxSpan.setAttribute("fx.provider", candidate.provider)
        fxSpan.setAttribute("route.type", candidate.routeType)

        // Make this span current so the WebClient call (auto-instrumented)
        // is correctly nested under it.
        val scope = fxSpan.makeCurrent()
        return try {
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
        } finally {
            scope.close()
            fxSpan.end()
        }
    }
}
