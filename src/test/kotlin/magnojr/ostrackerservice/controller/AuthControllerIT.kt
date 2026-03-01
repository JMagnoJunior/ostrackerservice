package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.config.SecurityProperties
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

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerIT : BaseControllerIT() {
    @Autowired
    private lateinit var securityProperties: SecurityProperties

    @Test
    fun `should issue token for valid client secret`() {
        val response =
            anonymousClient
                .post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("clientSecret" to securityProperties.clientSecret))
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val token = response.body!!["token"] as String?
        assertNotNull(token)
        assertTrue(token!!.isNotBlank())
    }

    @Test
    fun `should reject invalid client secret`() {
        val response =
            anonymousClient
                .post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("clientSecret" to "invalid-secret"))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `should reject blank client secret`() {
        val response =
            anonymousClient
                .post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("clientSecret" to ""))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
