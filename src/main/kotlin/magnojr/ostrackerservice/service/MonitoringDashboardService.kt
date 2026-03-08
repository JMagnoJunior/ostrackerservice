package magnojr.ostrackerservice.service

import io.micrometer.core.instrument.MeterRegistry
import magnojr.ostrackerservice.config.MonitoringProperties
import magnojr.ostrackerservice.controller.MonitoringCountersDTO
import magnojr.ostrackerservice.controller.MonitoringFilter
import magnojr.ostrackerservice.controller.MonitoringOrderItemDTO
import magnojr.ostrackerservice.controller.MonitoringPageResponseDTO
import magnojr.ostrackerservice.controller.MonitoringStatusVolumeDTO
import magnojr.ostrackerservice.controller.MonitoringSummaryDTO
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Service
class MonitoringDashboardService(
    private val orderRepository: OrderRepository,
    private val monitoringProperties: MonitoringProperties,
    private val meterRegistry: MeterRegistry,
) {
    fun getSummary(referenceAt: OffsetDateTime): MonitoringSummaryDTO {
        meterRegistry.counter("orders.monitoring.summary.requests").increment()

        val counters =
            MonitoringCountersDTO(
                atrasados = orderRepository.count(filterSpecification(MonitoringFilter.ATRASADOS, referenceAt)),
                semAgendamento = orderRepository.count(filterSpecification(MonitoringFilter.SEM_AGENDAMENTO, referenceAt)),
                proximosDescartes =
                    orderRepository.count(
                        filterSpecification(MonitoringFilter.PROXIMOS_DESCARTES, referenceAt),
                    ),
            )

        val countsByStatus: Map<OrderStatus, Long> =
            orderRepository.countGroupedByStatus().associate { projection ->
                projection.status to projection.count
            }
        val statusVolumes = OrderStatus.entries.map { status -> MonitoringStatusVolumeDTO(status, countsByStatus[status] ?: 0L) }

        return MonitoringSummaryDTO(
            generatedAt = referenceAt,
            counters = counters,
            statusVolumes = statusVolumes,
        )
    }

    fun listByFilter(
        filter: MonitoringFilter,
        statuses: Set<OrderStatus>?,
        pageable: Pageable,
        referenceAt: OffsetDateTime,
    ): MonitoringPageResponseDTO<MonitoringOrderItemDTO> {
        meterRegistry.counter("orders.monitoring.list.requests", "filter", filter.name).increment()

        var specification: Specification<Order> = filterSpecification(filter, referenceAt)
        statusSpecification(statuses)?.let { statusSpec -> specification = specification.and(statusSpec) }

        val resultPage = orderRepository.findAll(specification, pageable).map { mapToItem(it, filter, referenceAt) }
        return MonitoringPageResponseDTO.from(resultPage)
    }

    private fun mapToItem(
        order: Order,
        filter: MonitoringFilter,
        referenceAt: OffsetDateTime,
    ): MonitoringOrderItemDTO {
        val orderId = requireNotNull(order.id) { "Order id nao pode ser nulo para monitoramento" }
        val finishedAt = requireNotNull(order.finishedAt) { "Order $orderId sem finishedAt nao pode ser exibida no monitoramento" }
        val discardAt = finishedAt.plusDays(monitoringProperties.discardDeadlineDays)

        return MonitoringOrderItemDTO(
            id = orderId,
            status = order.status,
            clientName = order.clientName,
            clientPhone = order.clientPhone,
            technicalSummary = order.technicalSummary,
            finalValue = order.finalValue,
            finishedAt = finishedAt,
            inactiveHours = ChronoUnit.HOURS.between(finishedAt, referenceAt),
            discardAt = discardAt,
            daysToDiscard = ChronoUnit.DAYS.between(referenceAt, discardAt),
            monitoringFilter = filter,
        )
    }

    private fun filterSpecification(
        filter: MonitoringFilter,
        referenceAt: OffsetDateTime,
    ): Specification<Order> =
        when (filter) {
            MonitoringFilter.SEM_AGENDAMENTO -> semAgendamentoSpecification()
            MonitoringFilter.ATRASADOS -> atrasadosSpecification(referenceAt)
            MonitoringFilter.PROXIMOS_DESCARTES -> proximosDescartesSpecification(referenceAt)
        }

    private fun semAgendamentoSpecification(): Specification<Order> =
        Specification { root, _, criteriaBuilder ->
            val statusPath = root.get<OrderStatus>("status")
            val finishedAtPath = root.get<OffsetDateTime>("finishedAt")
            criteriaBuilder.and(
                statusPath.`in`(SEM_AGENDAMENTO_STATUSES),
                criteriaBuilder.isNotNull(finishedAtPath),
            )
        }

    private fun atrasadosSpecification(referenceAt: OffsetDateTime): Specification<Order> {
        val cutoff = referenceAt.minusHours(monitoringProperties.overdueHours)
        return Specification { root, _, criteriaBuilder ->
            val statusPath = root.get<OrderStatus>("status")
            val finishedAtPath = root.get<OffsetDateTime>("finishedAt")
            criteriaBuilder.and(
                statusPath.`in`(SEM_AGENDAMENTO_STATUSES),
                criteriaBuilder.isNotNull(finishedAtPath),
                criteriaBuilder.lessThanOrEqualTo(finishedAtPath, cutoff),
            )
        }
    }

    private fun proximosDescartesSpecification(referenceAt: OffsetDateTime): Specification<Order> {
        val warningCutoff = referenceAt.minusDays(monitoringProperties.discardWarningDays)
        val deadlineCutoff = referenceAt.minusDays(monitoringProperties.discardDeadlineDays)
        return Specification { root, _, criteriaBuilder ->
            val statusPath = root.get<OrderStatus>("status")
            val finishedAtPath = root.get<OffsetDateTime>("finishedAt")
            criteriaBuilder.and(
                criteriaBuilder.notEqual(statusPath, OrderStatus.ENTREGUE),
                criteriaBuilder.isNotNull(finishedAtPath),
                criteriaBuilder.lessThanOrEqualTo(finishedAtPath, warningCutoff),
                criteriaBuilder.greaterThan(finishedAtPath, deadlineCutoff),
            )
        }
    }

    private fun statusSpecification(statuses: Set<OrderStatus>?): Specification<Order>? {
        if (statuses.isNullOrEmpty()) {
            return null
        }
        return Specification { root, _, _ -> root.get<OrderStatus>("status").`in`(statuses) }
    }

    companion object {
        private val SEM_AGENDAMENTO_STATUSES = setOf(OrderStatus.FINALIZADA, OrderStatus.AGUARDANDO_AGENDAMENTO)
    }
}
