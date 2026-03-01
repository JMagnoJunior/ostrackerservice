package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.model.NotificationLog
import magnojr.ostrackerservice.model.NotificationType
import magnojr.ostrackerservice.repository.NotificationLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationLogService(
    private val logRepository: NotificationLogRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveLog(
        orderId: UUID,
        type: NotificationType,
        provider: String,
        status: String,
        providerSid: String?,
        errorMessage: String? = null,
    ) {
        val log =
            NotificationLog(
                orderId = orderId,
                type = type.name,
                provider = provider,
                providerSid = providerSid,
                status = status,
                errorLog = errorMessage,
            )
        logRepository.save(log)
    }
}
