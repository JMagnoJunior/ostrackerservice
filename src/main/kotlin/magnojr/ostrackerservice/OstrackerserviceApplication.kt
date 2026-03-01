package magnojr.ostrackerservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableScheduling
class OstrackerserviceApplication

fun main(args: Array<String>) {
    runApplication<OstrackerserviceApplication>(*args)
}
