package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.controller.OrderScheduleRequest
import magnojr.ostrackerservice.exception.InvalidOrderStatusException
import magnojr.ostrackerservice.exception.OrderNotFoundException
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.model.ScheduledShift
import magnojr.ostrackerservice.repository.OrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

class OrderScheduleServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val service = OrderScheduleService(orderRepository)

    private val tomorrow: LocalDate = LocalDate.now().plusDays(1)
    private val validRequest = OrderScheduleRequest(scheduledDate = tomorrow, scheduledShift = ScheduledShift.MANHA)

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
    fun `OS em AGENDADA_PRESENCIAL deve atualizar campos sem mudar status`() {
        val order = orderWithStatus(OrderStatus.AGENDADA_PRESENCIAL)

        val result = service.updateSchedule(order.id!!, validRequest)

        assertEquals(OrderStatus.AGENDADA_PRESENCIAL, result.status)
        assertEquals(tomorrow, result.scheduledDate)
        assertEquals(ScheduledShift.MANHA, result.scheduledShift)
    }

    @Test
    fun `OS em AGENDADA_DELIVERY deve atualizar campos sem mudar status`() {
        val order = orderWithStatus(OrderStatus.AGENDADA_DELIVERY)

        val result = service.updateSchedule(order.id!!, validRequest)

        assertEquals(OrderStatus.AGENDADA_DELIVERY, result.status)
        assertEquals(tomorrow, result.scheduledDate)
        assertEquals(ScheduledShift.MANHA, result.scheduledShift)
    }

    @Test
    fun `OS em FINALIZADA deve transicionar para AGENDADA_PRESENCIAL`() {
        val order = orderWithStatus(OrderStatus.FINALIZADA)

        val result = service.updateSchedule(order.id!!, validRequest)

        assertEquals(OrderStatus.AGENDADA_PRESENCIAL, result.status)
        assertEquals(tomorrow, result.scheduledDate)
        assertEquals(ScheduledShift.MANHA, result.scheduledShift)
    }

    @Test
    fun `OS em AGUARDANDO_AGENDAMENTO deve transicionar para AGENDADA_PRESENCIAL`() {
        val order = orderWithStatus(OrderStatus.AGUARDANDO_AGENDAMENTO)

        val result = service.updateSchedule(order.id!!, validRequest)

        assertEquals(OrderStatus.AGENDADA_PRESENCIAL, result.status)
        assertEquals(tomorrow, result.scheduledDate)
        assertEquals(ScheduledShift.MANHA, result.scheduledShift)
    }

    @Test
    fun `OS em ENTREGUE deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.ENTREGUE)

        assertThrows<InvalidOrderStatusException> {
            service.updateSchedule(order.id!!, validRequest)
        }
    }

    @Test
    fun `OS em AGUARDANDO_CONFERENCIA deve lancar InvalidOrderStatusException`() {
        val order = orderWithStatus(OrderStatus.AGUARDANDO_CONFERENCIA)

        assertThrows<InvalidOrderStatusException> {
            service.updateSchedule(order.id!!, validRequest)
        }
    }

    @Test
    fun `OS inexistente deve lancar OrderNotFoundException`() {
        val unknownId = UUID.randomUUID()
        whenever(orderRepository.findById(unknownId)).thenReturn(Optional.empty())

        assertThrows<OrderNotFoundException> {
            service.updateSchedule(unknownId, validRequest)
        }
    }
}
