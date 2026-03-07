package magnojr.ostrackerservice.config

import magnojr.ostrackerservice.service.AuthPrincipalClaims
import magnojr.ostrackerservice.service.JwtService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthenticationFilterTest {
    private val jwtService: JwtService = mock()
    private val filter = JwtAuthenticationFilter(jwtService)

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should inject role authority when token is valid`() {
        whenever(jwtService.parsePrincipal("valid-token"))
            .thenReturn(
                AuthPrincipalClaims(
                    userId = "1",
                    email = "superuser@ostracker.local",
                    role = "SUPERUSUARIO",
                    status = "ATIVO",
                ),
            )
        val request =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer valid-token")
            }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertEquals("ROLE_SUPERUSUARIO", authentication!!.authorities.first().authority)
    }

    @Test
    fun `should keep context empty when token is invalid`() {
        whenever(jwtService.parsePrincipal("invalid-token")).thenThrow(IllegalArgumentException("invalid"))
        val request =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer invalid-token")
            }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
