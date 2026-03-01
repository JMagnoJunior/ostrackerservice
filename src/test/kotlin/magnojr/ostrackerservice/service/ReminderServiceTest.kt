package magnojr.ostrackerservice.service

import magnojr.ostrackerservice.config.TwilioProperties
import magnojr.ostrackerservice.model.NotificationType
import magnojr.ostrackerservice.model.Order
import magnojr.ostrackerservice.model.OrderStatus
import magnojr.ostrackerservice.repository.OrderRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val smsService: SmsService = mock()
    private val twilioProperties =
        TwilioProperties().apply {
            accountSid = "sid"
            authToken = "token"
            fromNumber = "+1234567890"
            clientPortalUrl = "http://localhost:3000"
        }

    private val reminderService = ReminderService(orderRepository, smsService, twilioProperties)

    @Test
    fun `should request eligible statuses and send reminder sms`() {
        val order =
            Order(
                id = UUID.randomUUID(),
                status = OrderStatus.FINALIZADA,
                hashAccess = "abc123",
                clientPhone = "5511999999999",
            )
        whenever(orderRepository.findEligibleForReminder(any(), any(), any())).thenReturn(listOf(order))

        val processed = reminderService.process24hReminders(batchSize = 50)

        assertEquals(1, processed)
        verify(smsService).sendSms(
            eq(order.id!!),
            eq("5511999999999"),
            eq("Lembrete: sua OS está aguardando retirada. Acesse os detalhes e agende em: http://localhost:3000/abc123"),
            eq(NotificationType.REMINDER_24H),
        )

        val statusesCaptor = argumentCaptor<Collection<OrderStatus>>()
        val cutoffCaptor = argumentCaptor<OffsetDateTime>()
        verify(orderRepository).findEligibleForReminder(statusesCaptor.capture(), cutoffCaptor.capture(), any())
        assertEquals(setOf(OrderStatus.FINALIZADA, OrderStatus.AGUARDANDO_AGENDAMENTO), statusesCaptor.firstValue.toSet())

        val expectedCutoff = OffsetDateTime.now().minusHours(24)
        assertTrue(
            cutoffCaptor.firstValue.isBefore(expectedCutoff.plusMinutes(1)),
            "Cutoff should be around now-24h",
        )
    }

    @Test
    fun `should continue processing when sms throws`() {
        val order1 =
            Order(
                id = UUID.randomUUID(),
                status = OrderStatus.FINALIZADA,
                hashAccess = "first",
                clientPhone = "5511999991111",
            )
        val order2 =
            Order(
                id = UUID.randomUUID(),
                status = OrderStatus.AGUARDANDO_AGENDAMENTO,
                hashAccess = "second",
                clientPhone = "5511999992222",
            )
        whenever(orderRepository.findEligibleForReminder(any(), any(), any())).thenReturn(listOf(order1, order2))
        doThrow(RuntimeException("sms down")).whenever(smsService).sendSms(
            eq(order1.id!!),
            any(),
            any(),
            eq(NotificationType.REMINDER_24H),
        )

        val processed = reminderService.process24hReminders(batchSize = 10)

        assertEquals(2, processed)
        verify(smsService, times(2)).sendSms(any(), any(), any(), eq(NotificationType.REMINDER_24H))
    }
}
