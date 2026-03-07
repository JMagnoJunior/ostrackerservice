package magnojr.ostrackerservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.jsonwebtoken.Jwts
import magnojr.ostrackerservice.config.SecurityProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

class GoogleIdTokenVerifierTest {
    private val wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private val objectMapper = ObjectMapper()
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val kid = "test-key-id"

    private lateinit var properties: SecurityProperties
    private lateinit var verifier: GoogleIdTokenVerifier

    @BeforeEach
    fun setup() {
        wireMock.start()
        wireMock.stubFor(get("/certs").willReturn(aResponse().withBody(buildJwkSetJson())))

        properties =
            SecurityProperties().apply {
                jwtSecret = "dummy"
                clientSecret = "dummy"
                google =
                    SecurityProperties.GoogleProperties().apply {
                        clientId = "test-client-id"
                        jwksUri = "http://localhost:${wireMock.port()}/certs"
                    }
            }
        verifier = GoogleIdTokenVerifier(properties)
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    @Test
    fun `should verify valid Google ID token`() {
        val token = buildToken(email = "user@gmail.com", emailVerified = true)

        val claims = verifier.verify(token)

        assertEquals("google-sub-123", claims.sub)
        assertEquals("user@gmail.com", claims.email)
        assertTrue(claims.emailVerified)
        assertEquals("Test User", claims.name)
    }

    @Test
    fun `should reject token with invalid issuer`() {
        val token =
            Jwts
                .builder()
                .subject("sub")
                .issuer("https://evil.com")
                .audience()
                .add("test-client-id")
                .and()
                .claim("email", "user@gmail.com")
                .claim("email_verified", true)
                .issuedAt(Date())
                .expiration(Date(System.currentTimeMillis() + 3_600_000))
                .header()
                .keyId(kid)
                .and()
                .signWith(keyPair.private)
                .compact()

        assertThrows<GoogleTokenVerificationException> { verifier.verify(token) }
    }

    @Test
    fun `should reject token with wrong audience`() {
        val token =
            Jwts
                .builder()
                .subject("sub")
                .issuer("accounts.google.com")
                .audience()
                .add("wrong-client-id")
                .and()
                .claim("email", "user@gmail.com")
                .claim("email_verified", true)
                .issuedAt(Date())
                .expiration(Date(System.currentTimeMillis() + 3_600_000))
                .header()
                .keyId(kid)
                .and()
                .signWith(keyPair.private)
                .compact()

        assertThrows<GoogleTokenVerificationException> { verifier.verify(token) }
    }

    @Test
    fun `should reject expired token`() {
        val token =
            Jwts
                .builder()
                .subject("sub")
                .issuer("accounts.google.com")
                .audience()
                .add("test-client-id")
                .and()
                .claim("email", "user@gmail.com")
                .claim("email_verified", true)
                .issuedAt(Date(System.currentTimeMillis() - 7_200_000))
                .expiration(Date(System.currentTimeMillis() - 3_600_000))
                .header()
                .keyId(kid)
                .and()
                .signWith(keyPair.private)
                .compact()

        assertThrows<GoogleTokenVerificationException> { verifier.verify(token) }
    }

    @Test
    fun `should reject token with unverified email`() {
        val token = buildToken(email = "user@gmail.com", emailVerified = false)

        assertThrows<GoogleTokenVerificationException> { verifier.verify(token) }
    }

    @Test
    fun `parseKeyMap should extract RSA keys from JWK Set JSON`() {
        val jwksJson = buildJwkSetJson()

        val keyMap = verifier.parseKeyMap(jwksJson)

        assertTrue(keyMap.containsKey(kid))
    }

    private fun buildToken(
        email: String,
        emailVerified: Boolean,
    ): String =
        Jwts
            .builder()
            .subject("google-sub-123")
            .issuer("accounts.google.com")
            .audience()
            .add("test-client-id")
            .and()
            .claim("email", email)
            .claim("email_verified", emailVerified)
            .claim("name", "Test User")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .header()
            .keyId(kid)
            .and()
            .signWith(keyPair.private)
            .compact()

    private fun buildJwkSetJson(): String {
        val pub = keyPair.public as RSAPublicKey
        val encoder = Base64.getUrlEncoder().withoutPadding()

        fun bigIntToBase64(value: java.math.BigInteger): String {
            val bytes = value.toByteArray()
            val stripped = if (bytes[0] == 0.toByte()) bytes.drop(1).toByteArray() else bytes
            return encoder.encodeToString(stripped)
        }

        val jwk =
            mapOf(
                "kty" to "RSA",
                "kid" to kid,
                "use" to "sig",
                "alg" to "RS256",
                "n" to bigIntToBase64(pub.modulus),
                "e" to bigIntToBase64(pub.publicExponent),
            )
        return objectMapper.writeValueAsString(mapOf("keys" to listOf(jwk)))
    }
}
