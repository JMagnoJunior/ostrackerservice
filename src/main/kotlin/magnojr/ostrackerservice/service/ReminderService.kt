package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.config.TwilioProperties
import magnojr.ostrackerservice.model.NotificationType
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ReminderService(
    private val orderRepository: OrderRepository,
    private val smsService: SmsService,
    private val twilioProperties: TwilioProperties,
) {
    private val logger = LoggerFactory.getLogger(ReminderService::class.java)

    fun process24hReminders(batchSize: Int): Int {
        val cutoff = OffsetDateTime.now().minusHours(24)
        val eligibleOrders =
            orderRepository.findEligibleForReminder(
                statuses = listOf(OrderStatus.FINALIZADA, OrderStatus.AGUARDANDO_AGENDAMENTO),
                cutoff = cutoff,
                pageable = PageRequest.of(0, batchSize),
            )

        eligibleOrders.forEach { order ->
            val content =
                "Lembrete: sua OS está aguardando retirada. " +
                    "Acesse os detalhes e agende em: ${twilioProperties.clientPortalUrl}/${order.hashAccess}"

            try {
                smsService.sendSms(order.id!!, order.clientPhone!!, content, NotificationType.REMINDER_24H)
            } catch (e: Exception) {
                logger.warn("Failed to process reminder for orderId={}", order.id, e)
            }
        }

        return eligibleOrders.size
    }
}
