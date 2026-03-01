package magnojr.ostrackerservice.service

import io.micrometer.core.instrument.MeterRegistry
import magnojr.ostrackerservice.model.NotificationType
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
@ConditionalOnProperty(prefix = "app.sms", name = ["mock-enabled"], havingValue = "false")
class TwilioSmsService(
    private val twilioClient: TwilioClient,
    private val logService: NotificationLogService,
    private val meterRegistry: MeterRegistry,
    private val orderRepository: OrderRepository,
) : SmsService {
    override fun sendSms(
        orderId: UUID,
        recipient: String,
        content: String,
        type: NotificationType,
    ) {
        val formattedRecipient = formatE164(recipient)
        val sentAt = OffsetDateTime.now()

        try {
            val sid = twilioClient.sendMessage(formattedRecipient, content)
            logService.saveLog(orderId, type, "TWILIO", "SENT", sid)
            meterRegistry
                .counter("notifications.sent", "provider", "twilio", "type", type.name)
                .increment()
        } catch (e: Exception) {
            logService.saveLog(orderId, type, "TWILIO", "FAILED", null, e.message)
            meterRegistry
                .counter("notifications.failed", "provider", "twilio", "type", type.name)
                .increment()
        } finally {
            orderRepository.findById(orderId).ifPresent {
                it.lastNotificationAt = sentAt
                orderRepository.save(it)
            }
        }
    }

    private fun formatE164(phone: String): String {
        val clean = phone.replace(Regex("[^0-9]"), "")
        return if (clean.startsWith("55")) "+$clean" else "+55$clean"
    }
}
