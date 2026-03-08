package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.controller.ContactLogResponse
import magnojr.ostrackerservice.exception.OrderNotFoundException
import magnojr.ostrackerservice.model.OrderContactLog
import magnojr.ostrackerservice.repository.AppUserRepository
import magnojr.ostrackerservice.repository.OrderContactLogRepository
import magnojr.ostrackerservice.repository.OrderRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class OrderContactLogService(
    private val orderRepository: OrderRepository,
    private val appUserRepository: AppUserRepository,
    private val orderContactLogRepository: OrderContactLogRepository,
) {
    @Transactional
    fun createLog(
        orderId: UUID,
        authorId: UUID,
        note: String,
    ): ContactLogResponse {
        val order = orderRepository.findById(orderId).orElseThrow { OrderNotFoundException(orderId) }
        val author =
            appUserRepository.findById(authorId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User with id $authorId not found")
            }
        val log = orderContactLogRepository.save(OrderContactLog(order = order, author = author, note = note))
        return toResponse(log)
    }

    @Transactional(readOnly = true)
    fun listLogs(orderId: UUID): List<ContactLogResponse> {
        if (!orderRepository.existsById(orderId)) throw OrderNotFoundException(orderId)
        return orderContactLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId).map { toResponse(it) }
    }

    private fun toResponse(log: OrderContactLog): ContactLogResponse =
        ContactLogResponse(
            id = log.id!!,
            orderId = log.order.id!!,
            authorId = log.author.id!!,
            authorName = log.author.displayName,
            note = log.note,
            createdAt = log.createdAt,
        )
}
