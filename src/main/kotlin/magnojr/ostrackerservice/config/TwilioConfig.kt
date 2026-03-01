package magnojr.ostrackerservice.config

import com.twilio.Twilio
import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.sms", name = ["mock-enabled"], havingValue = "false")
class TwilioConfig(
    private val properties: TwilioProperties,
) {
    @PostConstruct
    fun initTwilio() {
        Twilio.init(properties.accountSid, properties.authToken)
    }
}
