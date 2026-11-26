package edu.course.lab.cms.app;

import edu.course.lab.cms.platform.audit.AuditConstants;
import edu.course.lab.cms.platform.audit.AuditFilter;
import edu.course.lab.cms.platform.audit.AuditService;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "CMS_VERIFY_MYSQL", matches = "true")
class AuditMySqlTest {
    @Test
    void successFollowsBusinessTransactionAndFailureSurvivesRollback() {
        try (var context = new AnnotationConfigApplicationContext(PersistenceConfig.class)) {
            var audit = context.getBean(AuditService.class);
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            long targetId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
            try {
                assertEquals(1, context.getBean(HealthMapper.class).ping());
                assertThrows(IllegalTransactionStateException.class,
                        () -> audit.recordSuccess(targetId, AuditConstants.ACTION_PUBLISH,
                                AuditConstants.TARGET_ARTICLE, targetId));

                transaction.executeWithoutResult(status ->
                        audit.recordSuccess(targetId, AuditConstants.ACTION_APPROVE,
                                AuditConstants.TARGET_ARTICLE, targetId));
                transaction.executeWithoutResult(status -> {
                    audit.recordSuccess(targetId, AuditConstants.ACTION_PUBLISH,
                            AuditConstants.TARGET_ARTICLE, targetId);
                    audit.recordFailure(targetId, AuditConstants.ACTION_PUBLISH,
                            AuditConstants.TARGET_ARTICLE, targetId);
                    status.setRollbackOnly();
                });
                // 模拟业务事务回滚结束后，由调用方独立写入失败记录
                audit.recordFailure(targetId, AuditConstants.ACTION_OFFLINE,
                        AuditConstants.TARGET_ARTICLE, targetId);

                var results = jdbc.queryForList("""
                        SELECT CONCAT(action, ':', result) FROM cms_operation_log
                        WHERE target_type = 'ARTICLE' AND target_id = ?
                        ORDER BY id
                        """, String.class, targetId);
                assertEquals(List.of("APPROVE:SUCCESS", "PUBLISH:FAILURE", "OFFLINE:FAILURE"), results);

                var filter = new AuditFilter();
                filter.setActorId(targetId);
                filter.setAction(AuditConstants.ACTION_PUBLISH);
                var page = audit.list(filter);
                assertEquals(1, page.total());
                assertEquals(targetId, page.items().get(0).targetId());
                assertEquals("FAILURE", page.items().get(0).result());
                var occurredAt = page.items().get(0).occurredAt();
                filter.setFrom(occurredAt);
                assertEquals(1, audit.list(filter).total());
                filter.setFrom(null);
                filter.setTo(occurredAt);
                assertEquals(0, audit.list(filter).total());

                // 验证分页与降序排序 (occurred_at DESC, id DESC)
                var paged = new AuditFilter();
                paged.setActorId(targetId);
                paged.setPage(1);
                paged.setSize(2);
                var firstPage = audit.list(paged);
                assertEquals(3, firstPage.total());
                assertEquals(2, firstPage.items().size());
                assertTrue(firstPage.items().get(0).id() > firstPage.items().get(1).id());

                paged.setPage(2);
                var secondPage = audit.list(paged);
                assertEquals(3, secondPage.total());
                assertEquals(1, secondPage.items().size());
                assertTrue(firstPage.items().get(1).id() > secondPage.items().get(0).id());
            } finally {
                jdbc.update("DELETE FROM cms_operation_log WHERE target_type = 'ARTICLE' AND target_id = ?", targetId);
            }
        }
    }
}
