package magnojr.ostrackerservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "app_users")
class AppUser(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(nullable = false, unique = true, length = 255)
    var email: String,
    @Column(name = "display_name", nullable = false, length = 255)
    var displayName: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var role: UserRole,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: UserStatus,
    @Column(name = "is_primary_superuser", nullable = false)
    var isPrimarySuperuser: Boolean = false,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    fun isActive(): Boolean = status == UserStatus.ATIVO

    fun isPrimaryActiveSuperuser(): Boolean = role == UserRole.SUPERUSUARIO && isActive() && isPrimarySuperuser

    @PrePersist
    fun prePersist() {
        val now = OffsetDateTime.now()
        email = email.trim().lowercase()
        displayName = displayName.trim()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        email = email.trim().lowercase()
        displayName = displayName.trim()
        updatedAt = OffsetDateTime.now()
    }
}
