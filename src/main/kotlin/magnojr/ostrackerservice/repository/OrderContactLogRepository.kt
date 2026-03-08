package magnojr.ostrackerservice.repository

import magnojr.ostrackerservice.model.OrderContactLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderContactLogRepository : JpaRepository<OrderContactLog, UUID> {
    fun findByOrderIdOrderByCreatedAtDesc(orderId: UUID): List<OrderContactLog>
}
