package magnojr.ostrackerservice.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

@Configuration
@ConfigurationProperties(prefix = "twilio")
@Validated
class TwilioProperties {
    @NotBlank
    lateinit var accountSid: String

    @NotBlank
    lateinit var authToken: String

    @NotBlank
    lateinit var fromNumber: String

    @NotBlank
    lateinit var clientPortalUrl: String
}
