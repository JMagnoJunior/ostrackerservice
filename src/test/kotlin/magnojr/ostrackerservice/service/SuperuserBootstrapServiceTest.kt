package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.config.SecurityProperties
import magnojr.ostrackerservice.model.AppUser
import magnojr.ostrackerservice.model.UserRole
import magnojr.ostrackerservice.model.UserStatus
import magnojr.ostrackerservice.repository.AppUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class SuperuserBootstrapServiceTest {
    private val appUserRepository: AppUserRepository = mock()
    private val securityProperties =
        SecurityProperties().apply {
            superuser.bootstrapEnabled = true
            superuser.email = "superuser@ostracker.local"
            superuser.displayName = "Superuser"
        }
    private val service = SuperuserBootstrapService(appUserRepository, securityProperties)

    @Test
    fun `should create superuser when none exists`() {
        whenever(appUserRepository.countPrimaryActiveSuperusers()).thenReturn(0L)
        whenever(appUserRepository.findByEmailIgnoreCase("superuser@ostracker.local")).thenReturn(Optional.empty())
        whenever(appUserRepository.save(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as AppUser
            AppUser(
                id = UUID.randomUUID(),
                email = saved.email,
                displayName = saved.displayName,
                role = saved.role,
                status = saved.status,
                isPrimarySuperuser = saved.isPrimarySuperuser,
                createdAt = saved.createdAt,
                updatedAt = saved.updatedAt,
            )
        }

        val created = service.ensurePrimarySuperuser()

        assertEquals("superuser@ostracker.local", created.email)
        assertEquals(UserRole.SUPERUSUARIO, created.role)
        assertEquals(UserStatus.ATIVO, created.status)
    }

    @Test
    fun `should be idempotent when primary superuser already exists`() {
        val existing =
            AppUser(
                id = UUID.randomUUID(),
                email = "existing@ostracker.local",
                displayName = "Existing",
                role = UserRole.SUPERUSUARIO,
                status = UserStatus.ATIVO,
                isPrimarySuperuser = true,
            )
        whenever(appUserRepository.countPrimaryActiveSuperusers()).thenReturn(1L)
        whenever(appUserRepository.findPrimaryActiveSuperuser()).thenReturn(Optional.of(existing))

        val returned = service.ensurePrimarySuperuser()

        assertEquals(existing.id, returned.id)
        verify(appUserRepository, never()).save(any())
    }

    @Test
    fun `should fail when more than one primary superuser exists`() {
        whenever(appUserRepository.countPrimaryActiveSuperusers()).thenReturn(2L)

        assertThrows(IllegalStateException::class.java) {
            service.ensurePrimarySuperuser()
        }
    }
}
