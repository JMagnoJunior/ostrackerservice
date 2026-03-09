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
import magnojr.ostrackerservice.model.ScheduledShift
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
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
                aguardandoConferencia =
                    orderRepository.count(
                        filterSpecification(MonitoringFilter.AGUARDANDO_CONFERENCIA, referenceAt),
                    ),
                agendadas = orderRepository.count(filterSpecification(MonitoringFilter.AGENDADAS, referenceAt)),
                noShow = orderRepository.count(filterSpecification(MonitoringFilter.NO_SHOW, referenceAt)),
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
        val finishedAt = order.finishedAt
        val discardAt = finishedAt?.plusDays(monitoringProperties.discardDeadlineDays)

        return MonitoringOrderItemDTO(
            id = orderId,
            status = order.status,
            clientName = order.clientName,
            clientPhone = order.clientPhone,
            technicalSummary = order.technicalSummary,
            finalValue = order.finalValue,
            finishedAt = finishedAt,
            inactiveHours = if (finishedAt != null) ChronoUnit.HOURS.between(finishedAt, referenceAt) else 0L,
            discardAt = discardAt ?: referenceAt,
            daysToDiscard = if (discardAt != null) ChronoUnit.DAYS.between(referenceAt, discardAt) else 0L,
            monitoringFilter = filter,
            scheduledDate = order.scheduledDate,
            scheduledShift = order.scheduledShift,
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
            MonitoringFilter.AGUARDANDO_CONFERENCIA -> aguardandoConferenciaSpecification()
            MonitoringFilter.AGENDADAS -> agendadasSpecification()
            MonitoringFilter.NO_SHOW -> noShowSpecification(referenceAt)
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

    private fun aguardandoConferenciaSpecification(): Specification<Order> =
        Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<OrderStatus>("status"), OrderStatus.AGUARDANDO_CONFERENCIA)
        }

    private fun agendadasSpecification(): Specification<Order> =
        Specification { root, _, criteriaBuilder ->
            root.get<OrderStatus>("status").`in`(AGENDADAS_STATUSES)
        }

    private fun noShowSpecification(referenceAt: OffsetDateTime): Specification<Order> {
        val zone = ZoneId.of(monitoringProperties.noShowZoneId)
        val today = referenceAt.atZoneSameInstant(zone).toLocalDate()

        val endOfManha = today.atTime(LocalTime.of(monitoringProperties.noShowShiftEndManha, 0)).atZone(zone).toOffsetDateTime()
        val endOfTarde = today.atTime(LocalTime.of(monitoringProperties.noShowShiftEndTarde, 0)).atZone(zone).toOffsetDateTime()
        val endOfNoite = today.atTime(LocalTime.of(monitoringProperties.noShowShiftEndNoite, 59)).atZone(zone).toOffsetDateTime()

        val cutoffManha = if (referenceAt.isAfter(endOfManha)) today else today.minusDays(1)
        val cutoffTarde = if (referenceAt.isAfter(endOfTarde)) today else today.minusDays(1)
        val cutoffNoite = if (referenceAt.isAfter(endOfNoite)) today else today.minusDays(1)

        return Specification { root, _, cb ->
            val statusPredicate = root.get<OrderStatus>("status").`in`(AGENDADAS_STATUSES)
            val scheduledDatePath = root.get<LocalDate>("scheduledDate")
            val scheduledShiftPath = root.get<ScheduledShift>("scheduledShift")

            val manha =
                cb.and(
                    cb.equal(scheduledShiftPath, ScheduledShift.MANHA),
                    cb.lessThanOrEqualTo(scheduledDatePath, cutoffManha),
                )
            val tarde =
                cb.and(
                    cb.equal(scheduledShiftPath, ScheduledShift.TARDE),
                    cb.lessThanOrEqualTo(scheduledDatePath, cutoffTarde),
                )
            val noite =
                cb.and(
                    cb.equal(scheduledShiftPath, ScheduledShift.NOITE),
                    cb.lessThanOrEqualTo(scheduledDatePath, cutoffNoite),
                )

            cb.and(
                statusPredicate,
                cb.isNotNull(scheduledDatePath),
                cb.isNotNull(scheduledShiftPath),
                cb.or(manha, tarde, noite),
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
        private val AGENDADAS_STATUSES = setOf(OrderStatus.AGENDADA_PRESENCIAL, OrderStatus.AGENDADA_DELIVERY)
    }
}
