package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.NotificationLogRepository
import magnojr.ostrackerservice.repository.OrderRepository
import magnojr.ostrackerservice.service.TwilioClient
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Duration

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderControllerIT {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var notificationLogRepository: NotificationLogRepository

    @MockitoBean
    private lateinit var twilioClient: TwilioClient

    private val restClient by lazy { RestClient.create("http://localhost:$port") }

    @AfterEach
    fun cleanup() {
        notificationLogRepository.deleteAll()
        orderRepository.deleteAll()
    }

    @Test
    fun `should create finalized order successfully and trigger notification`() {
        // Given
        `when`(twilioClient.sendMessage(anyString(), anyString())).thenReturn("mock-sid")

        val dto =
            OrderFinalizationDTO(
                technicalSummary = "Reparo realizado com sucesso",
                finalValue = BigDecimal("250.00"),
                clientName = "Joao Silva",
                clientPhone = "5511999999999",
            )

        // When
        val response =
            restClient
                .post()
                .uri("/api/orders/finalizations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto)
                .retrieve()
                .toEntity(Order::class.java)

        // Then
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val createdOrder = response.body
        assertNotNull(createdOrder)
        assertEquals(OrderStatus.FINALIZADA, createdOrder!!.status)
        assertNotNull(createdOrder.finishedAt)
        assertNotNull(createdOrder.hashAccess)
        assertEquals("Joao Silva", createdOrder.clientName)
        assertEquals("5511999999999", createdOrder.clientPhone)

        // US-02: Verify retrieval by hash access
        val orderRefreshedByHash = orderRepository.findByHashAccess(createdOrder.hashAccess!!).get()
        assertEquals(createdOrder.id, orderRefreshedByHash.id)

        // US-03_001: Verify notification log was created (async via listener)
        await().atMost(Duration.ofSeconds(10)).until {
            notificationLogRepository.findAll().any { it.orderId == createdOrder.id }
        }

        val log = notificationLogRepository.findAll().first { it.orderId == createdOrder.id }
        assertEquals("TWILIO", log.provider)
        assertEquals("SENT", log.status)
        assertEquals("mock-sid", log.providerSid)
    }

    @Test
    fun `should return 400 when finalValue is missing`() {
        // Given
        val dto =
            mapOf(
                "technicalSummary" to "Resumo",
                "clientName" to "Joao Silva",
                "clientPhone" to "5511999999999",
            )

        // When
        val response =
            restClient
                .post()
                .uri("/api/orders/finalizations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(Map::class.java)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `should return 400 when clientName is missing`() {
        // Given
        val dto =
            mapOf(
                "technicalSummary" to "Resumo",
                "finalValue" to 100.00,
                "clientPhone" to "5511999999999",
            )

        // When
        val response =
            restClient
                .post()
                .uri("/api/orders/finalizations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(Map::class.java)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `should return 400 when clientPhone is invalid`() {
        // Given
        val dto =
            mapOf(
                "technicalSummary" to "Resumo",
                "finalValue" to 100.00,
                "clientName" to "Joao Silva",
                "clientPhone" to "11-99999-9999",
            )

        // When
        val response =
            restClient
                .post()
                .uri("/api/orders/finalizations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(Map::class.java)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
