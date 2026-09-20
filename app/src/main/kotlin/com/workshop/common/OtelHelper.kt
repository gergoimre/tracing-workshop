package com.workshop.common

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

object OtelHelper {
    fun tracer(): Tracer = GlobalOpenTelemetry.getTracer("com.workshop", "0.0.1")
}

inline fun <T> withSpan(
    name: String,
    kind: SpanKind = SpanKind.INTERNAL,
    crossinline attrs: Span.() -> Unit = {},
    crossinline block: suspend (Span) -> T
): suspend () -> T = {
    val tracer = OtelHelper.tracer()
    val span = tracer.spanBuilder(name)
        .setSpanKind(kind)
        .setParent(Context.current())
        .startSpan()
    span.attrs()
    val scope = span.makeCurrent()
    try {
        block(span)
    } catch (e: Exception) {
        span.setStatus(StatusCode.ERROR, e.message ?: "error")
        span.recordException(e)
        throw e
    } finally {
        scope.close()
        span.end()
    }
}

suspend inline fun <T> tracedSpan(
    name: String,
    kind: SpanKind = SpanKind.INTERNAL,
    crossinline attrs: Span.() -> Unit = {},
    crossinline block: suspend (Span) -> T
): T {
    val tracer = OtelHelper.tracer()
    val span = tracer.spanBuilder(name)
        .setSpanKind(kind)
        .setParent(Context.current())
        .startSpan()
    span.attrs()
    val scope = span.makeCurrent()
    return try {
        block(span)
    } catch (e: Exception) {
        span.setStatus(StatusCode.ERROR, e.message ?: "error")
        span.recordException(e)
        throw e
    } finally {
        scope.close()
        span.end()
    }
}
