package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.controller.CallQueueOrderDTO
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Service
class CallQueueService(
    private val orderRepository: OrderRepository,
) {
    private val eligibleStatuses = listOf(OrderStatus.FINALIZADA, OrderStatus.AGUARDANDO_AGENDAMENTO)

    fun getCallQueue(pageable: Pageable): Page<CallQueueOrderDTO> {
        val cutoff = OffsetDateTime.now().minusHours(24)
        return orderRepository.findCallQueue(eligibleStatuses, cutoff, pageable).map { order ->
            CallQueueOrderDTO(
                id = order.id!!,
                clientName = order.clientName!!,
                clientPhone = order.clientPhone!!,
                status = order.status,
                finishedAt = order.finishedAt!!,
                inactiveHours = ChronoUnit.HOURS.between(order.finishedAt, OffsetDateTime.now()),
            )
        }
    }
}
