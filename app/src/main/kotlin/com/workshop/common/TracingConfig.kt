package com.workshop.common

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the ObservedAspect bean required for @Observed to create spans.
 *
 * Spring Boot auto-configures the ObservationRegistry and the Micrometer →
 * OTel bridge (micrometer-tracing-bridge-otel), but does NOT register
 * ObservedAspect automatically — that is intentionally left to the application
 * so teams control which packages are intercepted.
 *
 * With this bean in place, any method annotated with @Observed on a
 * Spring-managed bean will produce a child span whose name is the value
 * of @Observed(name = "...").
 *
 * Attribute enrichment inside annotated methods uses Span.current() from the
 * OTel API — the bridge routes these calls to the same Micrometer-managed span.
 */
@Configuration
class TracingConfig {

    @Bean
    fun observedAspect(registry: ObservationRegistry): ObservedAspect =
        ObservedAspect(registry)
}
