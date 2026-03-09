package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.service.OrderCheckinService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/admin/orders")
class OrderCheckinController(
    private val orderCheckinService: OrderCheckinService,
) {
    @PostMapping("/{orderId}/checkin")
    fun checkin(
        @PathVariable orderId: UUID,
    ): ResponseEntity<CheckinResponse> {
        val order = orderCheckinService.checkin(orderId)
        return ResponseEntity.ok(CheckinResponse.from(order))
    }
}
