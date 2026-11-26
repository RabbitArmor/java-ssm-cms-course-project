package edu.course.lab.cms.app;

import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.api.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final HealthMapper mapper;

    public HealthController(HealthMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.success(new HealthStatus("UP"));
    }

    @GetMapping("/db")
    public ApiResponse<HealthStatus> database() {
        try {
            if (mapper.ping() == 1) {
                return ApiResponse.success(new HealthStatus("UP"));
            }
        } catch (DataAccessException exception) {
            // 只返回就绪状态，连接详情由数据源在服务端记录。
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DB_UNAVAILABLE", "数据库暂不可用");
    }

    public record HealthStatus(String status) {
    }
}
