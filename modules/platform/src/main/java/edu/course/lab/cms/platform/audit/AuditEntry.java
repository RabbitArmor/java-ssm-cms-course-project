package edu.course.lab.cms.platform.audit;

import java.time.LocalDateTime;

public record AuditEntry(long id, long actorId, String action, String targetType,
                         long targetId, String result, LocalDateTime occurredAt) {
}
