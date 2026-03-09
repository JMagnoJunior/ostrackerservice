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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate
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
    fun `summary deve incluir contadores dos 3 novos filtros com valores corretos`() {
        val referenceAt = OffsetDateTime.parse("2026-03-08T15:00:00Z")
        // AGUARDANDO_CONFERENCIA
        saveOrder(OrderStatus.AGUARDANDO_CONFERENCIA, referenceAt.minusHours(2), "hash-conf-1")
        saveOrder(OrderStatus.AGUARDANDO_CONFERENCIA, referenceAt.minusHours(3), "hash-conf-2")
        // AGENDADAS
        saveOrder(OrderStatus.AGENDADA_PRESENCIAL, referenceAt.minusDays(2), "hash-ag-1")
        saveOrder(OrderStatus.AGENDADA_DELIVERY, referenceAt.minusDays(3), "hash-ag-2")
        // NO_SHOW: turno TARDE expirou ontem
        saveOrderWithSchedule(
            status = OrderStatus.AGENDADA_PRESENCIAL,
            finishedAt = referenceAt.minusDays(5),
            hashAccess = "hash-ns-1",
            scheduledDate = referenceAt.toLocalDate().minusDays(1),
            scheduledShift = ScheduledShift.TARDE,
        )

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring/summary?referenceAt=$referenceAt")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        @Suppress("UNCHECKED_CAST")
        val counters = response.body!!["counters"] as Map<String, Any?>
        assertEquals(2, (counters["aguardandoConferencia"] as Number).toInt())
        assertEquals(3, (counters["agendadas"] as Number).toInt())
        assertEquals(1, (counters["noShow"] as Number).toInt())
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
    fun `listagem com filter=AGUARDANDO_CONFERENCIA retorna apenas OS no status correto`() {
        val referenceAt = OffsetDateTime.parse("2026-03-08T12:00:00Z")
        saveOrder(OrderStatus.AGUARDANDO_CONFERENCIA, referenceAt.minusHours(1), "hash-ac-1")
        saveOrder(OrderStatus.AGUARDANDO_CONFERENCIA, referenceAt.minusHours(2), "hash-ac-2")
        saveOrder(OrderStatus.FINALIZADA, referenceAt.minusHours(5), "hash-ac-3")

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring?filter=AGUARDANDO_CONFERENCIA&referenceAt=$referenceAt")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(2, content.size)
        @Suppress("UNCHECKED_CAST")
        content.forEach { item ->
            assertEquals("AGUARDANDO_CONFERENCIA", (item as Map<String, Any?>)["status"])
        }
    }

    @Test
    fun `listagem com filter=AGENDADAS retorna OS AGENDADA_PRESENCIAL e AGENDADA_DELIVERY`() {
        val referenceAt = OffsetDateTime.parse("2026-03-08T12:00:00Z")
        saveOrder(OrderStatus.AGENDADA_PRESENCIAL, referenceAt.minusDays(1), "hash-ag-p")
        saveOrder(OrderStatus.AGENDADA_DELIVERY, referenceAt.minusDays(2), "hash-ag-d")
        saveOrder(OrderStatus.FINALIZADA, referenceAt.minusDays(1), "hash-ag-f")

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring?filter=AGENDADAS&referenceAt=$referenceAt")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(2, content.size)
        @Suppress("UNCHECKED_CAST")
        val statuses = content.map { (it as Map<String, Any?>)["status"] as String }.toSet()
        assertTrue(statuses.contains("AGENDADA_PRESENCIAL"))
        assertTrue(statuses.contains("AGENDADA_DELIVERY"))
    }

    @Test
    fun `listagem com filter=NO_SHOW retorna apenas OS com turno expirado`() {
        val referenceAt = OffsetDateTime.parse("2026-03-09T20:00:00-03:00")
        val yesterday = referenceAt.toLocalDate().minusDays(1)
        val today = referenceAt.toLocalDate()

        // Turno TARDE ontem — expirado (18:00 local)
        saveOrderWithSchedule(
            status = OrderStatus.AGENDADA_PRESENCIAL,
            finishedAt = referenceAt.minusDays(5),
            hashAccess = "hash-ns-expired",
            scheduledDate = yesterday,
            scheduledShift = ScheduledShift.TARDE,
        )
        // Turno NOITE hoje — ainda não expirou às 20:00 (expira às 23:59)
        saveOrderWithSchedule(
            status = OrderStatus.AGENDADA_DELIVERY,
            finishedAt = referenceAt.minusDays(3),
            hashAccess = "hash-ns-noite-today",
            scheduledDate = today,
            scheduledShift = ScheduledShift.NOITE,
        )
        // Sem scheduledDate — não qualifica
        saveOrder(OrderStatus.AGENDADA_PRESENCIAL, referenceAt.minusDays(2), "hash-ns-no-date")

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring?filter=NO_SHOW&referenceAt=$referenceAt")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(1, content.size)
        @Suppress("UNCHECKED_CAST")
        assertEquals("AGENDADA_PRESENCIAL", (content.first() as Map<String, Any?>)["status"])
    }

    @Test
    fun `listagem com filter=NO_SHOW nao retorna OS com scheduledDate futuro`() {
        val referenceAt = OffsetDateTime.parse("2026-03-09T10:00:00-03:00")
        val tomorrow = referenceAt.toLocalDate().plusDays(1)

        saveOrderWithSchedule(
            status = OrderStatus.AGENDADA_PRESENCIAL,
            finishedAt = referenceAt.minusDays(2),
            hashAccess = "hash-ns-future",
            scheduledDate = tomorrow,
            scheduledShift = ScheduledShift.MANHA,
        )

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring?filter=NO_SHOW&referenceAt=$referenceAt")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(0, content.size)
    }

    @Test
    fun `listagem com filter=NO_SHOW nao retorna OS sem scheduledDate`() {
        val referenceAt = OffsetDateTime.parse("2026-03-09T10:00:00-03:00")

        saveOrder(OrderStatus.AGENDADA_PRESENCIAL, referenceAt.minusDays(2), "hash-ns-nodate")

        val response =
            authenticatedClient(secretariaToken())
                .get()
                .uri("/admin/orders/monitoring?filter=NO_SHOW&referenceAt=$referenceAt")
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val content = response.body!!["content"] as List<*>
        assertEquals(0, content.size)
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

    private fun saveOrderWithSchedule(
        status: OrderStatus,
        finishedAt: OffsetDateTime?,
        hashAccess: String,
        scheduledDate: LocalDate,
        scheduledShift: ScheduledShift,
    ) = orderRepository.save(
        Order(
            status = status,
            technicalSummary = "Resumo tecnico",
            finalValue = BigDecimal("100.00"),
            finishedAt = finishedAt,
            hashAccess = hashAccess,
            clientName = "Cliente IT",
            clientPhone = "5511999990000",
            scheduledDate = scheduledDate,
            scheduledShift = scheduledShift,
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
