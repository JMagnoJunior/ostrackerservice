package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
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
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderCheckinControllerIT : BaseControllerIT() {
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
                finalValue = BigDecimal("150.00"),
                clientName = "Cliente Checkin IT",
                clientPhone = "5511988880000",
                hashAccess = "hash-${UUID.randomUUID()}",
            ),
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
    fun `POST checkin com OS em AGENDADA_PRESENCIAL deve retornar 200 e status ENTREGUE com deliveredAt`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/${order.id}/checkin")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("ENTREGUE", body["status"])
        assertNotNull(body["deliveredAt"])
    }

    @Test
    fun `POST checkin com OS em AGENDADA_DELIVERY deve retornar 200 e status ENTREGUE`() {
        val order = saveOrder(OrderStatus.AGENDADA_DELIVERY)

        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/${order.id}/checkin")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ENTREGUE", response.body!!["status"])
    }

    @Test
    fun `POST checkin com OS em FINALIZADA deve retornar 409`() {
        val order = saveOrder(OrderStatus.FINALIZADA)

        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/${order.id}/checkin")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `POST checkin com OS em ENTREGUE deve retornar 409`() {
        val order = saveOrder(OrderStatus.ENTREGUE)

        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/${order.id}/checkin")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `POST checkin com OS em ABERTA deve retornar 409`() {
        val order = saveOrder(OrderStatus.ABERTA)

        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/${order.id}/checkin")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `POST checkin com ID inexistente deve retornar 404`() {
        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/${UUID.randomUUID()}/checkin")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `POST checkin sem autenticacao deve retornar 401`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        val response =
            anonymousClient
                .post()
                .uri("/admin/orders/${order.id}/checkin")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `POST checkin com role TECNICO deve retornar 403`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        val response =
            authenticatedClient(tecnicoToken())
                .post()
                .uri("/admin/orders/${order.id}/checkin")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `POST checkin com role SECRETARIA deve retornar 200`() {
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/${order.id}/checkin")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `POST checkin com role SUPERUSUARIO deve retornar 200`() {
        val order = saveOrder(OrderStatus.AGENDADA_DELIVERY)

        val response =
            authenticatedClient(superusuarioToken())
                .post()
                .uri("/admin/orders/${order.id}/checkin")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `OS com status ENTREGUE nao deve aparecer no ReminderService`() {
        // Regressão US-04: verifica que OS ENTREGUE não é incluída no batch de lembretes.
        // O ReminderService filtra apenas FINALIZADA e AGUARDANDO_AGENDAMENTO,
        // então ENTREGUE nunca entraria — validado aqui via consulta direta no repositório.
        val order = saveOrder(OrderStatus.AGENDADA_PRESENCIAL)

        // Realiza o checkin
        authenticatedClient(secretariaToken())
            .post()
            .uri("/admin/orders/${order.id}/checkin")
            .retrieve()
            .toEntity(Map::class.java)

        // Verifica que a OS agora tem status ENTREGUE no banco
        val updated = orderRepository.findById(order.id!!).orElseThrow()
        assertEquals(OrderStatus.ENTREGUE, updated.status)

        // Verifica que a query de lembrete não retorna OS ENTREGUE
        val cutoff = OffsetDateTime.now().plusHours(1)
        val eligible =
            orderRepository.findEligibleForReminder(
                statuses = setOf(OrderStatus.FINALIZADA, OrderStatus.AGUARDANDO_AGENDAMENTO),
                cutoff = cutoff,
                pageable = PageRequest.of(0, 100),
            )
        assert(eligible.none { it.id == order.id }) {
            "OS ENTREGUE nao deve aparecer no batch de lembretes"
        }
    }
}
