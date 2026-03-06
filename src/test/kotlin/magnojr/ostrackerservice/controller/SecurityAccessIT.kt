package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.service.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
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
}
