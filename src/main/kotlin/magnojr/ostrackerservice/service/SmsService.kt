package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.model.NotificationType

interface SmsService {
    fun sendSms(
        orderId: java.util.UUID,
        recipient: String,
        content: String,
        type: NotificationType,
    )
}
