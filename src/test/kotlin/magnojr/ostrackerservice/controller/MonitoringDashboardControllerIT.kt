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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MonitoringDashboardControllerIT : BaseControllerIT() {
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

    @Test
    fun `summary deve retornar contadores corretos`() {
        val referenceAt = OffsetDateTime.parse("2026-03-08T12:00:00Z")
        saveOrder(OrderStatus.FINALIZADA, referenceAt.minusHours(30), "hash-summary-1")
        saveOrder(OrderStatus.AGUARDANDO_AGENDAMENTO, referenceAt.minusHours(10), "hash-summary-2")
        saveOrder(OrderStatus.AGENDADA_PRESENCIAL, referenceAt.minusDays(100), "hash-summary-3")
        saveOrder(OrderStatus.AGENDADA_DELIVERY, referenceAt.minusDays(120), "hash-summary-4")
        saveOrder(OrderStatus.ENTREGUE, referenceAt.minusDays(100), "hash-summary-5")

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring/summary?referenceAt=$referenceAt")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!

        @Suppress("UNCHECKED_CAST")
        val counters = body["counters"] as Map<String, Any?>
        assertEquals(1, (counters["atrasados"] as Number).toInt())
        assertEquals(2, (counters["semAgendamento"] as Number).toInt())
        assertEquals(1, (counters["proximosDescartes"] as Number).toInt())

        @Suppress("UNCHECKED_CAST")
        val statusVolumes = body["statusVolumes"] as List<Map<String, Any?>>
        val volumesMap = statusVolumes.associate { it["status"] as String to (it["count"] as Number).toInt() }
        assertEquals(1, volumesMap["FINALIZADA"])
        assertEquals(1, volumesMap["AGUARDANDO_AGENDAMENTO"])
        assertEquals(1, volumesMap["AGENDADA_PRESENCIAL"])
        assertEquals(1, volumesMap["AGENDADA_DELIVERY"])
        assertEquals(1, volumesMap["ENTREGUE"])
    }

    @Test
    fun `listagem deve respeitar paginacao e ordenacao`() {
        val referenceAt = OffsetDateTime.parse("2026-03-08T12:00:00Z")
        saveOrder(OrderStatus.FINALIZADA, referenceAt.minusHours(50), "hash-page-1")
        saveOrder(OrderStatus.AGUARDANDO_AGENDAMENTO, referenceAt.minusHours(40), "hash-page-2")
        saveOrder(OrderStatus.FINALIZADA, referenceAt.minusHours(30), "hash-page-3")
        saveOrder(OrderStatus.AGUARDANDO_AGENDAMENTO, referenceAt.minusHours(20), "hash-page-4")
        saveOrder(OrderStatus.FINALIZADA, referenceAt.minusHours(10), "hash-page-5")

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring?filter=SEM_AGENDAMENTO&page=0&size=2&referenceAt=$referenceAt")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        val content = body["content"] as List<*>
        assertEquals(2, content.size)
        assertEquals(5, (body["totalElements"] as Number).toInt())
        assertEquals(3, (body["totalPages"] as Number).toInt())
        assertEquals(true, body["hasNext"])

        @Suppress("UNCHECKED_CAST")
        val first = content[0] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val second = content[1] as Map<String, Any?>
        val firstInactive = (first["inactiveHours"] as Number).toLong()
        val secondInactive = (second["inactiveHours"] as Number).toLong()
        assertTrue(firstInactive >= secondInactive)
        assertEquals("SEM_AGENDAMENTO", first["monitoringFilter"])
    }

    @Test
    fun `listagem deve aplicar intersecao entre filter e status`() {
        val referenceAt = OffsetDateTime.parse("2026-03-08T12:00:00Z")
        saveOrder(OrderStatus.FINALIZADA, referenceAt.minusDays(100), "hash-filter-1")
        saveOrder(OrderStatus.AGENDADA_PRESENCIAL, referenceAt.minusDays(100), "hash-filter-2")

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri(
                    "/admin/orders/monitoring?filter=PROXIMOS_DESCARTES&status=FINALIZADA&referenceAt=$referenceAt",
                ).retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(1, content.size)
        @Suppress("UNCHECKED_CAST")
        val item = content.first() as Map<String, Any?>
        assertEquals("FINALIZADA", item["status"])
        assertEquals("PROXIMOS_DESCARTES", item["monitoringFilter"])
        assertEquals(20L, (item["daysToDiscard"] as Number).toLong())
    }

    @Test
    fun `summary deve retornar 401 sem token`() {
        val response =
            anonymousClient
                .get()
                .uri("/admin/orders/monitoring/summary")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `listagem deve retornar 403 para tecnico`() {
        val response =
            authenticatedClient(tecnicoToken())
                .get()
                .uri("/admin/orders/monitoring?filter=ATRASADOS")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `listagem deve retornar 400 para filter invalido`() {
        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring?filter=INVALIDO")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listagem deve retornar 400 para size fora do limite`() {
        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring?filter=SEM_AGENDAMENTO&size=201")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `listagem deve retornar 400 para referenceAt invalido`() {
        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring?filter=ATRASADOS&referenceAt=data-invalida")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    private fun saveOrder(
        status: OrderStatus,
        finishedAt: OffsetDateTime,
        hashAccess: String,
    ) = orderRepository.save(
        Order(
            status = status,
            technicalSummary = "Resumo tecnico",
            finalValue = BigDecimal("100.00"),
            finishedAt = finishedAt,
            hashAccess = hashAccess,
            clientName = "Cliente IT",
            clientPhone = "5511999990000",
        ),
    )

    private fun secretariaToken() =
        jwtService.generateUserToken(
            userId = UUID.randomUUID().toString(),
            email = "secretaria@ostracker.local",
            role = "SECRETARIA",
            status = "ATIVO",
        )

    private fun tecnicoToken() =
        jwtService.generateUserToken(
            userId = UUID.randomUUID().toString(),
            email = "tecnico@ostracker.local",
            role = "TECNICO",
            status = "ATIVO",
        )
}
