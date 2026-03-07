package magnojr.ostrackerservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.security")
class SecurityProperties {
    lateinit var jwtSecret: String
    var jwtExpiration: Long = 86400000 // 24h
    lateinit var clientSecret: String
    var superuser: SuperuserProperties = SuperuserProperties()
    var google: GoogleProperties = GoogleProperties()
    var cors: CorsProperties = CorsProperties()

    class SuperuserProperties {
        var bootstrapEnabled: Boolean = true
        var email: String = "superuser@localhost"
        var displayName: String = "Superusuario Primario"
    }

    class GoogleProperties {
        var clientId: String = ""
        var jwksUri: String = "https://www.googleapis.com/oauth2/v3/certs"
        var allowedDomain: String = ""
        var clockSkewSeconds: Long = 0
        var httpTimeoutMs: Long = 5000
    }

    class CorsProperties {
        var allowedOrigins: List<String> = emptyList()
    }
}
