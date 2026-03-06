package magnojr.ostrackerservice.repository

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.model.AppUser
import magnojr.ostrackerservice.model.UserRole
import magnojr.ostrackerservice.model.UserStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
class AppUserPersistenceIT {
    @Autowired
    private lateinit var appUserRepository: AppUserRepository

    @AfterEach
    fun cleanup() {
        appUserRepository.deleteAll()
    }

    @Test
    fun `should prevent duplicated email`() {
        appUserRepository.saveAndFlush(
            AppUser(
                email = "duplicated@ostracker.local",
                displayName = "User One",
                role = UserRole.TECNICO,
                status = UserStatus.ATIVO,
            ),
        )

        assertThrows(DataIntegrityViolationException::class.java) {
            appUserRepository.saveAndFlush(
                AppUser(
                    email = "duplicated@ostracker.local",
                    displayName = "User Two",
                    role = UserRole.SECRETARIA,
                    status = UserStatus.ATIVO,
                ),
            )
        }
    }

    @Test
    fun `should prevent multiple primary active superusers`() {
        appUserRepository.saveAndFlush(
            AppUser(
                email = "super1@ostracker.local",
                displayName = "Super One",
                role = UserRole.SUPERUSUARIO,
                status = UserStatus.ATIVO,
                isPrimarySuperuser = true,
            ),
        )

        assertThrows(DataIntegrityViolationException::class.java) {
            appUserRepository.saveAndFlush(
                AppUser(
                    email = "super2@ostracker.local",
                    displayName = "Super Two",
                    role = UserRole.SUPERUSUARIO,
                    status = UserStatus.ATIVO,
                    isPrimarySuperuser = true,
                ),
            )
        }
    }
}
