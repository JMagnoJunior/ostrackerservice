package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.exception.InvalidOrderStatusException
import magnojr.ostrackerservice.exception.OrderNotFoundException
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class OrderCheckinService(
    private val orderRepository: OrderRepository,
) {
    private val eligibleStatuses =
        setOf(
            OrderStatus.AGENDADA_PRESENCIAL,
            OrderStatus.AGENDADA_DELIVERY,
        )

    @Transactional
    fun checkin(orderId: UUID): Order {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                OrderNotFoundException(orderId)
            }

        if (order.status !in eligibleStatuses) {
            throw InvalidOrderStatusException(
                "OS $orderId nao pode ser marcada como entregue no status ${order.status}",
            )
        }

        order.status = OrderStatus.ENTREGUE
        order.deliveredAt = OffsetDateTime.now(ZoneOffset.UTC)

        return orderRepository.save(order)
    }
}
