package magnojr.ostrackerservice.service

import io.micrometer.core.instrument.MeterRegistry
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val meterRegistry: MeterRegistry,
) {
    @Transactional
    fun createFinalizedOrder(
        technicalSummary: String?,
        finalValue: BigDecimal?,
        clientName: String,
        clientPhone: String,
    ): Order {
        val now = OffsetDateTime.now()
        val order =
            Order(
                status = OrderStatus.AGUARDANDO_CONFERENCIA,
                technicalSummary = technicalSummary,
                finalValue = finalValue,
                finishedAt = now,
                hashAccess = null,
                clientName = clientName,
                clientPhone = clientPhone,
            )

        val savedOrder = orderRepository.save(order)
        meterRegistry.counter("orders.finalized", "flow", "creation").increment()

        return savedOrder
    }
}
