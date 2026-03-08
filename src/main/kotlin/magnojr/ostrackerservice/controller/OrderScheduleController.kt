package magnojr.ostrackerservice.controller

import jakarta.validation.Valid
import magnojr.ostrackerservice.service.OrderScheduleService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/admin/orders")
class OrderScheduleController(
    private val orderScheduleService: OrderScheduleService,
) {
    @PatchMapping("/{orderId}/schedule")
    fun updateSchedule(
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: OrderScheduleRequest,
    ): ResponseEntity<OrderScheduleResponse> {
        val order = orderScheduleService.updateSchedule(orderId, request)
        return ResponseEntity.ok(OrderScheduleResponse.from(order))
    }
}
