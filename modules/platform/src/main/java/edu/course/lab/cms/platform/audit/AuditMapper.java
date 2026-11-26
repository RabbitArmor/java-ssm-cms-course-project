package edu.course.lab.cms.platform.audit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditMapper {
    @Insert("""
            INSERT INTO cms_operation_log (actor_id, action, target_type, target_id, result)
            VALUES (#{actorId}, #{action}, #{targetType}, #{targetId}, #{result})
            """)
    int insert(@Param("actorId") long actorId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("targetId") long targetId,
            @Param("result") String result);

    @Select("""
            <script>
            SELECT id, actor_id, action, target_type, target_id, result, occurred_at
            FROM cms_operation_log
            <where>
                <if test="filter.actorId != null">AND actor_id = #{filter.actorId}</if>
                <if test="filter.action != null">AND action = #{filter.action}</if>
                <if test="filter.from != null">AND occurred_at &gt;= #{filter.from}</if>
                <if test="filter.to != null">AND occurred_at &lt; #{filter.to}</if>
            </where>
            ORDER BY occurred_at DESC, id DESC
            </script>
            """)
    List<AuditEntry> find(Page<AuditEntry> page, @Param("filter") AuditFilter filter);
}
