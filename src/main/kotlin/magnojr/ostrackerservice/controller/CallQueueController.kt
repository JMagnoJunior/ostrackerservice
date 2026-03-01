package magnojr.ostrackerservice.controller

import magnojr.ostrackerservice.service.CallQueueService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/orders")
class CallQueueController(
    private val callQueueService: CallQueueService,
) {
    @GetMapping("/call-queue")
    fun getCallQueue(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<CallQueueOrderDTO> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        return callQueueService.getCallQueue(PageRequest.of(safePage, safeSize))
    }
}