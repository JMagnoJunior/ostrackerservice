package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.model.AppUser
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.model.UserRole
import magnojr.ostrackerservice.model.UserStatus
import magnojr.ostrackerservice.repository.AppUserRepository
import magnojr.ostrackerservice.repository.OrderContactLogRepository
import magnojr.ostrackerservice.repository.OrderRepository
import magnojr.ostrackerservice.service.JwtService
import magnojr.ostrackerservice.service.TwilioClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
class OrderContactLogControllerIT : BaseControllerIT() {
    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var appUserRepository: AppUserRepository

    @Autowired
    private lateinit var orderContactLogRepository: OrderContactLogRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var twilioClient: TwilioClient

    @AfterEach
    fun cleanup() {
        orderContactLogRepository.deleteAll()
        orderRepository.deleteAll()
        appUserRepository.deleteAll()
    }

    private fun saveOrder(status: OrderStatus = OrderStatus.FINALIZADA) =
        orderRepository.save(
            Order(
                status = status,
                finalValue = BigDecimal("200.00"),
                finishedAt = OffsetDateTime.now().minusHours(2),
                hashAccess = "hash-${UUID.randomUUID()}",
                clientName = "Cliente IT",
                clientPhone = "5511999990001",
            ),
        )

    private fun saveUser(
        role: UserRole = UserRole.SECRETARIA,
        suffix: String = UUID.randomUUID().toString(),
    ) = appUserRepository.save(
        AppUser(
            email = "user-$suffix@ostracker.local",
            displayName = "User $suffix",
            role = role,
            status = UserStatus.ATIVO,
        ),
    )

    private fun tokenFor(
        user: AppUser,
        role: UserRole = user.role,
    ): String =
        jwtService.generateUserToken(
            userId = user.id!!.toString(),
            email = user.email,
            role = role.name,
            status = UserStatus.ATIVO.name,
        )

    private fun tokenForRole(role: String): String =
        jwtService.generateUserToken(
            userId = UUID.randomUUID().toString(),
            email = "role-test-$role@ostracker.local",
            role = role,
            status = UserStatus.ATIVO.name,
        )

    // ─── POST ────────────────────────────────────────────────────────────────

