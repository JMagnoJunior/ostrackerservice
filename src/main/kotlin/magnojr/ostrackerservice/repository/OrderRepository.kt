package magnojr.ostrackerservice.repository

import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByHashAccess(hashAccess: String): Optional<Order>

    fun findByStatusOrderByFinishedAtAsc(
        status: OrderStatus,
        pageable: Pageable,
    ): Page<Order>

    fun findByIdAndStatus(
        id: UUID,
        status: OrderStatus,
    ): Optional<Order>

    @Query(
        """
        select o from Order o
        where o.status in :statuses
          and o.finishedAt < :cutoff
        order by o.finishedAt asc
        """,
    )
    fun findCallQueue(
        statuses: Collection<OrderStatus>,
        cutoff: OffsetDateTime,
        pageable: Pageable,
    ): Page<Order>

    @Query(
        """
        select o
        from Order o
        where o.status in :statuses
          and o.clientPhone is not null
          and o.hashAccess is not null
          and (o.lastNotificationAt is null or o.lastNotificationAt <= :cutoff)
        order by o.finishedAt asc
        """,
    )
    fun findEligibleForReminder(
        statuses: Collection<OrderStatus>,
        cutoff: OffsetDateTime,
        pageable: Pageable,
    ): List<Order>
}
