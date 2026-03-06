package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.model.UserRole
import magnojr.ostrackerservice.model.UserStatus
import magnojr.ostrackerservice.repository.AppUserRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/access")
class AccessManagementController(
    private val appUserRepository: AppUserRepository,
) {
    @GetMapping("/users")
    fun listUsers(
        @RequestParam(required = false) status: UserStatus?,
    ): List<AccessUserDTO> {
        val users =
            if (status == null) {
                appUserRepository.findAllByOrderByCreatedAtDesc()
            } else {
                appUserRepository.findAllByStatusOrderByCreatedAtDesc(status)
            }

        return users.map { user ->
            AccessUserDTO(
                id = user.id!!,
                email = user.email,
                displayName = user.displayName,
                role = user.role,
                status = user.status,
                isPrimarySuperuser = user.isPrimarySuperuser,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt,
            )
        }
    }
}

data class AccessUserDTO(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val status: UserStatus,
    val isPrimarySuperuser: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
