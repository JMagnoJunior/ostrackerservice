package magnojr.ostrackerservice.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info =
        Info(
            title = "OS Tracker Service API",
            version = "v1",
            description = "API para gestão e finalização de ordens de serviço.",
            contact = Contact(name = "OS Tracker Team"),
        ),
    servers = [Server(url = "/", description = "Default server")],
)
class OpenApiConfig
