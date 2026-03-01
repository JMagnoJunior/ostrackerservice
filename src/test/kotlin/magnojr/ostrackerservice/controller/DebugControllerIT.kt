package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.model.NotificationLog
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.NotificationLogRepository
import magnojr.ostrackerservice.repository.OrderRepository
import magnojr.ostrackerservice.service.TwilioClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.OffsetDateTime

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DebugControllerIT : BaseControllerIT() {
    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var notificationLogRepository: NotificationLogRepository

    @MockitoBean
    private lateinit var twilioClient: TwilioClient

    @AfterEach
    fun cleanup() {
        notificationLogRepository.deleteAll()
        orderRepository.deleteAll()
    }

    @Test
    fun `should list orders from debug endpoint`() {
        orderRepository.save(
            Order(
                status = OrderStatus.FINALIZADA,
                technicalSummary = "Ajuste tecnico",
                finalValue = BigDecimal("180.00"),
                finishedAt = OffsetDateTime.now(),
                hashAccess = "hash-debug-001",
                clientName = "Cliente Debug",
                clientPhone = "5511999991111",
            ),
        )

        val response =
            restClient
                .get()
                .uri("/api/debug/orders?size=10")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        val content = body!!["content"] as List<*>
        assertEquals(1, content.size)
    }

    @Test
    fun `should ignore unsupported sort query param in debug orders endpoint`() {
        orderRepository.save(
            Order(
                status = OrderStatus.FINALIZADA,
                technicalSummary = "Ajuste tecnico",
                finalValue = BigDecimal("180.00"),
                finishedAt = OffsetDateTime.now(),
                hashAccess = "hash-debug-003",
                clientName = "Cliente Debug 2",
                clientPhone = "5511999993333",
            ),
        )

        val response =
            restClient
                .get()
                .uri("/api/debug/orders?size=10&sort=%5B%22string%22%5D")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        val content = body!!["content"] as List<*>
        assertEquals(1, content.size)
    }

    @Test
    fun `should list notification logs from debug endpoint`() {
        val order =
            orderRepository.save(
                Order(
                    status = OrderStatus.FINALIZADA,
                    technicalSummary = "Resumo",
                    finalValue = BigDecimal("99.90"),
                    finishedAt = OffsetDateTime.now(),
                    hashAccess = "hash-debug-002",
                    clientName = "Cliente Log",
                    clientPhone = "5511999992222",
                ),
            )

        notificationLogRepository.save(
            NotificationLog(
                orderId = order.id!!,
                provider = "TWILIO",
                type = "INITIAL_ALERT",
                providerSid = "sid-debug",
                status = "SENT",
            ),
        )

        val response =
            restClient
                .get()
                .uri("/api/debug/notification-logs?size=10")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        val content = body!!["content"] as List<*>
        assertEquals(1, content.size)
    }
}
