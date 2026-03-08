package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.controller.DeliveryScheduleRequest
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
class OrderDeliveryService(
    private val orderRepository: OrderRepository,
) {
    private val scheduleEligibleStatuses =
        setOf(
            OrderStatus.FINALIZADA,
            OrderStatus.AGUARDANDO_AGENDAMENTO,
            OrderStatus.AGENDADA_PRESENCIAL,
            OrderStatus.AGENDADA_DELIVERY,
        )

    @Transactional
    fun scheduleDelivery(
        orderId: UUID,
        request: DeliveryScheduleRequest,
    ): Order {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                OrderNotFoundException(orderId)
            }

        if (order.status !in scheduleEligibleStatuses) {
            throw InvalidOrderStatusException(
                "OS $orderId nao pode ser agendada como delivery no status ${order.status}",
            )
        }

        order.status = OrderStatus.AGENDADA_DELIVERY
        order.scheduledDate = request.scheduledDate
        order.scheduledShift = request.scheduledShift

        return orderRepository.save(order)
    }

    @Transactional
    fun confirmDelivery(orderId: UUID): Order {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                OrderNotFoundException(orderId)
            }

        if (order.status != OrderStatus.AGENDADA_DELIVERY) {
            throw InvalidOrderStatusException(
                "OS $orderId nao pode ser confirmada como entregue no status ${order.status}",
            )
        }

        order.status = OrderStatus.ENTREGUE
        order.deliveredAt = OffsetDateTime.now(ZoneOffset.UTC)

        return orderRepository.save(order)
    }
}
