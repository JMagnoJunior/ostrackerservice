package magnojr.ostrackerservice.controller

import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.NotNull
import magnojr.ostrackerservice.model.ScheduledShift
import java.time.LocalDate

data class OrderScheduleRequest(
    @field:NotNull
    @field:FutureOrPresent
    val scheduledDate: LocalDate?,
    @field:NotNull
    val scheduledShift: ScheduledShift?,
)
