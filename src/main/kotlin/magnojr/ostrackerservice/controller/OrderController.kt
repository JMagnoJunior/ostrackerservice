package magnojr.ostrackerservice.controller

import jakarta.validation.Valid
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.service.OrderService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
) {
    @PostMapping("/finalizations")
    @ResponseStatus(HttpStatus.CREATED)
    fun createFinalizedOrder(
        @Valid @RequestBody dto: OrderFinalizationDTO,
    ): Order =
        orderService.createFinalizedOrder(
            technicalSummary = dto.technicalSummary,
            finalValue = dto.finalValue!!,
            clientName = dto.clientName,
            clientPhone = dto.clientPhone,
        )
}
