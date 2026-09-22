package com.workshop.audit

import com.workshop.common.AuditEventRequest
import com.workshop.common.AuditEventResponse
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/audit")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "audit")
class AuditController {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/event")
    fun recordEvent(@RequestBody request: AuditEventRequest): AuditEventResponse {
        Thread.sleep(10)

        val eventId = "evt-${UUID.randomUUID().toString().take(8)}"

        Span.current().apply {
            setAttribute("audit.event_type", request.eventType)
            setAttribute("audit.entity_id", request.entityId)
            setAttribute("audit.actor_id", request.actorId)
        }

        log.info("Audit event recorded: eventId={} type={} entity={}", eventId, request.eventType, request.entityId)

        return AuditEventResponse(eventId = eventId, recorded = true)
    }
}
