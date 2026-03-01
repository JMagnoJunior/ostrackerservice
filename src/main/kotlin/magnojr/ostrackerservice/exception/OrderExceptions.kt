package magnojr.ostrackerservice.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.NOT_FOUND)
class OrderNotFoundException(
    id: java.util.UUID,
) : RuntimeException("Order with id $id not found")

@ResponseStatus(HttpStatus.CONFLICT)
class InvalidOrderStatusException(
    message: String,
) : RuntimeException(message)
