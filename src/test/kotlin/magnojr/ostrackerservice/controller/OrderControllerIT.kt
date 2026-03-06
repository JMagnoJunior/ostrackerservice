package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.NotificationLogRepository
import magnojr.ostrackerservice.repository.OrderRepository
import magnojr.ostrackerservice.service.TwilioClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderControllerIT : BaseControllerIT() {
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
    fun `should create order in awaiting conference status without notification`() {
        // Given
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
        assertEquals(OrderStatus.AGUARDANDO_CONFERENCIA, createdOrder!!.status)
        assertNotNull(createdOrder.finishedAt)
        assertNull(createdOrder.hashAccess)
        assertEquals(BigDecimal("250.00"), createdOrder.finalValue)
        assertEquals("Joao Silva", createdOrder.clientName)
        assertEquals("5511999999999", createdOrder.clientPhone)

        assertEquals(0, notificationLogRepository.count())
        verifyNoInteractions(twilioClient)
    }

    @Test
    fun `should create order when finalValue is missing`() {
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
                .toEntity(Order::class.java)

        // Then
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val createdOrder = response.body
        assertNotNull(createdOrder)
        assertEquals(OrderStatus.AGUARDANDO_CONFERENCIA, createdOrder!!.status)
        assertNull(createdOrder.finalValue)
        assertNull(createdOrder.hashAccess)
        assertEquals(0, notificationLogRepository.count())
        verifyNoInteractions(twilioClient)
    }

    @Test
    fun `should return 400 when finalValue is negative`() {
        // Given
        val dto =
            mapOf(
                "technicalSummary" to "Resumo",
                "finalValue" to -100.00,
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
