package magnojr.ostrackerservice.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OrderServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val meterRegistry: MeterRegistry = mock()
    private val counter: Counter = mock()
    private val orderService = OrderService(orderRepository, meterRegistry)

    @Test
    fun `should create order with awaiting conference status and null hash`() {
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments.first() as Order }
        whenever(meterRegistry.counter("orders.finalized", "flow", "creation")).thenReturn(counter)

        val saved =
            orderService.createFinalizedOrder(
                technicalSummary = "Resumo tecnico",
                finalValue = BigDecimal("180.00"),
                clientName = "Joao",
                clientPhone = "5511999999999",
            )

        val orderCaptor = argumentCaptor<Order>()
        verify(orderRepository).save(orderCaptor.capture())
        verify(counter).increment()

        val order = orderCaptor.firstValue
        assertEquals(OrderStatus.AGUARDANDO_CONFERENCIA, order.status)
        assertEquals(BigDecimal("180.00"), order.finalValue)
        assertNull(order.hashAccess)
        assertNotNull(order.finishedAt)

        assertEquals(OrderStatus.AGUARDANDO_CONFERENCIA, saved.status)
        assertNull(saved.hashAccess)
    }

    @Test
    fun `should allow null finalValue when creating order`() {
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments.first() as Order }
        whenever(meterRegistry.counter("orders.finalized", "flow", "creation")).thenReturn(counter)

        val saved =
            orderService.createFinalizedOrder(
                technicalSummary = "Resumo tecnico",
                finalValue = null,
                clientName = "Joao",
                clientPhone = "5511999999999",
            )

        assertNull(saved.finalValue)
        assertEquals(OrderStatus.AGUARDANDO_CONFERENCIA, saved.status)
        assertNull(saved.hashAccess)
        verify(counter).increment()
    }
}
