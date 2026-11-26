package edu.course.lab.cms.platform.audit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.course.lab.cms.platform.api.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditMapper mapper;

    public AuditService(AuditMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(long actorId, String action, String targetType, long targetId) {
        mapper.insert(actorId, action, targetType, targetId, "SUCCESS");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(long actorId, String action, String targetType, long targetId) {
        mapper.insert(actorId, action, targetType, targetId, "FAILURE");
    }

    @Transactional(readOnly = true)
    public PageResult<AuditEntry> list(AuditFilter filter) {
        var page = new Page<AuditEntry>(filter.getPage(), filter.getSize());
        var items = mapper.find(page, filter);
        return new PageResult<>(items, page.getCurrent(), page.getSize(), page.getTotal());
    }
}
