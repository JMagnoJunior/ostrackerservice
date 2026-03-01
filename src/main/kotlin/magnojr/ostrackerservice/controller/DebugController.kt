package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.model.NotificationLog
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.repository.NotificationLogRepository
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/debug")
@ConditionalOnProperty(prefix = "app.debug", name = ["endpoints-enabled"], havingValue = "true", matchIfMissing = true)
class DebugController(
    private val orderRepository: OrderRepository,
    private val notificationLogRepository: NotificationLogRepository,
) {
    @GetMapping("/orders")
    fun listOrders(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<Order> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "finishedAt"))
        return orderRepository.findAll(pageable)
    }

    @GetMapping("/notification-logs")
    fun listNotificationLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<NotificationLog> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "sentAt"))
        return notificationLogRepository.findAll(pageable)
    }
}
