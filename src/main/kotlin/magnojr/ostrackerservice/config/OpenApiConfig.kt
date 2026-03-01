package magnojr.ostrackerservice.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.context.annotation.Configuration

@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer",
)
@OpenAPIDefinition(
    info =
        Info(
            title = "OS Tracker Service API",
            version = "v1",
            description = "API para gestão e finalização de ordens de serviço.",
            contact = Contact(name = "OS Tracker Team"),
        ),
    servers = [Server(url = "/", description = "Default server")],
    security = [SecurityRequirement(name = "bearerAuth")],
)
class OpenApiConfig
