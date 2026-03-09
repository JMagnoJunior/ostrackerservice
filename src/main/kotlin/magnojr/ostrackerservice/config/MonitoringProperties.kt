package magnojr.ostrackerservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.monitoring")
class MonitoringProperties {
    var overdueHours: Long = 24
    var discardWarningDays: Long = 90
    var discardDeadlineDays: Long = 120
    var maxPageSize: Int = 200
    var noShowShiftEndManha: Int = 12
    var noShowShiftEndTarde: Int = 18
    var noShowShiftEndNoite: Int = 23
    var noShowZoneId: String = "America/Sao_Paulo"
}
