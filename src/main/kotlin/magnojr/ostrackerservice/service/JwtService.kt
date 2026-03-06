package magnojr.ostrackerservice.service

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import magnojr.ostrackerservice.config.SecurityProperties
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    private val properties: SecurityProperties,
) {
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(properties.jwtSecret.toByteArray())
    }

    fun generateUserToken(
        userId: String,
        email: String,
        role: String,
    ): String =
        Jwts
            .builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + properties.jwtExpiration))
            .signWith(signingKey)
            .compact()

    fun parsePrincipal(token: String): AuthPrincipalClaims {
        val claims = parseClaims(token)
        val userId = claims.subject?.takeIf { it.isNotBlank() } ?: throw JwtException("Missing subject")
        val email = claims["email"]?.toString()?.takeIf { it.isNotBlank() } ?: throw JwtException("Missing email claim")
        val role = claims["role"]?.toString()?.takeIf { it.isNotBlank() } ?: throw JwtException("Missing role claim")
        return AuthPrincipalClaims(userId = userId, email = email, role = role)
    }

    fun isTokenValid(token: String): Boolean =
        try {
            parsePrincipal(token)
            true
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }

    private fun parseClaims(token: String): io.jsonwebtoken.Claims {
        val claims =
            Jwts
                .parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload

        if (claims.expiration == null || !claims.expiration.after(Date())) {
            throw JwtException("Expired token")
        }

        return claims
    }
}
