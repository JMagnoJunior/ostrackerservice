package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.config.SecurityProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtServiceTest {
    private val properties =
        SecurityProperties().apply {
            jwtSecret = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437"
            jwtExpiration = 60_000
            clientSecret = "test-client-secret"
        }
    private val jwtService = JwtService(properties)

    @Test
    fun `should include and extract role claim`() {
        val userId = UUID.randomUUID().toString()
        val token = jwtService.generateUserToken(userId, "superuser@ostracker.local", "SUPERUSUARIO")

        val claims = jwtService.parsePrincipal(token)

        assertEquals(userId, claims.userId)
        assertEquals("superuser@ostracker.local", claims.email)
        assertEquals("SUPERUSUARIO", claims.role)
    }

    @Test
    fun `should return false for invalid token`() {
        assertFalse(jwtService.isTokenValid("invalid-token"))
    }

    @Test
    fun `should return true for valid token`() {
        val token =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "user@ostracker.local",
                role = "TECNICO",
            )

        assertTrue(jwtService.isTokenValid(token))
    }
}
