package magnojr.ostrackerservice.controller

import jakarta.validation.Valid
import magnojr.ostrackerservice.service.OrderConferenceService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/admin/orders/conference")
class OrderConferenceController(
    private val orderConferenceService: OrderConferenceService,
) {
    @GetMapping
    fun listPendingConference(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<OrderConferenceItemDTO> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        return orderConferenceService.listPendingConference(PageRequest.of(safePage, safeSize))
    }

    @PutMapping("/{orderId}")
    fun updateConference(
        @PathVariable orderId: UUID,
        @Valid @RequestBody dto: OrderConferenceUpdateDTO,
    ): OrderConferenceItemDTO = OrderConferenceItemDTO.from(orderConferenceService.updateConference(orderId, dto))

    @PostMapping("/{orderId}/confirm")
    @ResponseStatus(HttpStatus.OK)
    fun confirmConference(
        @PathVariable orderId: UUID,
    ): OrderConferenceItemDTO = OrderConferenceItemDTO.from(orderConferenceService.confirmConference(orderId))
}
