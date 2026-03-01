package magnojr.ostrackerservice.event

import magnojr.ostrackerservice.model.Order

data class OrderFinishedEvent(
    val order: Order,
)
