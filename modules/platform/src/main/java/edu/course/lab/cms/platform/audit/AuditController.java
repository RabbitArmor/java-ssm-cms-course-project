package edu.course.lab.cms.platform.audit;

import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.api.ApiResponse;
import edu.course.lab.cms.platform.api.PageResult;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit/logs")
public class AuditController {
    private final AuditService audit;
    private final ObjectProvider<CurrentUserProvider> userProvider;

    public AuditController(AuditService audit, ObjectProvider<CurrentUserProvider> userProvider) {
        this.audit = audit;
        this.userProvider = userProvider;
    }

    @GetMapping
    public ApiResponse<PageResult<AuditEntry>> list(@Valid @ModelAttribute AuditFilter filter) {
        var user = Optional.ofNullable(userProvider.getIfAvailable())
                .flatMap(CurrentUserProvider::currentUser)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"));
        if (!user.permissions().contains("audit.read")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有审计查询权限");
        }
        if (filter.getFrom() != null && filter.getTo() != null && !filter.getFrom().isBefore(filter.getTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE", "开始时间必须早于结束时间");
        }
        return ApiResponse.success(audit.list(filter));
    }
}
