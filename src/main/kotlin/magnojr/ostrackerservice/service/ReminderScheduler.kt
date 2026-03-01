package magnojr.ostrackerservice.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "jobs.reminder-retry",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class ReminderScheduler(
    private val reminderService: ReminderService,
    @Value("\${jobs.reminder-retry.batch-size:200}")
    private val batchSize: Int,
) {
    private val logger = LoggerFactory.getLogger(ReminderScheduler::class.java)

    @Scheduled(cron = "\${jobs.reminder-retry.cron:0 0 9 * * *}")
    fun runDailyReminderRetry() {
        val processed = reminderService.process24hReminders(batchSize)
        logger.info("US04 reminder retry finished. processed={}", processed)
    }
}
