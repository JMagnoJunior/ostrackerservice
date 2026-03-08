package magnojr.ostrackerservice.controller

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class CreateContactLogRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 2000)
    val note: String,
)

data class ContactLogResponse(
    val id: UUID,
    val orderId: UUID,
    val authorId: UUID,
    val authorName: String,
    val note: String,
    val createdAt: OffsetDateTime,
)
