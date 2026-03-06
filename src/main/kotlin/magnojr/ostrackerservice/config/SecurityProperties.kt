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

    class SuperuserProperties {
        var bootstrapEnabled: Boolean = true
        var email: String = "superuser@localhost"
        var displayName: String = "Superusuario Primario"
    }
}
