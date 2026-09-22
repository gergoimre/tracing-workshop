package com.workshop.notification

import com.workshop.common.AuditEventRequest
import com.workshop.common.AuditEventResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "notification")
class AuditClient(@Qualifier("auditRestClient") private val restClient: RestClient) {

    fun record(eventType: String, entityId: String, actorId: String, payload: String): AuditEventResponse =
        restClient.post().uri("/audit/event")
            .body(AuditEventRequest(eventType = eventType, entityId = entityId, actorId = actorId, payload = payload))
            .retrieve().body<AuditEventResponse>()!!
}
