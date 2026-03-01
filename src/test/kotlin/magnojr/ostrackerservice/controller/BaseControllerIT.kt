package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.service.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestClient

abstract class BaseControllerIT {
    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    private lateinit var jwtService: JwtService

    protected val anonymousClient: RestClient by lazy {
        RestClient.create("http://localhost:$port")
    }

    protected val restClient: RestClient by lazy {
        authenticatedClient()
    }

    protected fun authenticatedClient(token: String = jwtService.generateSystemToken()): RestClient =
        RestClient
            .builder()
            .baseUrl("http://localhost:$port")
            .defaultHeader("Authorization", "Bearer $token")
            .build()
}
