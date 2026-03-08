package magnojr.ostrackerservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "order_contact_logs")
class OrderContactLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,
    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    val author: AppUser,
    @Column(nullable = false, columnDefinition = "TEXT")
    val note: String,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @PrePersist
    fun prePersist() {
        createdAt = OffsetDateTime.now()
    }
}
