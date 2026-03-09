package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.exception.InvalidOrderStatusException
import magnojr.ostrackerservice.exception.OrderNotFoundException
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

class OrderCheckinServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val service = OrderCheckinService(orderRepository)

    private fun orderWithStatus(status: OrderStatus): Order {
        val order =
            Order(
                id = UUID.randomUUID(),
                status = status,
                finishedAt = OffsetDateTime.now(),
                clientName = "Cliente Teste",
                clientPhone = "5511999999999",
            )
        whenever(orderRepository.findById(order.id!!)).thenReturn(Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] as Order }
        return order
    }

    @Test
    fun `checkin com OS em AGENDADA_PRESENCIAL deve transicionar para ENTREGUE e preencher deliveredAt`() {
        val order = orderWithStatus(OrderStatus.AGENDADA_PRESENCIAL)

        val result = service.checkin(order.id!!)

        assertEquals(OrderStatus.ENTREGUE, result.status)
        assertNotNull(result.deliveredAt)
    }

    @Test
    fun `checkin com OS em AGENDADA_DELIVERY deve transicionar para ENTREGUE e preencher deliveredAt`() {
        val order = orderWithStatus(OrderStatus.AGENDADA_DELIVERY)

        val result = service.checkin(order.id!!)

        assertEquals(OrderStatus.ENTREGUE, result.status)
        assertNotNull(result.deliveredAt)
    }

    @Test
    fun `checkin com OS em FINALIZADA deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.FINALIZADA)

        assertThrows<InvalidOrderStatusException> {
            service.checkin(order.id!!)
        }
    }

    @Test
    fun `checkin com OS em ENTREGUE deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.ENTREGUE)

        assertThrows<InvalidOrderStatusException> {
            service.checkin(order.id!!)
        }
    }

    @Test
    fun `checkin com OS em ABERTA deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.ABERTA)

        assertThrows<InvalidOrderStatusException> {
            service.checkin(order.id!!)
        }
    }

    @Test
    fun `checkin com OS em AGUARDANDO_AGENDAMENTO deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.AGUARDANDO_AGENDAMENTO)

        assertThrows<InvalidOrderStatusException> {
            service.checkin(order.id!!)
        }
    }

    @Test
    fun `checkin com OS inexistente deve lancar OrderNotFoundException`() {
        val unknownId = UUID.randomUUID()
        whenever(orderRepository.findById(unknownId)).thenReturn(Optional.empty())

        assertThrows<OrderNotFoundException> {
            service.checkin(unknownId)
        }
    }
}
