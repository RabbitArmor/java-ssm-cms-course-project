package edu.course.lab.cms.app;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import edu.course.lab.cms.platform.api.PageResult;
import edu.course.lab.cms.platform.audit.AuditConstants;
import edu.course.lab.cms.platform.audit.AuditFilter;
import edu.course.lab.cms.platform.audit.AuditService;
import edu.course.lab.cms.platform.persistence.TimestampFillHandler;
import edu.course.lab.cms.platform.session.CurrentUser;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformUnitTest {
    @Test
    void auditServiceRejectsInvalidWriteAndQueryArguments() {
        var service = new AuditService(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSuccess(0, AuditConstants.ACTION_PUBLISH, AuditConstants.TARGET_ARTICLE, 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordFailure(1, AuditConstants.ACTION_PUBLISH, AuditConstants.TARGET_ARTICLE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSuccess(1, "publish", AuditConstants.TARGET_ARTICLE, 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordFailure(1, AuditConstants.ACTION_PUBLISH, "article", 1));
        assertThrows(NullPointerException.class, () -> service.list(null));

        var badRange = new AuditFilter();
        badRange.setFrom(LocalDateTime.of(2026, 11, 26, 12, 0));
        badRange.setTo(LocalDateTime.of(2026, 11, 26, 12, 0));
        assertThrows(IllegalArgumentException.class, () -> service.list(badRange));
    }

    @Test
    void currentUserAndPageResultHandleNullCollectionsDefensively() {
        var user = new CurrentUser(1L, null, null);
        assertTrue(user.permissions().isEmpty());

        var page = new PageResult<String>(null, 1, 20, 0);
        assertEquals(List.of(), page.items());
    }

    @Test
    void timestampFillHandlerPopulatesUtcTimestamps() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SampleEntity.class);
        var handler = new TimestampFillHandler();
        var entity = new SampleEntity();
        var before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(2);
        handler.insertFill(SystemMetaObject.forObject(entity));
        var after = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(2);

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
        assertTrue(!entity.getCreatedAt().isBefore(before) && !entity.getCreatedAt().isAfter(after));

        entity.setUpdatedAt(null);
        handler.updateFill(SystemMetaObject.forObject(entity));
        assertNotNull(entity.getUpdatedAt());
    }

    static class SampleEntity {
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updatedAt;

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
