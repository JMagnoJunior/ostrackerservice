package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.config.SecurityProperties
import magnojr.ostrackerservice.model.AppUser
import magnojr.ostrackerservice.model.UserRole
import magnojr.ostrackerservice.model.UserStatus
import magnojr.ostrackerservice.repository.AppUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

class GoogleOAuthLoginServiceTest {
    private val verifier: GoogleIdTokenVerifier = mock()
    private val repository: AppUserRepository = mock()
    private val jwtService: JwtService =
        JwtService(
            SecurityProperties().apply {
                jwtSecret = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437"
                jwtExpiration = 60_000
                clientSecret = "test"
            },
        )

    private val service = GoogleOAuthLoginService(verifier, repository, jwtService)

    private val googleClaims =
        GoogleIdentityClaims(
            sub = "google-sub-abc",
            email = "novo@gmail.com",
            emailVerified = true,
            name = "Novo Usuario",
        )

    @Test
    fun `should create pending user on first login with unknown email`() {
        whenever(verifier.verify(any())).thenReturn(googleClaims)
        whenever(repository.findByEmailIgnoreCase("novo@gmail.com")).thenReturn(Optional.empty())

        val savedUser =
            AppUser(
                id = UUID.randomUUID(),
                email = "novo@gmail.com",
                displayName = "Novo Usuario",
                role = UserRole.PENDENTE,
                status = UserStatus.PENDENTE_APROVACAO,
                firstLoginAt = OffsetDateTime.now(),
            )
        whenever(repository.save(any())).thenReturn(savedUser)

        val result = service.authenticate("fake-id-token")

        val captor = argumentCaptor<AppUser>()
        verify(repository).save(captor.capture())
        val captured = captor.firstValue
        assertEquals(UserRole.PENDENTE, captured.role)
        assertEquals(UserStatus.PENDENTE_APROVACAO, captured.status)
        assertNotNull(captured.firstLoginAt)

        assertTrue(result.isPending)
        assertEquals("PENDENTE_APROVACAO", result.status)
    }

    @Test
    fun `should reuse existing user on repeated login without duplication`() {
        val existingUser =
            AppUser(
                id = UUID.randomUUID(),
                email = "novo@gmail.com",
                displayName = "Novo Usuario",
                role = UserRole.PENDENTE,
                status = UserStatus.PENDENTE_APROVACAO,
                firstLoginAt = OffsetDateTime.now().minusDays(1),
            )

        whenever(verifier.verify(any())).thenReturn(googleClaims)
        whenever(repository.findByEmailIgnoreCase("novo@gmail.com")).thenReturn(Optional.of(existingUser))

        val result = service.authenticate("fake-id-token")

        verify(repository, never()).save(any())
        assertTrue(result.isPending)
    }

    @Test
    fun `should keep pending user without promoting role or status`() {
        whenever(verifier.verify(any())).thenReturn(googleClaims)
        whenever(repository.findByEmailIgnoreCase("novo@gmail.com")).thenReturn(Optional.empty())

        val savedUser =
            AppUser(
                id = UUID.randomUUID(),
                email = "novo@gmail.com",
                displayName = "Novo Usuario",
                role = UserRole.PENDENTE,
                status = UserStatus.PENDENTE_APROVACAO,
                firstLoginAt = OffsetDateTime.now(),
            )
        whenever(repository.save(any())).thenReturn(savedUser)

        service.authenticate("fake-id-token")

        val captor = argumentCaptor<AppUser>()
        verify(repository, times(1)).save(captor.capture())
        assertEquals(UserRole.PENDENTE, captor.firstValue.role)
        assertEquals(UserStatus.PENDENTE_APROVACAO, captor.firstValue.status)
    }

    @Test
    fun `should authenticate active user with correct role and status`() {
        val activeClaims =
            GoogleIdentityClaims(
                sub = "google-sub-ativo",
                email = "ativo@gmail.com",
                emailVerified = true,
                name = "Usuario Ativo",
            )
        val activeUser =
            AppUser(
                id = UUID.randomUUID(),
                email = "ativo@gmail.com",
                displayName = "Usuario Ativo",
                role = UserRole.TECNICO,
                status = UserStatus.ATIVO,
                firstLoginAt = OffsetDateTime.now().minusDays(5),
            )

        whenever(verifier.verify(any())).thenReturn(activeClaims)
        whenever(repository.findByEmailIgnoreCase("ativo@gmail.com")).thenReturn(Optional.of(activeUser))

        val result = service.authenticate("fake-id-token")

        assertFalse(result.isPending)
        assertEquals("ATIVO", result.status)

        val claims = jwtService.parsePrincipal(result.token)
        assertEquals("TECNICO", claims.role)
        assertEquals("ATIVO", claims.status)
    }
}
