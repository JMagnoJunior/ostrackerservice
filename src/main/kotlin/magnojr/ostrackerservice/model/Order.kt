package magnojr.ostrackerservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.ABERTA,
    @Column(name = "technical_summary")
    var technicalSummary: String? = null,
    @Column(name = "final_value", nullable = true)
    var finalValue: BigDecimal? = null,
    @Column(name = "finished_at", nullable = false)
    var finishedAt: OffsetDateTime? = null,
    @Column(name = "hash_access", nullable = true)
    var hashAccess: String? = null,
    @Column(name = "client_name", nullable = true)
    var clientName: String? = null,
    @Column(name = "client_phone", nullable = true)
    var clientPhone: String? = null,
    @Column(name = "last_notification_at")
    var lastNotificationAt: OffsetDateTime? = null,
    @Column(name = "scheduled_date")
    var scheduledDate: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "scheduled_shift", length = 20)
    var scheduledShift: ScheduledShift? = null,
    @Column(name = "delivered_at")
    var deliveredAt: OffsetDateTime? = null,
)
