package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.config.SecurityProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

abstract class BaseControllerIT {
    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    private lateinit var securityProperties: SecurityProperties

    protected val anonymousClient: RestClient by lazy {
        RestClient.create("http://localhost:$port")
    }

    protected val restClient: RestClient by lazy {
        authenticatedClient()
    }

    protected fun authenticatedClient(token: String = issueToken()): RestClient =
        RestClient
            .builder()
            .baseUrl("http://localhost:$port")
            .defaultHeader("Authorization", "Bearer $token")
            .build()

    private fun issueToken(): String {
        val response =
            anonymousClient
                .post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("clientSecret" to securityProperties.clientSecret))
                .retrieve()
                .toEntity(Map::class.java)

        val token = response.body?.get("token") as? String
        require(!token.isNullOrBlank()) { "Failed to issue auth token for integration test client" }
        return token
    }
}
