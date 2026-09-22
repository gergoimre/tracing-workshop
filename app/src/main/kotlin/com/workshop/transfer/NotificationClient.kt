package com.workshop.transfer

import com.workshop.common.NotificationRequest
import com.workshop.common.NotificationResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class NotificationClient(@Qualifier("notificationRestClient") private val restClient: RestClient) {

    fun send(customerId: String, transferId: String, channel: String, templateId: String): NotificationResponse =
        restClient.post().uri("/notifications/send")
            .body(NotificationRequest(
                customerId = customerId,
                transferId = transferId,
                channel = channel,
                templateId = templateId
            ))
            .retrieve().body<NotificationResponse>()!!
}
