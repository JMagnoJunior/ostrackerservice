package magnojr.ostrackerservice.repository

import magnojr.ostrackerservice.model.NotificationLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationLogRepository : JpaRepository<NotificationLog, UUID>
