package magnojr.ostrackerservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "notification_logs")
class NotificationLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(name = "order_id", nullable = false)
    val orderId: UUID,
    @Column(nullable = false)
    val provider: String,
    @Column(nullable = false)
    val type: String,
    @Column(name = "provider_sid")
    var providerSid: String? = null,
    @Column(nullable = false)
    var status: String,
    @Column(name = "sent_at", nullable = false)
    val sentAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "error_log")
    var errorLog: String? = null,
)
