package magnojr.ostrackerservice.service

import io.micrometer.core.instrument.MeterRegistry
import magnojr.ostrackerservice.model.NotificationType
import magnojr.ostrackerservice.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
@ConditionalOnProperty(prefix = "app.sms", name = ["mock-enabled"], havingValue = "true", matchIfMissing = true)
class MockSmsService(
    private val logService: NotificationLogService,
    private val meterRegistry: MeterRegistry,
    private val orderRepository: OrderRepository,
) : SmsService {
    private val logger = LoggerFactory.getLogger(MockSmsService::class.java)

    override fun sendSms(
        orderId: UUID,
        recipient: String,
        content: String,
        type: NotificationType,
    ) {
        val sentAt = OffsetDateTime.now()
        logger.info("SMS mock enabled. Simulating SMS send to recipient={} type={}", recipient, type)
        logger.debug("SMS content: {}", content)
        logService.saveLog(orderId, type, "MOCK", "SENT", "mock-${type.name.lowercase()}-${orderId.toString().take(8)}")
        meterRegistry
            .counter("notifications.sent", "provider", "mock", "type", type.name)
            .increment()

        orderRepository.findById(orderId).ifPresent {
            it.lastNotificationAt = sentAt
            orderRepository.save(it)
        }
    }
}
