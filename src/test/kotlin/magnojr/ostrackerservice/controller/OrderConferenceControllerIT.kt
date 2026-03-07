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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderConferenceControllerIT : BaseControllerIT() {
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

    private fun saveOrder(
        status: OrderStatus = OrderStatus.AGUARDANDO_CONFERENCIA,
        technicalSummary: String? = "Resumo tecnico",
        finalValue: BigDecimal? = BigDecimal("120.00"),
        clientName: String? = "Cliente IT",
        clientPhone: String? = "5511999990000",
        hashAccess: String? = null,
    ) = orderRepository.save(
        Order(
            status = status,
            technicalSummary = technicalSummary,
            finalValue = finalValue,
            finishedAt = OffsetDateTime.now(),
            hashAccess = hashAccess,
            clientName = clientName,
            clientPhone = clientPhone,
        ),
    )

    private fun secretariaToken() =
        jwtService.generateUserToken(
            userId = UUID.randomUUID().toString(),
            email = "secretaria@ostracker.local",
            role = "SECRETARIA",
        )

    // --- listPendingConference ---

    @Test
    fun `GET deve retornar fila de conferencia paginada`() {
        repeat(3) { saveOrder() }
        saveOrder(status = OrderStatus.FINALIZADA, hashAccess = "hash-finalizada")

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/conference")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(3, content.size)
    }

    @Test
    fun `GET deve suportar paginacao corretamente`() {
        repeat(5) { saveOrder() }

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/conference?page=0&size=2")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        val content = body["content"] as List<*>
        assertEquals(2, content.size)
        assertEquals(5, (body["totalElements"] as Number).toInt())
    }

    // --- updateConference ---

    @Test
    fun `PUT deve atualizar dados da OS pendente`() {
        val order = saveOrder(technicalSummary = "Antigo resumo", finalValue = BigDecimal("50.00"))

        val response =
            authenticatedClient(secretariaToken())
                .put()
                .uri("/admin/orders/conference/${order.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("technicalSummary" to "Novo resumo", "finalValue" to 200.00))
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Novo resumo", response.body!!["technicalSummary"])
    }

    @Test
    fun `PUT deve retornar 409 ao editar OS que nao esta em AGUARDANDO_CONFERENCIA`() {
        val order = saveOrder(status = OrderStatus.FINALIZADA, hashAccess = "hash-fin")

        val response =
            authenticatedClient(secretariaToken())
                .put()
                .uri("/admin/orders/conference/${order.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("technicalSummary" to "Novo resumo"))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    // --- confirmConference ---

    @Test
    fun `POST confirm deve transicionar para FINALIZADA e gerar hashAccess`() {
        val order = saveOrder()

        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/conference/${order.id}/confirm")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("FINALIZADA", response.body!!["status"])
        assertNotNull(response.body!!["hashAccess"])
    }

    @Test
    fun `POST confirm deve retornar 409 ao confirmar OS fora de AGUARDANDO_CONFERENCIA`() {
        val order = saveOrder(status = OrderStatus.FINALIZADA, hashAccess = "hash-fin")

        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/conference/${order.id}/confirm")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `POST confirm deve retornar 404 quando OS nao existe`() {
        val response =
            authenticatedClient(secretariaToken())
                .post()
                .uri("/admin/orders/conference/${UUID.randomUUID()}/confirm")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // --- segurança ---

    @Test
    fun `GET deve retornar 401 sem token`() {
        val response =
            anonymousClient
                .get()
                .uri("/admin/orders/conference")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `GET deve retornar 403 para token com perfil TECNICO`() {
        val tecnicoToken =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "tecnico@ostracker.local",
                role = "TECNICO",
            )

        val response =
            authenticatedClient(tecnicoToken)
                .get()
                .uri("/admin/orders/conference")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `GET deve retornar 200 para token SUPERUSUARIO`() {
        val response =
            restClient
                .get()
                .uri("/admin/orders/conference")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
