package magnojr.ostrackerservice.controller

import jakarta.validation.Valid
import magnojr.ostrackerservice.service.AuthPrincipalClaims
import magnojr.ostrackerservice.service.OrderContactLogService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/admin/orders/{orderId}/contact-logs")
class OrderContactLogController(
    private val orderContactLogService: OrderContactLogService,
) {
    @GetMapping
    fun listLogs(
        @PathVariable orderId: UUID,
    ): List<ContactLogResponse> = orderContactLogService.listLogs(orderId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createLog(
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: CreateContactLogRequest,
        authentication: Authentication,
    ): ContactLogResponse {
        val claims = authentication.principal as AuthPrincipalClaims
        val authorId = UUID.fromString(claims.userId)
        return orderContactLogService.createLog(orderId, authorId, request.note)
    }
}
