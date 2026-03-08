package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.exception.OrderNotFoundException
import magnojr.ostrackerservice.model.AppUser
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderContactLog
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.model.UserRole
import magnojr.ostrackerservice.model.UserStatus
import magnojr.ostrackerservice.repository.AppUserRepository
import magnojr.ostrackerservice.repository.OrderContactLogRepository
import magnojr.ostrackerservice.repository.OrderRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderContactLogServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val appUserRepository: AppUserRepository = mock()
    private val orderContactLogRepository: OrderContactLogRepository = mock()
    private val service = OrderContactLogService(orderRepository, appUserRepository, orderContactLogRepository)

    private fun order(id: UUID = UUID.randomUUID()) =
        Order(
            id = id,
            status = OrderStatus.FINALIZADA,
            finalValue = BigDecimal("200.00"),
            finishedAt = OffsetDateTime.now().minusHours(2),
            hashAccess = "hash-$id",
            clientName = "Cliente Teste",
            clientPhone = "5511999999999",
        )

    private fun appUser(
        id: UUID = UUID.randomUUID(),
        name: String = "Secretaria User",
    ) = AppUser(
        id = id,
        email = "user-$id@ostracker.local",
        displayName = name,
        role = UserRole.SECRETARIA,
        status = UserStatus.ATIVO,
    )

    private fun contactLog(
        order: Order,
        author: AppUser,
        note: String = "Contato realizado com sucesso",
        createdAt: OffsetDateTime = OffsetDateTime.now(),
    ) = OrderContactLog(
        id = UUID.randomUUID(),
        order = order,
        author = author,
        note = note,
        createdAt = createdAt,
    )

    @Test
    fun `createLog com OS existente e note valida deve retornar ContactLogResponse com dados corretos`() {
        val orderId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val order = order(orderId)
        val author = appUser(authorId, "Maria Secretaria")
        val note = "Cliente confirmou presença para retirada amanhã."
        val log = contactLog(order, author, note)

        whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
        whenever(appUserRepository.findById(authorId)).thenReturn(Optional.of(author))
        whenever(orderContactLogRepository.save(any())).thenReturn(log)

        val response = service.createLog(orderId, authorId, note)

        assertEquals(orderId, response.orderId)
        assertEquals(authorId, response.authorId)
        assertEquals("Maria Secretaria", response.authorName)
        assertEquals(note, response.note)
    }

    @Test
    fun `createLog com OS inexistente deve lancar OrderNotFoundException`() {
        val orderId = UUID.randomUUID()
        val authorId = UUID.randomUUID()

        whenever(orderRepository.findById(orderId)).thenReturn(Optional.empty())

        assertThrows<OrderNotFoundException> {
            service.createLog(orderId, authorId, "alguma nota")
        }
    }

    @Test
    fun `createLog com autor inexistente deve lancar ResponseStatusException com status 404`() {
        val orderId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val order = order(orderId)

        whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
        whenever(appUserRepository.findById(authorId)).thenReturn(Optional.empty())

        val ex =
            assertThrows<ResponseStatusException> {
                service.createLog(orderId, authorId, "alguma nota")
            }

        assertEquals(404, ex.statusCode.value())
    }

    @Test
    fun `listLogs com OS com entradas deve retornar lista em ordem decrescente de createdAt`() {
        val orderId = UUID.randomUUID()
        val order = order(orderId)
        val author = appUser()
        val now = OffsetDateTime.now()
        val logs =
            listOf(
                contactLog(order, author, "Nota mais recente", now),
                contactLog(order, author, "Nota intermediaria", now.minusHours(1)),
                contactLog(order, author, "Nota mais antiga", now.minusHours(2)),
            )

        whenever(orderRepository.existsById(orderId)).thenReturn(true)
        whenever(orderContactLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(logs)

        val result = service.listLogs(orderId)

        assertEquals(3, result.size)
        assertEquals("Nota mais recente", result[0].note)
        assertEquals("Nota intermediaria", result[1].note)
        assertEquals("Nota mais antiga", result[2].note)
    }

    @Test
    fun `listLogs com OS sem entradas deve retornar lista vazia`() {
        val orderId = UUID.randomUUID()

        whenever(orderRepository.existsById(orderId)).thenReturn(true)
        whenever(orderContactLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(emptyList())

        val result = service.listLogs(orderId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listLogs com OS inexistente deve lancar OrderNotFoundException`() {
        val orderId = UUID.randomUUID()

        whenever(orderRepository.existsById(orderId)).thenReturn(false)

        assertThrows<OrderNotFoundException> {
            service.listLogs(orderId)
        }
    }
}
