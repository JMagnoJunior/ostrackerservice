package magnojr.ostrackerservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import magnojr.ostrackerservice.config.SecurityProperties
import magnojr.ostrackerservice.service.JwtService
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
) {
    @Operation(summary = "Obtain a system JWT token")
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
        return TokenResponse(jwtService.generateSystemToken())
    }
}

data class TokenRequest(
    val clientSecret: String,
)

data class TokenResponse(
    val token: String,
)
