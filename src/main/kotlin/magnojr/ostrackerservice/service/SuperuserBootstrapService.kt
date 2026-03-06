package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.config.SecurityProperties
import magnojr.ostrackerservice.model.AppUser
import magnojr.ostrackerservice.model.UserRole
import magnojr.ostrackerservice.model.UserStatus
import magnojr.ostrackerservice.repository.AppUserRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SuperuserBootstrapService(
    private val appUserRepository: AppUserRepository,
    private val securityProperties: SecurityProperties,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun bootstrapOnStartup() {
        ensurePrimarySuperuser()
    }

    @Transactional
    fun ensurePrimarySuperuser(): AppUser {
        val primaryCount = appUserRepository.countPrimaryActiveSuperusers()
        if (primaryCount > 1) {
            throw IllegalStateException(
                "Inconsistent state: expected at most one active primary superuser, found $primaryCount",
            )
        }

        if (primaryCount == 1L) {
            return appUserRepository.findPrimaryActiveSuperuser().orElseThrow {
                IllegalStateException("Primary superuser count is 1 but record was not found")
            }
        }

        if (!securityProperties.superuser.bootstrapEnabled) {
            throw IllegalStateException("Primary superuser not found and bootstrap is disabled")
        }

        val normalizedEmail =
            securityProperties.superuser.email
                .trim()
                .lowercase()
        val configuredDisplayName =
            securityProperties.superuser.displayName
                .trim()
        require(normalizedEmail.isNotBlank()) { "Superuser email must not be blank" }
        require(configuredDisplayName.isNotBlank()) { "Superuser display name must not be blank" }

        val existingByEmail = appUserRepository.findByEmailIgnoreCase(normalizedEmail)
        if (existingByEmail.isPresent) {
            val existing = existingByEmail.get()
            existing.email = normalizedEmail
            existing.displayName = configuredDisplayName
            existing.role = UserRole.SUPERUSUARIO
            existing.status = UserStatus.ATIVO
            existing.isPrimarySuperuser = true
            return appUserRepository.save(existing)
        }

        return appUserRepository.save(
            AppUser(
                email = normalizedEmail,
                displayName = configuredDisplayName,
                role = UserRole.SUPERUSUARIO,
                status = UserStatus.ATIVO,
                isPrimarySuperuser = true,
            ),
        )
    }
}
