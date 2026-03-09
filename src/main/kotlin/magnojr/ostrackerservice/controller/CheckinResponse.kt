package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import java.time.OffsetDateTime
import java.util.UUID

data class CheckinResponse(
    val id: UUID,
    val status: OrderStatus,
    val deliveredAt: OffsetDateTime,
) {
    companion object {
        fun from(order: Order) =
            CheckinResponse(
                id = order.id!!,
                status = order.status,
                deliveredAt = order.deliveredAt!!,
            )
    }
}
