package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.model.OrderStatus
import java.time.OffsetDateTime
import java.util.UUID

data class CallQueueOrderDTO(
    val id: UUID,
    val clientName: String,
    val clientPhone: String,
    val status: OrderStatus,
    val finishedAt: OffsetDateTime,
    val inactiveHours: Long,
)
