package magnojr.ostrackerservice.repository

import magnojr.ostrackerservice.model.AppUser
import magnojr.ostrackerservice.model.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface AppUserRepository : JpaRepository<AppUser, UUID> {
    fun findByEmailIgnoreCase(email: String): Optional<AppUser>

    @Query(
        value =
            """
            select * from app_users
            where role = 'SUPERUSUARIO'
              and status = 'ATIVO'
              and is_primary_superuser = true
            limit 1
            """,
        nativeQuery = true,
    )
    fun findPrimaryActiveSuperuser(): Optional<AppUser>

    @Query(
        value =
            """
            select count(*) from app_users
            where role = 'SUPERUSUARIO'
              and status = 'ATIVO'
              and is_primary_superuser = true
            """,
        nativeQuery = true,
    )
    fun countPrimaryActiveSuperusers(): Long

    fun findAllByStatusOrderByCreatedAtDesc(status: UserStatus): List<AppUser>

    fun findAllByOrderByCreatedAtDesc(): List<AppUser>
}
