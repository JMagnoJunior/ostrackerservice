package magnojr.ostrackerservice.controller

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class OrderConferenceUpdateDTO(
    val technicalSummary: String?,
    @field:Positive
    val finalValue: BigDecimal?,
    val clientName: String?,
    @field:Pattern(regexp = "^[1-9][0-9]{10,14}$")
    val clientPhone: String?,
)
