package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallQueueServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val callQueueService = CallQueueService(orderRepository)

    private fun order(
        status: OrderStatus,
        finishedAt: OffsetDateTime,
    ) = Order(
        id = UUID.randomUUID(),
        status = status,
        finalValue = BigDecimal("100.00"),
        finishedAt = finishedAt,
        hashAccess = UUID.randomUUID().toString(),
        clientName = "Cliente Teste",
        clientPhone = "5511999999999",
    )

    @Test
    fun `OS com finishedAt exatamente 24h atras nao deve aparecer`() {
        val exactly24hAgo = OffsetDateTime.now().minusHours(24)
        whenever(orderRepository.findCallQueue(any(), any(), any()))
            .thenReturn(PageImpl(emptyList()))

        val result = callQueueService.getCallQueue(PageRequest.of(0, 50))

        assertTrue(result.content.isEmpty())
    }

    @Test
    fun `OS com finishedAt 25h atras e status FINALIZADA deve aparecer`() {
        val order = order(OrderStatus.FINALIZADA, OffsetDateTime.now().minusHours(25))
        whenever(orderRepository.findCallQueue(any(), any(), any()))
            .thenReturn(PageImpl(listOf(order)))

        val result = callQueueService.getCallQueue(PageRequest.of(0, 50))

        assertEquals(1, result.totalElements)
        assertEquals(order.id, result.content.first().id)
        assertEquals(OrderStatus.FINALIZADA, result.content.first().status)
    }

    @Test
    fun `OS com finishedAt 48h atras e status AGUARDANDO_AGENDAMENTO deve aparecer`() {
        val order = order(OrderStatus.AGUARDANDO_AGENDAMENTO, OffsetDateTime.now().minusHours(48))
        whenever(orderRepository.findCallQueue(any(), any(), any()))
            .thenReturn(PageImpl(listOf(order)))

        val result = callQueueService.getCallQueue(PageRequest.of(0, 50))

        assertEquals(1, result.totalElements)
        assertEquals(OrderStatus.AGUARDANDO_AGENDAMENTO, result.content.first().status)
    }

    @Test
    fun `OS com status AGENDADA_PRESENCIAL nao deve aparecer`() {
        whenever(orderRepository.findCallQueue(any(), any(), any()))
            .thenReturn(PageImpl(emptyList()))

        val result = callQueueService.getCallQueue(PageRequest.of(0, 50))

        assertTrue(result.content.isEmpty())
    }

    @Test
    fun `OS com status ENTREGUE nao deve aparecer`() {
        whenever(orderRepository.findCallQueue(any(), any(), any()))
            .thenReturn(PageImpl(emptyList()))

        val result = callQueueService.getCallQueue(PageRequest.of(0, 50))

        assertTrue(result.content.isEmpty())
    }

    @Test
    fun `calculo de inactiveHours para OS de 36h atras deve retornar 36`() {
        val finishedAt = OffsetDateTime.now().minusHours(36)
        val order = order(OrderStatus.FINALIZADA, finishedAt)
        whenever(orderRepository.findCallQueue(any(), any(), any()))
            .thenReturn(PageImpl(listOf(order)))

        val result = callQueueService.getCallQueue(PageRequest.of(0, 50))

        val dto = result.content.first()
        assertTrue(dto.inactiveHours >= 35, "inactiveHours deve ser ao menos 35, foi ${dto.inactiveHours}")
        assertTrue(dto.inactiveHours <= 37, "inactiveHours deve ser no maximo 37, foi ${dto.inactiveHours}")
    }
}
