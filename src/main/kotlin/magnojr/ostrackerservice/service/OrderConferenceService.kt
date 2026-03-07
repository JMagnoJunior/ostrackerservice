package magnojr.ostrackerservice.service

import io.micrometer.core.instrument.MeterRegistry
import magnojr.ostrackerservice.controller.OrderConferenceItemDTO
import magnojr.ostrackerservice.controller.OrderConferenceUpdateDTO
import magnojr.ostrackerservice.event.OrderFinishedEvent
import magnojr.ostrackerservice.exception.InvalidOrderStatusException
import magnojr.ostrackerservice.exception.OrderNotFoundException
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrderConferenceService(
    private val orderRepository: OrderRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val meterRegistry: MeterRegistry,
) {
    fun listPendingConference(pageable: Pageable): Page<OrderConferenceItemDTO> =
        orderRepository
            .findByStatusOrderByFinishedAtAsc(OrderStatus.AGUARDANDO_CONFERENCIA, pageable)
            .map { OrderConferenceItemDTO.from(it) }

    @Transactional
    fun updateConference(
        orderId: UUID,
        dto: OrderConferenceUpdateDTO,
    ): Order {
        val order = findPendingOrThrow(orderId)

        dto.technicalSummary?.let { order.technicalSummary = it }
        dto.finalValue?.let { order.finalValue = it }
        dto.clientName?.let { order.clientName = it }
        dto.clientPhone?.let { order.clientPhone = it }

        return orderRepository.save(order)
    }

    @Transactional
    fun confirmConference(orderId: UUID): Order {
        val order = findPendingOrThrow(orderId)

        validateForConfirmation(order)

        if (order.hashAccess == null) {
            order.hashAccess = UUID.randomUUID().toString()
        }
        order.status = OrderStatus.FINALIZADA

        val saved = orderRepository.save(order)
        eventPublisher.publishEvent(OrderFinishedEvent(saved))
        meterRegistry.counter("orders.conference.confirmed").increment()

        return saved
    }

    private fun findPendingOrThrow(orderId: UUID): Order =
        orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA)
            .orElseThrow {
                orderRepository.findById(orderId).orElseThrow { OrderNotFoundException(orderId) }
                InvalidOrderStatusException("OS $orderId nao esta em AGUARDANDO_CONFERENCIA")
            }

    private fun validateForConfirmation(order: Order) {
        require(!order.technicalSummary.isNullOrBlank()) {
            "technicalSummary nao pode ser vazio para confirmar a conferencia"
        }
        require(order.finalValue != null && order.finalValue!! > java.math.BigDecimal.ZERO) {
            "finalValue deve ser positivo para confirmar a conferencia"
        }
        require(!order.clientName.isNullOrBlank()) {
            "clientName nao pode ser vazio para confirmar a conferencia"
        }
        require(
            !order.clientPhone.isNullOrBlank() &&
                order.clientPhone!!.matches(Regex("^[1-9][0-9]{10,14}$")),
        ) {
            "clientPhone invalido para confirmar a conferencia"
        }
    }
}
