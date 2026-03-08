package magnojr.ostrackerservice.controller

import jakarta.validation.Valid
import magnojr.ostrackerservice.service.OrderDeliveryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/admin/orders")
class OrderDeliveryController(
    private val orderDeliveryService: OrderDeliveryService,
) {
    @PatchMapping("/{orderId}/delivery/schedule")
    fun scheduleDelivery(
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: DeliveryScheduleRequest,
    ): ResponseEntity<DeliveryScheduleResponse> {
        val order = orderDeliveryService.scheduleDelivery(orderId, request)
        return ResponseEntity.ok(DeliveryScheduleResponse.from(order))
    }

    @PostMapping("/{orderId}/delivery/confirm")
    fun confirmDelivery(
        @PathVariable orderId: UUID,
    ): ResponseEntity<DeliveryConfirmResponse> {
        val order = orderDeliveryService.confirmDelivery(orderId)
        return ResponseEntity.ok(DeliveryConfirmResponse.from(order))
    }
}
