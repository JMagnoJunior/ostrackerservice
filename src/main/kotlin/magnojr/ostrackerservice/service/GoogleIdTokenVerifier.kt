package magnojr.ostrackerservice.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import magnojr.ostrackerservice.config.SecurityProperties
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

@Service
class GoogleIdTokenVerifier(
    private val properties: SecurityProperties,
) {
    companion object {
        private val ALLOWED_ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")
        private val MAPPER = ObjectMapper()
    }

    private val restClient: RestClient = RestClient.create()

    @Cacheable("google-jwks")
    fun fetchKeyMap(): Map<String, PublicKey> {
        val json =
            restClient
                .get()
                .uri(properties.google.jwksUri)
                .retrieve()
                .body(String::class.java)
                ?: throw GoogleTokenVerificationException("Empty JWKS response from Google")

        return parseKeyMap(json)
    }

    fun verify(idToken: String): GoogleIdentityClaims {
        val keyMap = fetchKeyMap()
        val kid = extractKidFromHeader(idToken)
        val key =
            (if (kid != null) keyMap[kid] else null)
                ?: keyMap.values.firstOrNull()
                ?: throw GoogleTokenVerificationException("No matching key found in JWKS")

        val claims =
            try {
                Jwts
                    .parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(idToken)
                    .payload
            } catch (e: JwtException) {
                throw GoogleTokenVerificationException("Invalid Google ID token: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw GoogleTokenVerificationException("Malformed Google ID token: ${e.message}", e)
            }

        val iss = claims.issuer ?: throw GoogleTokenVerificationException("Missing issuer claim")
        if (iss !in ALLOWED_ISSUERS) throw GoogleTokenVerificationException("Invalid issuer: $iss")

        val clientId = properties.google.clientId
        if (clientId.isNotBlank()) {
            val aud = claims.audience
            if (clientId !in aud) throw GoogleTokenVerificationException("Invalid audience: expected $clientId")
        }

        val email = claims["email"]?.toString() ?: throw GoogleTokenVerificationException("Missing email claim")
        val allowedDomain = properties.google.allowedDomain
        if (allowedDomain.isNotBlank() && !email.endsWith("@$allowedDomain")) {
            throw GoogleTokenVerificationException("Email domain not allowed: $email")
        }

        val emailVerified =
            when (val v = claims["email_verified"]) {
                is Boolean -> v
                is String -> v.toBoolean()
                else -> false
            }
        if (!emailVerified) throw GoogleTokenVerificationException("Email not verified by Google")

        return GoogleIdentityClaims(
            sub = claims.subject ?: throw GoogleTokenVerificationException("Missing subject claim"),
            email = email,
            emailVerified = emailVerified,
            name = claims["name"]?.toString(),
        )
    }

    internal fun parseKeyMap(jwksJson: String): Map<String, PublicKey> {
        val tree: JsonNode = MAPPER.readTree(jwksJson)
        val result = mutableMapOf<String, PublicKey>()
        tree["keys"]?.forEach { node ->
            val kty = node["kty"]?.asText() ?: return@forEach
            val kid = node["kid"]?.asText() ?: return@forEach
            if (kty == "RSA") {
                val n = node["n"]?.asText() ?: return@forEach
                val e = node["e"]?.asText() ?: return@forEach
                result[kid] = buildRsaPublicKey(n, e)
            }
        }
        return result
    }

    private fun extractKidFromHeader(jwt: String): String? =
        try {
            val headerB64 = jwt.substringBefore(".")
            val headerJson = String(Base64.getUrlDecoder().decode(headerB64))
            MAPPER.readTree(headerJson)["kid"]?.asText()
        } catch (_: Exception) {
            null
        }

    private fun buildRsaPublicKey(
        nBase64: String,
        eBase64: String,
    ): RSAPublicKey {
        val decoder = Base64.getUrlDecoder()
        val modulus = BigInteger(1, decoder.decode(nBase64))
        val exponent = BigInteger(1, decoder.decode(eBase64))
        return KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent)) as RSAPublicKey
    }
}
