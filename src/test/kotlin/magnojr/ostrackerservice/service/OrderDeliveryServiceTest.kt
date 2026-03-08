package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.controller.DeliveryScheduleRequest
import magnojr.ostrackerservice.exception.InvalidOrderStatusException
import magnojr.ostrackerservice.exception.OrderNotFoundException
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.model.ScheduledShift
import magnojr.ostrackerservice.repository.OrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

class OrderDeliveryServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val service = OrderDeliveryService(orderRepository)

    private val tomorrow: LocalDate = LocalDate.now().plusDays(1)
    private val validRequest = DeliveryScheduleRequest(scheduledDate = tomorrow, scheduledShift = ScheduledShift.MANHA)

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

    // --- scheduleDelivery ---

    @Test
    fun `scheduleDelivery com OS em FINALIZADA deve transicionar para AGENDADA_DELIVERY`() {
        val order = orderWithStatus(OrderStatus.FINALIZADA)

        val result = service.scheduleDelivery(order.id!!, validRequest)

        assertEquals(OrderStatus.AGENDADA_DELIVERY, result.status)
        assertEquals(tomorrow, result.scheduledDate)
        assertEquals(ScheduledShift.MANHA, result.scheduledShift)
    }

    @Test
    fun `scheduleDelivery com OS em AGUARDANDO_AGENDAMENTO deve transicionar para AGENDADA_DELIVERY`() {
        val order = orderWithStatus(OrderStatus.AGUARDANDO_AGENDAMENTO)

        val result = service.scheduleDelivery(order.id!!, validRequest)

        assertEquals(OrderStatus.AGENDADA_DELIVERY, result.status)
    }

    @Test
    fun `scheduleDelivery com OS em AGENDADA_PRESENCIAL deve transicionar para AGENDADA_DELIVERY`() {
        val order = orderWithStatus(OrderStatus.AGENDADA_PRESENCIAL)

        val result = service.scheduleDelivery(order.id!!, validRequest)

        assertEquals(OrderStatus.AGENDADA_DELIVERY, result.status)
    }

    @Test
    fun `scheduleDelivery com OS em AGENDADA_DELIVERY deve sobrescrever campos e manter status`() {
        val order = orderWithStatus(OrderStatus.AGENDADA_DELIVERY)

        val result = service.scheduleDelivery(order.id!!, validRequest)

        assertEquals(OrderStatus.AGENDADA_DELIVERY, result.status)
        assertEquals(tomorrow, result.scheduledDate)
        assertEquals(ScheduledShift.MANHA, result.scheduledShift)
    }

    @Test
    fun `scheduleDelivery com OS em ENTREGUE deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.ENTREGUE)

        assertThrows<InvalidOrderStatusException> {
            service.scheduleDelivery(order.id!!, validRequest)
        }
    }

    @Test
    fun `scheduleDelivery com OS em AGUARDANDO_CONFERENCIA deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.AGUARDANDO_CONFERENCIA)

        assertThrows<InvalidOrderStatusException> {
            service.scheduleDelivery(order.id!!, validRequest)
        }
    }

    @Test
    fun `scheduleDelivery com OS inexistente deve lancar OrderNotFoundException`() {
        val unknownId = UUID.randomUUID()
        whenever(orderRepository.findById(unknownId)).thenReturn(Optional.empty())

        assertThrows<OrderNotFoundException> {
            service.scheduleDelivery(unknownId, validRequest)
        }
    }

    // --- confirmDelivery ---

    @Test
    fun `confirmDelivery com OS em AGENDADA_DELIVERY deve transicionar para ENTREGUE e preencher deliveredAt`() {
        val order = orderWithStatus(OrderStatus.AGENDADA_DELIVERY)

        val result = service.confirmDelivery(order.id!!)

        assertEquals(OrderStatus.ENTREGUE, result.status)
        assertNotNull(result.deliveredAt)
    }

    @Test
    fun `confirmDelivery com OS em AGENDADA_PRESENCIAL deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.AGENDADA_PRESENCIAL)

        assertThrows<InvalidOrderStatusException> {
            service.confirmDelivery(order.id!!)
        }
    }

    @Test
    fun `confirmDelivery com OS em FINALIZADA deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.FINALIZADA)

        assertThrows<InvalidOrderStatusException> {
            service.confirmDelivery(order.id!!)
        }
    }

    @Test
    fun `confirmDelivery com OS inexistente deve lancar OrderNotFoundException`() {
        val unknownId = UUID.randomUUID()
        whenever(orderRepository.findById(unknownId)).thenReturn(Optional.empty())

        assertThrows<OrderNotFoundException> {
            service.confirmDelivery(unknownId)
        }
    }
}
