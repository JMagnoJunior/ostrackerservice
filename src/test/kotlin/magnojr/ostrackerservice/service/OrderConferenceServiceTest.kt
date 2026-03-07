package magnojr.ostrackerservice.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import magnojr.ostrackerservice.controller.OrderConferenceUpdateDTO
import magnojr.ostrackerservice.event.OrderFinishedEvent
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

class OrderConferenceServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()
    private val counter: Counter = mock()
    private val meterRegistry: MeterRegistry =
        mock<MeterRegistry>().also { reg ->
            whenever(reg.counter(any<String>())).thenReturn(counter)
        }

    private val service = OrderConferenceService(orderRepository, eventPublisher, meterRegistry)

    private fun pendingOrder(
        id: UUID = UUID.randomUUID(),
        technicalSummary: String? = "Resumo tecnico",
        finalValue: BigDecimal? = BigDecimal("100.00"),
        clientName: String? = "Cliente Teste",
        clientPhone: String? = "5511999999999",
        hashAccess: String? = null,
    ) = Order(
        id = id,
        status = OrderStatus.AGUARDANDO_CONFERENCIA,
        technicalSummary = technicalSummary,
        finalValue = finalValue,
        finishedAt = OffsetDateTime.now(),
        hashAccess = hashAccess,
        clientName = clientName,
        clientPhone = clientPhone,
    )

    @Test
    fun `listPendingConference deve retornar pagina filtrada por AGUARDANDO_CONFERENCIA`() {
        val order = pendingOrder()
        whenever(
            orderRepository.findByStatusOrderByFinishedAtAsc(
                OrderStatus.AGUARDANDO_CONFERENCIA,
                PageRequest.of(0, 10),
            ),
        ).thenReturn(PageImpl(listOf(order)))

        val result = service.listPendingConference(PageRequest.of(0, 10))

        assertEquals(1, result.totalElements)
        assertEquals(order.id, result.content.first().id)
        assertEquals(OrderStatus.AGUARDANDO_CONFERENCIA, result.content.first().status)
    }

    @Test
    fun `updateConference deve atualizar campos fornecidos e retornar OS salva`() {
        val orderId = UUID.randomUUID()
        val order = pendingOrder(id = orderId, technicalSummary = "Antigo resumo")
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] }

        val dto =
            OrderConferenceUpdateDTO(
                technicalSummary = "Novo resumo",
                finalValue = BigDecimal("250.00"),
                clientName = null,
                clientPhone = null,
            )

        val result = service.updateConference(orderId, dto)

        assertEquals("Novo resumo", result.technicalSummary)
        assertEquals(BigDecimal("250.00"), result.finalValue)
        assertEquals("Cliente Teste", result.clientName)
    }

    @Test
    fun `updateConference deve lancar InvalidOrderStatusException ao editar OS fora de AGUARDANDO_CONFERENCIA`() {
        val orderId = UUID.randomUUID()
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.empty())
        whenever(orderRepository.findById(orderId))
            .thenReturn(
                Optional.of(
                    Order(id = orderId, status = OrderStatus.FINALIZADA, finishedAt = OffsetDateTime.now()),
                ),
            )

        assertThrows<InvalidOrderStatusException> {
            service.updateConference(orderId, OrderConferenceUpdateDTO(null, null, null, null))
        }
    }

    @Test
    fun `updateConference deve lancar OrderNotFoundException quando OS nao existe`() {
        val orderId = UUID.randomUUID()
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.empty())
        whenever(orderRepository.findById(orderId)).thenReturn(Optional.empty())

        assertThrows<OrderNotFoundException> {
            service.updateConference(orderId, OrderConferenceUpdateDTO(null, null, null, null))
        }
    }

    @Test
    fun `confirmConference deve gerar hashAccess quando ausente e transicionar para FINALIZADA`() {
        val orderId = UUID.randomUUID()
        val order = pendingOrder(id = orderId, hashAccess = null)
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = service.confirmConference(orderId)

        assertEquals(OrderStatus.FINALIZADA, result.status)
        assertNotNull(result.hashAccess)
    }

    @Test
    fun `confirmConference deve manter hashAccess existente`() {
        val orderId = UUID.randomUUID()
        val order = pendingOrder(id = orderId, hashAccess = "hash-existente")
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = service.confirmConference(orderId)

        assertEquals("hash-existente", result.hashAccess)
    }

    @Test
    fun `confirmConference deve publicar OrderFinishedEvent apos persistir FINALIZADA`() {
        val orderId = UUID.randomUUID()
        val order = pendingOrder(id = orderId)
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.of(order))
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] }

        service.confirmConference(orderId)

        val captor = argumentCaptor<OrderFinishedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(OrderStatus.FINALIZADA, captor.firstValue.order.status)
    }

    @Test
    fun `confirmConference deve lancar InvalidOrderStatusException quando OS nao esta em AGUARDANDO_CONFERENCIA`() {
        val orderId = UUID.randomUUID()
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.empty())
        whenever(orderRepository.findById(orderId))
            .thenReturn(
                Optional.of(
                    Order(id = orderId, status = OrderStatus.FINALIZADA, finishedAt = OffsetDateTime.now()),
                ),
            )

        assertThrows<InvalidOrderStatusException> {
            service.confirmConference(orderId)
        }
    }

    @Test
    fun `confirmConference deve falhar quando technicalSummary esta vazio`() {
        val orderId = UUID.randomUUID()
        val order = pendingOrder(id = orderId, technicalSummary = "")
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.of(order))

        assertThrows<IllegalArgumentException> {
            service.confirmConference(orderId)
        }
    }

    @Test
    fun `confirmConference deve falhar quando finalValue e nulo ou zero`() {
        val orderId = UUID.randomUUID()
        val order = pendingOrder(id = orderId, finalValue = null)
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.of(order))

        assertThrows<IllegalArgumentException> {
            service.confirmConference(orderId)
        }
    }

    @Test
    fun `confirmConference deve falhar quando clientPhone e invalido`() {
        val orderId = UUID.randomUUID()
        val order = pendingOrder(id = orderId, clientPhone = "123")
        whenever(orderRepository.findByIdAndStatus(orderId, OrderStatus.AGUARDANDO_CONFERENCIA))
            .thenReturn(Optional.of(order))

        assertThrows<IllegalArgumentException> {
            service.confirmConference(orderId)
        }
    }
}
