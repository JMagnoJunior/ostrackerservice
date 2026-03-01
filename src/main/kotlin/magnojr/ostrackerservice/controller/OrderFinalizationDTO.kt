package magnojr.ostrackerservice.controller

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class OrderFinalizationDTO(
    val technicalSummary: String?,
    @field:NotNull
    @field:Positive
    val finalValue: BigDecimal?,
    @field:NotBlank
    val clientName: String,
    @field:NotBlank
    @field:Pattern(regexp = "^[1-9][0-9]{10,14}$")
    val clientPhone: String,
)
