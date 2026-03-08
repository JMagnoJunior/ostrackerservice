package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.model.ScheduledShift
import magnojr.ostrackerservice.repository.NotificationLogRepository
import magnojr.ostrackerservice.repository.OrderRepository
import magnojr.ostrackerservice.service.JwtService
import magnojr.ostrackerservice.service.TwilioClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderScheduleControllerIT : BaseControllerIT() {
    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var notificationLogRepository: NotificationLogRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var twilioClient: TwilioClient

    @AfterEach
    fun cleanup() {
        notificationLogRepository.deleteAll()
        orderRepository.deleteAll()
    }

    private fun saveOrder(status: OrderStatus): Order =
        orderRepository.save(
            Order(
                status = status,
                finishedAt = OffsetDateTime.now(),
                finalValue = BigDecimal("200.00"),
                clientName = "Cliente IT",
                clientPhone = "5511999990000",
                hashAccess = "hash-${UUID.randomUUID()}",
            ),
        )

    private val validBody =
        mapOf(
            "scheduledDate" to LocalDate.now().plusDays(1).toString(),
            "scheduledShift" to ScheduledShift.MANHA.name,
        )

    private fun secretariaToken() =
        jwtService.generateUserToken(
            userId = UUID.randomUUID().toString(),
            email = "secretaria@ostracker.local",
            role = "SECRETARIA",
            status = "ATIVO",
        )

    private fun superusuarioToken() =
        jwtService.generateUserToken(
            userId = UUID.randomUUID().toString(),
            email = "super@ostracker.local",
            role = "SUPERUSUARIO",
            status = "ATIVO",
        )

    private fun tecnicoToken() =
        jwtService.generateUserToken(
            userId = UUID.randomUUID().toString(),
            email = "tecnico@ostracker.local",
            role = "TECNICO",
            status = "ATIVO",
        )

    @Test
    fun `PATCH com OS em AGENDADA_PRESENCIAL deve retornar 200 com campos atualizados`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        val response =
            authenticatedClient(secretariaToken())
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("AGENDADA_PRESENCIAL", body["status"])
        assertNotNull(body["scheduledDate"])
        assertEquals("MANHA", body["scheduledShift"])
    }

    @Test
    fun `PATCH com OS em FINALIZADA deve retornar 200 e status AGENDADA_PRESENCIAL`() {
        val order = saveOrder(OrderStatus.FINALIZADA)

        val response =
            authenticatedClient(secretariaToken())
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("AGENDADA_PRESENCIAL", response.body!!["status"])
    }

    @Test
    fun `PATCH com OS em AGUARDANDO_AGENDAMENTO deve retornar 200 e status AGENDADA_PRESENCIAL`() {
        val order = saveOrder(OrderStatus.AGUARDANDO_AGENDAMENTO)

        val response =
            authenticatedClient(secretariaToken())
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("AGENDADA_PRESENCIAL", response.body!!["status"])
    }

    @Test
    fun `PATCH com OS em ENTREGUE deve retornar 409`() {
        val order = saveOrder(OrderStatus.ENTREGUE)

        val response =
            authenticatedClient(secretariaToken())
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `PATCH com OS em AGUARDANDO_CONFERENCIA deve retornar 409`() {
        val order = saveOrder(OrderStatus.AGUARDANDO_CONFERENCIA)

        val response =
            authenticatedClient(secretariaToken())
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `PATCH com OS inexistente deve retornar 404`() {
        val response =
            authenticatedClient(secretariaToken())
                .patch()
                .uri("/admin/orders/${UUID.randomUUID()}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `PATCH com scheduledDate no passado deve retornar 400`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)
        val pastBody =
            mapOf(
                "scheduledDate" to LocalDate.now().minusDays(1).toString(),
                "scheduledShift" to ScheduledShift.TARDE.name,
            )

        val response =
            authenticatedClient(secretariaToken())
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(pastBody)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `PATCH sem autenticacao deve retornar 401`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        val response =
            anonymousClient
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `PATCH com role TECNICO deve retornar 403`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        val response =
            authenticatedClient(tecnicoToken())
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `PATCH com role SECRETARIA deve retornar 200`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        val response =
            authenticatedClient(secretariaToken())
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `PATCH com role SUPERUSUARIO deve retornar 200`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        val response =
            authenticatedClient(superusuarioToken())
                .patch()
                .uri("/admin/orders/${order.id}/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validBody)
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
