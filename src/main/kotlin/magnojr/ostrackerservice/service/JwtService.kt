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

    fun generateSystemToken(): String =
        Jwts
            .builder()
            .subject("system")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + properties.jwtExpiration))
            .signWith(signingKey)
            .compact()

    fun isTokenValid(token: String): Boolean =
        try {
            Jwts
                .parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
                .expiration
                .after(Date())
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
}
