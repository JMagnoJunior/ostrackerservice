package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.controller.OrderScheduleRequest
import magnojr.ostrackerservice.exception.InvalidOrderStatusException
import magnojr.ostrackerservice.exception.OrderNotFoundException
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrderScheduleService(
    private val orderRepository: OrderRepository,
) {
    private val eligibleStatuses =
        setOf(
            OrderStatus.FINALIZADA,
            OrderStatus.AGUARDANDO_AGENDAMENTO,
            OrderStatus.AGENDADA_PRESENCIAL,
            OrderStatus.AGENDADA_DELIVERY,
        )

    private val statusesRequiringTransition =
        setOf(OrderStatus.FINALIZADA, OrderStatus.AGUARDANDO_AGENDAMENTO)

    @Transactional
    fun updateSchedule(
        orderId: UUID,
        request: OrderScheduleRequest,
    ): Order {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                OrderNotFoundException(orderId)
            }

        if (order.status !in eligibleStatuses) {
            throw InvalidOrderStatusException(
                "OS $orderId nao pode ser agendada no status ${order.status}",
            )
        }

        if (order.status in statusesRequiringTransition) {
            order.status = OrderStatus.AGENDADA_PRESENCIAL
        }

        order.scheduledDate = request.scheduledDate
        order.scheduledShift = request.scheduledShift

        return orderRepository.save(order)
    }
}
