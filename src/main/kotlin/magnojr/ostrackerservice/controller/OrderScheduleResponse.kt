package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.model.ScheduledShift
import java.time.LocalDate
import java.util.UUID

data class OrderScheduleResponse(
    val id: UUID,
    val status: OrderStatus,
    val scheduledDate: LocalDate?,
    val scheduledShift: ScheduledShift?,
    val clientName: String?,
    val clientPhone: String?,
) {
    companion object {
        fun from(order: Order) =
            OrderScheduleResponse(
                id = order.id!!,
                status = order.status,
                scheduledDate = order.scheduledDate,
                scheduledShift = order.scheduledShift,
                clientName = order.clientName,
                clientPhone = order.clientPhone,
            )
    }
}
