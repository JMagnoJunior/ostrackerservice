package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.TestcontainersConfiguration
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.NotificationLogRepository
import magnojr.ostrackerservice.repository.OrderRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.OffsetDateTime

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
class ReminderSchedulerIT {
    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var notificationLogRepository: NotificationLogRepository

    @Autowired
    private lateinit var reminderScheduler: ReminderScheduler

    @MockitoBean
    private lateinit var twilioClient: TwilioClient

    @AfterEach
    fun cleanup() {
        notificationLogRepository.deleteAll()
        orderRepository.deleteAll()
    }

    @Test
    fun `should send reminder only for eligible orders`() {
        `when`(twilioClient.sendMessage(anyString(), anyString())).thenReturn("sid-ok")

        val eligible =
            orderRepository.save(
                Order(
                    status = OrderStatus.FINALIZADA,
                    clientName = "Cliente Elegivel",
                    clientPhone = "5511999991111",
                    finalValue = BigDecimal("150.00"),
                    hashAccess = "eligible",
                    lastNotificationAt = OffsetDateTime.now().minusHours(26),
                    finishedAt = OffsetDateTime.now().minusDays(2),
                ),
            )
        orderRepository.save(
            Order(
                status = OrderStatus.AGUARDANDO_AGENDAMENTO,
                clientName = "Cliente Recente",
                clientPhone = "5511999992222",
                finalValue = BigDecimal("200.00"),
                hashAccess = "too-soon",
                lastNotificationAt = OffsetDateTime.now().minusHours(2),
                finishedAt = OffsetDateTime.now().minusDays(1),
            ),
        )
        orderRepository.save(
            Order(
                status = OrderStatus.AGENDADA_PRESENCIAL,
                clientName = "Cliente Agendado",
                clientPhone = "5511999993333",
                finalValue = BigDecimal("250.00"),
                hashAccess = "already-scheduled",
                lastNotificationAt = OffsetDateTime.now().minusHours(30),
                finishedAt = OffsetDateTime.now().minusDays(1),
            ),
        )

        reminderScheduler.runDailyReminderRetry()

        val logs = notificationLogRepository.findAll()
        assertEquals(1, logs.size)
        assertEquals(eligible.id, logs.first().orderId)
        assertEquals("REMINDER_24H", logs.first().type)
        assertEquals("SENT", logs.first().status)

        val updatedEligible = orderRepository.findById(eligible.id!!).orElseThrow()
        assertNotNull(updatedEligible.lastNotificationAt)
        assertTrue(updatedEligible.lastNotificationAt!!.isAfter(OffsetDateTime.now().minusMinutes(1)))
    }

    @Test
    fun `should register failed log and continue processing remaining orders`() {
        `when`(twilioClient.sendMessage(anyString(), anyString()))
            .thenThrow(RuntimeException("twilio down"))
            .thenReturn("sid-recovered")

        val first =
            orderRepository.save(
                Order(
                    status = OrderStatus.FINALIZADA,
                    clientName = "Primeiro Cliente",
                    clientPhone = "5511988881111",
                    finalValue = BigDecimal("300.00"),
                    hashAccess = "first-fail",
                    lastNotificationAt = OffsetDateTime.now().minusHours(30),
                    finishedAt = OffsetDateTime.now().minusDays(3),
                ),
            )
        val second =
            orderRepository.save(
                Order(
                    status = OrderStatus.FINALIZADA,
                    clientName = "Segundo Cliente",
                    clientPhone = "5511977772222",
                    finalValue = BigDecimal("350.00"),
                    hashAccess = "second-success",
                    lastNotificationAt = OffsetDateTime.now().minusHours(30),
                    finishedAt = OffsetDateTime.now().minusDays(2),
                ),
            )

        reminderScheduler.runDailyReminderRetry()

        val logsByOrder = notificationLogRepository.findAll().associateBy { it.orderId }
        assertEquals(2, logsByOrder.size)
        assertEquals("FAILED", logsByOrder[first.id]!!.status)
        assertEquals("SENT", logsByOrder[second.id]!!.status)
        assertEquals("REMINDER_24H", logsByOrder[first.id]!!.type)
        assertEquals("REMINDER_24H", logsByOrder[second.id]!!.type)

        val firstUpdated = orderRepository.findById(first.id!!).orElseThrow()
        val secondUpdated = orderRepository.findById(second.id!!).orElseThrow()
        assertNotNull(firstUpdated.lastNotificationAt)
        assertNotNull(secondUpdated.lastNotificationAt)
    }
}
