package magnojr.ostrackerservice.service

import io.micrometer.core.instrument.MeterRegistry
import magnojr.ostrackerservice.event.OrderFinishedEvent
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val meterRegistry: MeterRegistry,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun createFinalizedOrder(
        technicalSummary: String?,
        finalValue: BigDecimal,
        clientName: String,
        clientPhone: String,
    ): Order {
        val now = OffsetDateTime.now()
        val order =
            Order(
                status = OrderStatus.FINALIZADA,
                technicalSummary = technicalSummary,
                finalValue = finalValue,
                finishedAt = now,
                hashAccess = UUID.randomUUID().toString(),
                clientName = clientName,
                clientPhone = clientPhone,
            )

        val savedOrder = orderRepository.save(order)
        meterRegistry.counter("orders.finalized", "flow", "creation").increment()

        eventPublisher.publishEvent(OrderFinishedEvent(savedOrder))

        return savedOrder
    }
}
