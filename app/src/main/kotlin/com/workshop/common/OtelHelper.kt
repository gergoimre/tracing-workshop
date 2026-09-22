package com.workshop.common

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * Shared tracer for manual span creation.
 *
 * Used only where @WithSpan cannot be applied — e.g. when attributes must be
 * set on the span the agent already created for an HTTP request
 * (Span.current().setAttribute(...)).
 *
 * All other custom spans use @WithSpan on the @Service/@Component method.
 */
object OtelHelper {
    fun tracer(): Tracer = GlobalOpenTelemetry.getTracer("com.workshop", "0.0.1")
}
