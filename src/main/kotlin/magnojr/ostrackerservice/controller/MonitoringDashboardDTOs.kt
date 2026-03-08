package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.model.OrderStatus
import org.springframework.data.domain.Page
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

enum class MonitoringFilter {
    ATRASADOS,
    SEM_AGENDAMENTO,
    PROXIMOS_DESCARTES,
}

data class MonitoringSummaryDTO(
    val generatedAt: OffsetDateTime,
    val counters: MonitoringCountersDTO,
    val statusVolumes: List<MonitoringStatusVolumeDTO>,
)

data class MonitoringCountersDTO(
    val atrasados: Long,
    val semAgendamento: Long,
    val proximosDescartes: Long,
)

data class MonitoringStatusVolumeDTO(
    val status: OrderStatus,
    val count: Long,
)

data class MonitoringOrderItemDTO(
    val id: UUID,
    val status: OrderStatus,
    val clientName: String?,
    val clientPhone: String?,
    val technicalSummary: String?,
    val finalValue: BigDecimal?,
    val finishedAt: OffsetDateTime,
    val inactiveHours: Long,
    val discardAt: OffsetDateTime,
    val daysToDiscard: Long,
    val monitoringFilter: MonitoringFilter,
)

data class MonitoringPageResponseDTO<T : Any>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun <T : Any> from(page: Page<T>): MonitoringPageResponseDTO<T> =
            MonitoringPageResponseDTO(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                hasNext = page.hasNext(),
            )
    }
}