    @Test
    fun `POST com OS existente e note valida deve retornar 201 com log persistido`() {
        val order = saveOrder()
        val author = saveUser(UserRole.SECRETARIA)
        val client = authenticatedClient(tokenFor(author))

        val response =
            client
                .post()
                .uri("/admin/orders/${order.id}/contact-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("note" to "Cliente confirmado para amanhã."))
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body!!
        assertNotNull(body["id"])
        assertEquals(order.id.toString(), body["orderId"])
        assertEquals(author.id.toString(), body["authorId"])
        assertEquals(author.displayName, body["authorName"])
        assertEquals("Cliente confirmado para amanhã.", body["note"])
        assertNotNull(body["createdAt"])

        val logsInDb = orderContactLogRepository.findByOrderIdOrderByCreatedAtDesc(order.id!!)
        assertEquals(1, logsInDb.size)
    }

    @Test
    fun `POST com OS inexistente deve retornar 404`() {
        val author = saveUser(UserRole.SECRETARIA)
        val client = authenticatedClient(tokenFor(author))
        val fakeOrderId = UUID.randomUUID()

        val response =
            client
                .post()
                .uri("/admin/orders/$fakeOrderId/contact-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("note" to "Nota qualquer"))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `POST com note vazia deve retornar 400`() {
        val order = saveOrder()
        val author = saveUser(UserRole.SECRETARIA)
        val client = authenticatedClient(tokenFor(author))

        val response =
            client
                .post()
                .uri("/admin/orders/${order.id}/contact-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("note" to ""))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `POST com note menor que 3 chars deve retornar 400`() {
        val order = saveOrder()
        val author = saveUser(UserRole.SECRETARIA)
        val client = authenticatedClient(tokenFor(author))

        val response =
            client
                .post()
                .uri("/admin/orders/${order.id}/contact-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("note" to "ab"))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `POST com note maior que 2000 chars deve retornar 400`() {
        val order = saveOrder()
        val author = saveUser(UserRole.SECRETARIA)
        val client = authenticatedClient(tokenFor(author))
        val longNote = "a".repeat(2001)

        val response =
            client
                .post()
                .uri("/admin/orders/${order.id}/contact-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("note" to longNote))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `POST sem autenticacao deve retornar 401`() {
        val order = saveOrder()

        val response =
            anonymousClient
                .post()
                .uri("/admin/orders/${order.id}/contact-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("note" to "Nota válida"))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `POST com role TECNICO deve retornar 403`() {
        val order = saveOrder()
        val client = authenticatedClient(tokenForRole("TECNICO"))

        val response =
            client
                .post()
                .uri("/admin/orders/${order.id}/contact-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("note" to "Nota válida"))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `POST com role SECRETARIA deve retornar 201`() {
        val order = saveOrder()
        val author = saveUser(UserRole.SECRETARIA)
        val client = authenticatedClient(tokenFor(author, UserRole.SECRETARIA))

        val response =
            client
                .post()
                .uri("/admin/orders/${order.id}/contact-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("note" to "Nota da secretaria"))
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `POST com role SUPERUSUARIO deve retornar 201`() {
        val order = saveOrder()
        val author = saveUser(UserRole.SUPERUSUARIO)
        val client = authenticatedClient(tokenFor(author, UserRole.SUPERUSUARIO))

        val response =
            client
                .post()
                .uri("/admin/orders/${order.id}/contact-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("note" to "Nota do superusuario"))
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    // ─── GET ─────────────────────────────────────────────────────────────────

    @Test
    fun `GET com OS com multiplos logs deve retornar 200 com lista em ordem decrescente`() {
        val order = saveOrder()
        val author = saveUser(UserRole.SECRETARIA)
        val postClient = authenticatedClient(tokenFor(author))
        val now = System.currentTimeMillis()

        // Insert 3 logs with slight delays to ensure distinct createdAt
        postClient
            .post()
            .uri("/admin/orders/${order.id}/contact-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("note" to "Nota mais antiga"))
            .retrieve()
            .toEntity(Map::class.java)

        Thread.sleep(50)

        postClient
            .post()
            .uri("/admin/orders/${order.id}/contact-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("note" to "Nota intermediaria"))
            .retrieve()
            .toEntity(Map::class.java)

        Thread.sleep(50)

        postClient
            .post()
            .uri("/admin/orders/${order.id}/contact-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("note" to "Nota mais recente"))
            .retrieve()
            .toEntity(Map::class.java)

        val getResponse =
            postClient
                .get()
                .uri("/admin/orders/${order.id}/contact-logs")
                .retrieve()
                .toEntity(List::class.java)

        assertEquals(HttpStatus.OK, getResponse.statusCode)
        val items = getResponse.body!!
        assertEquals(3, items.size)

        @Suppress("UNCHECKED_CAST")
        val firstNote = (items[0] as Map<String, Any?>)["note"] as String
        assertEquals("Nota mais recente", firstNote)
    }

    @Test
    fun `GET com OS sem logs deve retornar 200 com lista vazia`() {
        val order = saveOrder()
        val author = saveUser(UserRole.SECRETARIA)
        val client = authenticatedClient(tokenFor(author))

        val response =
            client
                .get()
                .uri("/admin/orders/${order.id}/contact-logs")
                .retrieve()
                .toEntity(List::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.isEmpty())
    }

    @Test
    fun `GET com OS inexistente deve retornar 404`() {
        val author = saveUser(UserRole.SECRETARIA)
        val client = authenticatedClient(tokenFor(author))
        val fakeOrderId = UUID.randomUUID()

        val response =
            client
                .get()
                .uri("/admin/orders/$fakeOrderId/contact-logs")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `GET com role TECNICO deve retornar 403`() {
        val order = saveOrder()
        val client = authenticatedClient(tokenForRole("TECNICO"))

        val response =
            client
                .get()
                .uri("/admin/orders/${order.id}/contact-logs")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }
}
