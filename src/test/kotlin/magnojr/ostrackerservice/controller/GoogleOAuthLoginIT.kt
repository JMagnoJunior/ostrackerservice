package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.model.UserStatus
import magnojr.ostrackerservice.repository.AppUserRepository
import magnojr.ostrackerservice.service.GoogleIdTokenVerifier
import magnojr.ostrackerservice.service.GoogleIdentityClaims
import magnojr.ostrackerservice.service.GoogleTokenVerificationException
import magnojr.ostrackerservice.service.JwtService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GoogleOAuthLoginIT : BaseControllerIT() {
    @MockitoBean
    private lateinit var googleIdTokenVerifier: GoogleIdTokenVerifier

    @Autowired
    private lateinit var appUserRepository: AppUserRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @AfterEach
    fun cleanup() {
        appUserRepository
            .findAllByOrderByCreatedAtDesc()
            .filter { !it.isPrimaryActiveSuperuser() }
            .forEach { appUserRepository.delete(it) }
    }

    @Test
    fun `POST google with valid token and unknown email creates pending user and returns session`() {
        whenever(googleIdTokenVerifier.verify(any())).thenReturn(
            GoogleIdentityClaims(
                sub = "google-sub-new",
                email = "newuser@gmail.com",
                emailVerified = true,
                name = "New User",
            ),
        )

        val response =
            anonymousClient
                .post()
                .uri("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("idToken" to "fake-google-token"))
                .retrieve()
                .toEntity(Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertNotNull(body["token"])
        assertEquals("PENDENTE_APROVACAO", body["status"])
        assertEquals(true, body["pending"])

        val user = appUserRepository.findByEmailIgnoreCase("newuser@gmail.com").orElseThrow()
        assertEquals(UserStatus.PENDENTE_APROVACAO, user.status)
        assertNotNull(user.firstLoginAt)
    }

    @Test
    fun `POST google with already registered email does not create duplicate`() {
        whenever(googleIdTokenVerifier.verify(any())).thenReturn(
            GoogleIdentityClaims(
                sub = "google-sub-existing",
                email = "newuser@gmail.com",
                emailVerified = true,
                name = "New User",
            ),
        )

        anonymousClient
            .post()
            .uri("/api/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("idToken" to "fake-google-token"))
            .retrieve()
            .toEntity(Map::class.java)

        anonymousClient
            .post()
            .uri("/api/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("idToken" to "fake-google-token"))
            .retrieve()
            .toEntity(Map::class.java)

        val count =
            appUserRepository
                .findAllByOrderByCreatedAtDesc()
                .count { it.email == "newuser@gmail.com" }
        assertEquals(1, count)
    }

    @Test
    fun `POST google with invalid token returns 401`() {
        whenever(googleIdTokenVerifier.verify(any())).thenThrow(
            GoogleTokenVerificationException("Invalid token"),
        )

        val response =
            anonymousClient
                .post()
                .uri("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("idToken" to "bad-token"))
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `pending user receives 403 on protected internal route`() {
        whenever(googleIdTokenVerifier.verify(any())).thenReturn(
            GoogleIdentityClaims(
                sub = "google-sub-pending",
                email = "pending@gmail.com",
                emailVerified = true,
                name = "Pending User",
            ),
        )

        val loginResponse =
            anonymousClient
                .post()
                .uri("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("idToken" to "fake-google-token"))
                .retrieve()
                .toEntity(Map::class.java)

        val token = loginResponse.body!!["token"] as String
        assertTrue(token.isNotBlank())

        val protectedResponse =
            authenticatedClient(token)
                .get()
                .uri("/api/debug/orders?size=1")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .toEntity(String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, protectedResponse.statusCode)
    }

    @Test
    fun `pending user appears in access users with PENDENTE_APROVACAO status`() {
        whenever(googleIdTokenVerifier.verify(any())).thenReturn(
            GoogleIdentityClaims(
                sub = "google-sub-listed",
                email = "listed@gmail.com",
                emailVerified = true,
                name = "Listed User",
            ),
        )

        anonymousClient
            .post()
            .uri("/api/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("idToken" to "fake-google-token"))
            .retrieve()
            .toEntity(Map::class.java)

        val response =
            restClient
                .get()
                .uri("/api/access/users?status=PENDENTE_APROVACAO")
                .retrieve()
                .toEntity(List::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.isNotEmpty())
    }
}
