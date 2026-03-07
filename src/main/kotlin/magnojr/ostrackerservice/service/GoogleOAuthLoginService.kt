package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.model.AppUser
import magnojr.ostrackerservice.model.UserRole
import magnojr.ostrackerservice.model.UserStatus
import magnojr.ostrackerservice.repository.AppUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class GoogleOAuthLoginService(
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    private val appUserRepository: AppUserRepository,
    private val jwtService: JwtService,
) {
    @Transactional
    fun authenticate(idToken: String): OAuthAuthenticationResult {
        val claims = googleIdTokenVerifier.verify(idToken)
        val now = OffsetDateTime.now()

        val user =
            appUserRepository.findByEmailIgnoreCase(claims.email).orElseGet {
                appUserRepository.save(
                    AppUser(
                        email = claims.email,
                        displayName = claims.name ?: claims.email,
                        role = UserRole.PENDENTE,
                        status = UserStatus.PENDENTE_APROVACAO,
                        firstLoginAt = now,
                    ),
                )
            }

        if (user.firstLoginAt == null) {
            user.firstLoginAt = now
            appUserRepository.save(user)
        }

        val userId = requireNotNull(user.id) { "User must have a persisted id" }
        val token =
            jwtService.generateUserToken(
                userId = userId.toString(),
                email = user.email,
                role = user.role.name,
                status = user.status.name,
            )

        return OAuthAuthenticationResult(
            token = token,
            status = user.status.name,
            isPending = user.isPendingApproval(),
        )
    }
}
