package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.config.MonitoringProperties
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.service.MonitoringDashboardService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

@RestController
@RequestMapping("/admin/orders/monitoring")
class MonitoringDashboardController(
    private val monitoringDashboardService: MonitoringDashboardService,
    private val monitoringProperties: MonitoringProperties,
) {
    @GetMapping("/summary")
    fun getSummary(
        @RequestParam(required = false) referenceAt: OffsetDateTime?,
    ): MonitoringSummaryDTO = monitoringDashboardService.getSummary(referenceAt ?: OffsetDateTime.now())

    @GetMapping
    fun listMonitoringOrders(
        @RequestParam filter: MonitoringFilter,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) status: List<OrderStatus>?,
        @RequestParam(required = false) referenceAt: OffsetDateTime?,
    ): MonitoringPageResponseDTO<MonitoringOrderItemDTO> {
        if (page < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page deve ser maior ou igual a 0")
        }
        if (size < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "size deve ser maior ou igual a 1")
        }
        if (size > monitoringProperties.maxPageSize) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "size deve ser menor ou igual a ${monitoringProperties.maxPageSize}",
            )
        }

        val sortedPageable =
            PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("finishedAt"), Sort.Order.asc("id")),
            )

        return monitoringDashboardService.listByFilter(
            filter = filter,
            statuses = status?.toSet()?.takeIf { it.isNotEmpty() },
            pageable = sortedPageable,
            referenceAt = referenceAt ?: OffsetDateTime.now(),
        )
    }
}
