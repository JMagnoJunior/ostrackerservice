package magnojr.ostrackerservice.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import magnojr.ostrackerservice.config.MonitoringProperties
import magnojr.ostrackerservice.controller.MonitoringFilter
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import magnojr.ostrackerservice.repository.OrderStatusCountProjection
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals

class MonitoringDashboardServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val monitoringProperties = MonitoringProperties()
    private val meterRegistry = SimpleMeterRegistry()
    private val service = MonitoringDashboardService(orderRepository, monitoringProperties, meterRegistry)

    @Test
    fun `summary deve retornar contadores e volumes por status`() {
        whenever(orderRepository.count(any<Specification<Order>>()))
            .thenReturn(3L, 5L, 2L)
        whenever(orderRepository.countGroupedByStatus())
            .thenReturn(
                listOf(
                    projection(OrderStatus.FINALIZADA, 7L),
                    projection(OrderStatus.ENTREGUE, 1L),
                ),
            )

        val referenceAt = OffsetDateTime.parse("2026-03-08T12:00:00Z")
        val summary = service.getSummary(referenceAt)

        assertEquals(referenceAt, summary.generatedAt)
        assertEquals(3L, summary.counters.atrasados)
        assertEquals(5L, summary.counters.semAgendamento)
        assertEquals(2L, summary.counters.proximosDescartes)
        assertEquals(OrderStatus.entries.size, summary.statusVolumes.size)
        assertEquals(7L, summary.statusVolumes.first { it.status == OrderStatus.FINALIZADA }.count)
        assertEquals(1L, summary.statusVolumes.first { it.status == OrderStatus.ENTREGUE }.count)
        assertEquals(0L, summary.statusVolumes.first { it.status == OrderStatus.ABERTA }.count)
    }

    @Test
    fun `listagem deve calcular campos derivados do item`() {
        val referenceAt = OffsetDateTime.parse("2026-03-08T12:00:00Z")
        val finishedAt = referenceAt.minusDays(10)
        val order =
            Order(
                id = UUID.randomUUID(),
                status = OrderStatus.FINALIZADA,
                technicalSummary = "Resumo",
                finalValue = BigDecimal("320.50"),
                finishedAt = finishedAt,
                hashAccess = "hash-list",
                clientName = "Cliente",
                clientPhone = "5511999990000",
            )

        whenever(orderRepository.findAll(any<Specification<Order>>(), any<Pageable>()))
            .thenReturn(PageImpl(listOf(order), PageRequest.of(0, 50), 1))

        val response =
            service.listByFilter(
                filter = MonitoringFilter.ATRASADOS,
                statuses = null,
                pageable = PageRequest.of(0, 50),
                referenceAt = referenceAt,
            )

        assertEquals(1L, response.totalElements)
        assertEquals(1, response.content.size)
        val item = response.content.first()
        assertEquals(order.id, item.id)
        assertEquals(240L, item.inactiveHours)
        assertEquals(referenceAt.plusDays(110), item.discardAt)
        assertEquals(110L, item.daysToDiscard)
        assertEquals(MonitoringFilter.ATRASADOS, item.monitoringFilter)
    }

    private fun projection(
        status: OrderStatus,
        count: Long,
    ): OrderStatusCountProjection =
        object : OrderStatusCountProjection {
            override val status: OrderStatus = status
            override val count: Long = count
        }
}
