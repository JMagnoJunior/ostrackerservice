package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityAccessIT : BaseControllerIT() {
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
}
