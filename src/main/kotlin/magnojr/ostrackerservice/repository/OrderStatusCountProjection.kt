package magnojr.ostrackerservice.repository

import magnojr.ostrackerservice.model.OrderStatus

interface OrderStatusCountProjection {
    val status: OrderStatus
    val count: Long
}
