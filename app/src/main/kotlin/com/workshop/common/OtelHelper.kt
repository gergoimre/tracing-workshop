package com.workshop.common

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer

object OtelHelper {
    fun tracer(): Tracer = GlobalOpenTelemetry.getTracer("com.workshop", "0.0.1")
}
