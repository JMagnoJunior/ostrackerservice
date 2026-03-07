package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class OrderConferenceItemDTO(
    val id: UUID,
    val status: OrderStatus,
    val technicalSummary: String?,
    val finalValue: BigDecimal?,
    val clientName: String?,
    val clientPhone: String?,
    val hashAccess: String?,
    val finishedAt: OffsetDateTime?,
) {
    companion object {
        fun from(order: Order) =
            OrderConferenceItemDTO(
                id = order.id!!,
                status = order.status,
                technicalSummary = order.technicalSummary,
                finalValue = order.finalValue,
                clientName = order.clientName,
                clientPhone = order.clientPhone,
                hashAccess = order.hashAccess,
                finishedAt = order.finishedAt,
            )
    }
}
