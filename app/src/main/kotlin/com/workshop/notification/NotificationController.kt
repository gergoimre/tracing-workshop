package com.workshop.notification

import com.workshop.common.NotificationRequest
import com.workshop.common.NotificationResponse
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/notifications")
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "notification")
class NotificationController(private val auditClient: AuditClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/send")
    fun send(@RequestBody request: NotificationRequest): NotificationResponse = sendNotification(request)

    @WithSpan("notification.send")
    fun sendNotification(request: NotificationRequest): NotificationResponse {
        Thread.sleep(20)

        val notificationId = "notif-${UUID.randomUUID().toString().take(8)}"

        auditClient.record(
            eventType = "NOTIFICATION_SENT",
            entityId = request.transferId,
            actorId = request.customerId,
            payload = """{"channel":"${request.channel}","templateId":"${request.templateId}"}"""
        )

        Span.current().apply {
            setAttribute("notification.channel", request.channel)
            setAttribute("notification.template_id", request.templateId)
            setAttribute("notification.queued", true)
        }

        log.info("Notification sent: transferId={} channel={} notificationId={}", request.transferId, request.channel, notificationId)

        return NotificationResponse(notificationId = notificationId, channel = request.channel, queued = true)
    }
}
