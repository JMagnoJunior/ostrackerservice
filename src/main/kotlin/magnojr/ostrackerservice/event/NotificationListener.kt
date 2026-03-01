package magnojr.ostrackerservice.event

import magnojr.ostrackerservice.config.TwilioProperties
import magnojr.ostrackerservice.model.NotificationType
import magnojr.ostrackerservice.service.SmsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NotificationListener(
    private val smsService: SmsService,
    private val twilioProperties: TwilioProperties,
) {
    private val logger = LoggerFactory.getLogger(NotificationListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderFinished(event: OrderFinishedEvent) {
        val order = event.order
        val recipient = order.clientPhone ?: return

        val content =
            "Sua OS foi finalizada! Acesse os detalhes e agende a retirada em: " +
                "${twilioProperties.clientPortalUrl}/${order.hashAccess}"

        try {
            smsService.sendSms(order.id!!, recipient, content, NotificationType.INITIAL_ALERT)
        } catch (e: Exception) {
            logger.warn("Failed to send initial alert for orderId={}", order.id, e)
        }
    }
}
