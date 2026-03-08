package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.service.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityAccessIT : BaseControllerIT() {
    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should return 401 for protected endpoint without token`() {
        val response =
            anonymousClient
                .get()
                .uri("/api/debug/orders?size=1")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `should return 401 for protected endpoint with invalid token`() {
        val response =
            authenticatedClient("invalid-token")
                .get()
                .uri("/api/debug/orders?size=1")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `should allow protected endpoint with valid token`() {
        val response =
            restClient
                .get()
                .uri("/api/debug/orders?size=1")
                .retrieve()
                .toEntity(String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `should allow health endpoint without token`() {
        val response =
            anonymousClient
                .get()
                .uri("/actuator/health")
                .retrieve()
                .toEntity(String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `should allow openapi docs endpoint without token`() {
        val response =
            anonymousClient
                .get()
                .uri("/v3/api-docs")
                .retrieve()
                .toEntity(String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `should return 401 for access endpoint without token`() {
        val response =
            anonymousClient
                .get()
                .uri("/api/access/users")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `should return 403 for access endpoint with non-superuser role`() {
        val techToken =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "tecnico@ostracker.local",
                role = "TECNICO",
                status = "ATIVO",
            )

        val response =
            authenticatedClient(techToken)
                .get()
                .uri("/api/access/users")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `should return 200 for access endpoint with superuser token`() {
        val response =
            restClient
                .get()
                .uri("/api/access/users")
                .retrieve()
                .toEntity(List::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body != null)
    }

    @Test
    fun `SUPERUSUARIO should access modulo tecnico - POST finalizations returns non-403`() {
        val superToken =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "super@ostracker.local",
                role = "SUPERUSUARIO",
                status = "ATIVO",
            )

        val response =
            authenticatedClient(superToken)
                .post()
                .uri("/api/orders/finalizations")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertNotEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNotEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `SUPERUSUARIO should access modulo secretaria - GET conference returns 200`() {
        val superToken =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "super@ostracker.local",
                role = "SUPERUSUARIO",
                status = "ATIVO",
            )

        val response =
            authenticatedClient(superToken)
                .get()
                .uri("/admin/orders/conference")
                .retrieve()
                .toEntity(String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `SUPERUSUARIO should access monitoramento - GET monitoring summary returns 200`() {
        val superToken =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "super@ostracker.local",
                role = "SUPERUSUARIO",
                status = "ATIVO",
            )

        val response =
            authenticatedClient(superToken)
                .get()
                .uri("/admin/orders/monitoring/summary")
                .retrieve()
                .toEntity(String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `SECRETARIA should be blocked from modulo tecnico - POST finalizations returns 403`() {
        val secretariaToken =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "secretaria@ostracker.local",
                role = "SECRETARIA",
                status = "ATIVO",
            )

        val response =
            authenticatedClient(secretariaToken)
                .post()
                .uri("/api/orders/finalizations")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `TECNICO should be blocked from modulo secretaria - GET conference returns 403`() {
        val techToken =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "tecnico@ostracker.local",
                role = "TECNICO",
                status = "ATIVO",
            )

        val response =
            authenticatedClient(techToken)
                .get()
                .uri("/admin/orders/conference")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `TECNICO should be blocked from monitoramento - GET monitoring summary returns 403`() {
        val techToken =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "tecnico@ostracker.local",
                role = "TECNICO",
                status = "ATIVO",
            )

        val response =
            authenticatedClient(techToken)
                .get()
                .uri("/admin/orders/monitoring/summary")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `should return 403 for protected endpoint with PENDENTE role`() {
        val pendingToken =
            jwtService.generateUserToken(
                userId = UUID.randomUUID().toString(),
                email = "pending@ostracker.local",
                role = "PENDENTE",
                status = "PENDENTE_APROVACAO",
            )

        val response =
            authenticatedClient(pendingToken)
                .get()
                .uri("/api/debug/orders?size=1")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }
}
