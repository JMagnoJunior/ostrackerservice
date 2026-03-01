package magnojr.ostrackerservice.service

import com.twilio.exception.TwilioException
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import magnojr.ostrackerservice.config.TwilioProperties
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class TwilioClient(
    private val properties: TwilioProperties,
) {
    private val logger = LoggerFactory.getLogger(TwilioClient::class.java)

    @Retryable(
        retryFor = [TwilioException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0),
    )
    fun sendMessage(
        recipient: String,
        content: String,
    ): String {
        logger.info("Sending SMS to $recipient")
        val message =
            Message
                .creator(
                    PhoneNumber(recipient),
                    PhoneNumber(properties.fromNumber),
                    content,
                ).create()
        return message.sid
    }

    @Recover
    fun recover(
        e: TwilioException,
        recipient: String,
        content: String,
    ): String? {
        logger.error("Failed to send SMS to $recipient after retries: ${e.message}")
        throw e
    }
}
