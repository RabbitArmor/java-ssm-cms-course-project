package edu.course.lab.cms.platform.audit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.course.lab.cms.platform.api.PageResult;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final AuditMapper mapper;

    public AuditService(AuditMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(long actorId, String action, String targetType, long targetId) {
        validateRecordArguments(actorId, action, targetType, targetId);
        mapper.insert(actorId, action, targetType, targetId, "SUCCESS");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(long actorId, String action, String targetType, long targetId) {
        validateRecordArguments(actorId, action, targetType, targetId);
        mapper.insert(actorId, action, targetType, targetId, "FAILURE");
    }

    @Transactional(readOnly = true)
    public PageResult<AuditEntry> list(AuditFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        if (filter.getFrom() != null && filter.getTo() != null && !filter.getFrom().isBefore(filter.getTo())) {
            throw new IllegalArgumentException("from must be before to");
        }
        var page = new Page<AuditEntry>(filter.getPage(), filter.getSize());
        var items = mapper.find(page, filter);
        return new PageResult<>(items, page.getCurrent(), page.getSize(), page.getTotal());
    }

    private static void validateRecordArguments(long actorId, String action, String targetType, long targetId) {
        if (actorId <= 0) {
            throw new IllegalArgumentException("actorId must be positive");
        }
        if (targetId <= 0) {
            throw new IllegalArgumentException("targetId must be positive");
        }
        if (action == null || !IDENTIFIER_PATTERN.matcher(action).matches()) {
            throw new IllegalArgumentException("action must match [A-Z][A-Z0-9_]{0,63}");
        }
        if (targetType == null || !IDENTIFIER_PATTERN.matcher(targetType).matches()) {
            throw new IllegalArgumentException("targetType must match [A-Z][A-Z0-9_]{0,63}");
        }
    }
}
