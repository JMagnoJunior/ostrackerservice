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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.OffsetDateTime

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CallQueueControllerIT {
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

    private fun saveOrder(
        status: OrderStatus,
        finishedAt: OffsetDateTime,
        hashAccess: String = "hash-${System.nanoTime()}",
    ) = orderRepository.save(
        Order(
            status = status,
            finalValue = BigDecimal("150.00"),
            finishedAt = finishedAt,
            hashAccess = hashAccess,
            clientName = "Cliente IT",
            clientPhone = "5511999990000",
        ),
    )

    @Test
    fun `fila mista deve retornar apenas OS elegivel`() {
        saveOrder(OrderStatus.FINALIZADA, OffsetDateTime.now().minusHours(48))
        saveOrder(OrderStatus.FINALIZADA, OffsetDateTime.now().minusHours(12))
        saveOrder(OrderStatus.AGENDADA_PRESENCIAL, OffsetDateTime.now().minusHours(30))

        val response =
            restClient
                .get()
                .uri("/admin/orders/call-queue")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(1, content.size)
    }

    @Test
    fun `paginacao deve funcionar corretamente`() {
        repeat(10) { i ->
            saveOrder(
                OrderStatus.FINALIZADA,
                OffsetDateTime.now().minusHours(48 + i.toLong()),
                hashAccess = "hash-pag-$i",
            )
        }

        val response =
            restClient
                .get()
                .uri("/admin/orders/call-queue?page=0&size=3")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        val content = body["content"] as List<*>
        assertEquals(3, content.size)
        assertEquals(10, (body["totalElements"] as Number).toInt())
    }

    @Test
    fun `fila vazia deve retornar 200 com content vazio`() {
        saveOrder(OrderStatus.FINALIZADA, OffsetDateTime.now().minusHours(12))

        val response =
            restClient
                .get()
                .uri("/admin/orders/call-queue")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        val content = body["content"] as List<*>
        assertTrue(content.isEmpty())
        assertEquals(0, (body["totalElements"] as Number).toInt())
    }

    @Test
    fun `campos da resposta devem estar presentes e corretos`() {
        saveOrder(OrderStatus.FINALIZADA, OffsetDateTime.now().minusHours(48), hashAccess = "hash-campos")

        val response =
            restClient
                .get()
                .uri("/admin/orders/call-queue")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(1, content.size)

        @Suppress("UNCHECKED_CAST")
        val item = content.first() as Map<String, Any?>
        assertNotNull(item["id"])
        assertEquals("Cliente IT", item["clientName"])
        assertEquals("5511999990000", item["clientPhone"])
        assertEquals("FINALIZADA", item["status"])
        assertNotNull(item["finishedAt"])
        val inactiveHours = (item["inactiveHours"] as Number).toLong()
        assertTrue(inactiveHours >= 47, "inactiveHours deve ser ao menos 47, foi $inactiveHours")
    }

    @Test
    fun `ordenacao deve ser crescente por finishedAt`() {
        saveOrder(OrderStatus.FINALIZADA, OffsetDateTime.now().minusHours(72), hashAccess = "hash-ord-3")
        saveOrder(OrderStatus.FINALIZADA, OffsetDateTime.now().minusHours(96), hashAccess = "hash-ord-4")
        saveOrder(OrderStatus.FINALIZADA, OffsetDateTime.now().minusHours(48), hashAccess = "hash-ord-2")

        val response =
            restClient
                .get()
                .uri("/admin/orders/call-queue")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(3, content.size)

        @Suppress("UNCHECKED_CAST")
        val hours = content.map { (it as Map<String, Any?>)["inactiveHours"] as Number }.map { it.toLong() }
        assertTrue(hours[0] > hours[1] || hours[0] >= hours[1], "Primeiro item deve ter mais horas inativas (mais antigo)")
        assertTrue(
            hours[0] >= hours[1] && hours[1] >= hours[2],
            "Deve estar em ordem decrescente de inactiveHours (crescente de finishedAt)",
        )
    }
}
