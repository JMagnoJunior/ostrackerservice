package magnojr.ostrackerservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import magnojr.ostrackerservice.config.SecurityProperties
import magnojr.ostrackerservice.service.GoogleOAuthLoginService
import magnojr.ostrackerservice.service.GoogleTokenVerificationException
import magnojr.ostrackerservice.service.JwtService
import magnojr.ostrackerservice.service.SuperuserBootstrapService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val jwtService: JwtService,
    private val securityProperties: SecurityProperties,
    private val superuserBootstrapService: SuperuserBootstrapService,
    private val googleOAuthLoginService: GoogleOAuthLoginService,
) {
    @Operation(summary = "Obtain a JWT token bound to the primary active superuser")
    @SecurityRequirements
    @PostMapping("/token")
    fun token(
        @RequestBody request: TokenRequest,
    ): TokenResponse {
        val expected = securityProperties.clientSecret.toByteArray()
        val provided = request.clientSecret.toByteArray()
        if (!MessageDigest.isEqual(expected, provided)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid client secret")
        }

        val superuser = superuserBootstrapService.ensurePrimarySuperuser()
        val superuserId = requireNotNull(superuser.id) { "Primary superuser must have a persisted identifier" }
        return TokenResponse(
            jwtService.generateUserToken(
                userId = superuserId.toString(),
                email = superuser.email,
                role = superuser.role.name,
                status = superuser.status.name,
            ),
        )
    }

    @Operation(summary = "Authenticate via Google OAuth ID token")
    @SecurityRequirements
    @PostMapping("/google")
    fun googleLogin(
        @RequestBody request: GoogleLoginRequest,
    ): AuthSessionResponse {
        try {
            val result = googleOAuthLoginService.authenticate(request.idToken)
            return AuthSessionResponse(token = result.token, status = result.status, pending = result.isPending)
        } catch (e: GoogleTokenVerificationException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, e.message)
        }
    }
}

data class TokenRequest(
    val clientSecret: String,
)

data class TokenResponse(
    val token: String,
)

data class GoogleLoginRequest(
    val idToken: String,
)

data class AuthSessionResponse(
    val token: String,
    val status: String,
    val pending: Boolean,
)
