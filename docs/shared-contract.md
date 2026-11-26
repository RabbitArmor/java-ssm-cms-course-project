# 公共接口草案

以下约定供 A、B、C、D 对齐后纳入公共基线。

- 由 A 实现并注册一个 `CurrentUserProvider` Spring Bean，从服务端会话返回 `CurrentUser`，匿名请求返回空。登录后会话提供 `userId`、权限标识集合和单个 `labTypeId`。操作者 ID 从会话取得。
- `userId`、`categoryId`、`labTypeId` 统一使用 Java `Long` 和 MySQL `BIGINT`。内容分类负责文章归档，实验室类别限制定向通报的可见范围。
- 权限标识建议使用小写点分格式，例如 `article.submit`、`article.review`、`article.publish`、`audit.read`。缺少身份返回 HTTP 401，缺少权限返回 HTTP 403。
- 文章状态建议为 `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → OFFLINE`。退回进入 `RETURNED`，修订后回到 `IN_REVIEW`。C 统一实现读者端列表、搜索和详情的可见性过滤。
- API 接口使用 `/api` 前缀。成功响应包含 `code: "OK"`、`message` 和 `data`。失败保留对应 HTTP 状态，同时提供稳定的字符串 `code`。Bean Validation 与参数类型转换错误使用 `VALIDATION_FAILED`，不可解析的 JSON 请求体使用 `INVALID_REQUEST_BODY`。
- 列表使用 `page` 和 `size`，页码范围为 1 至 2147483647，默认每页 20 条，最大 100 条。返回的 `data` 使用 `items`、`page`、`size`、`total`。
- Java 包按 `edu.course.lab.cms.identity`、`taxonomy`、`content` 和 `platform` 组织。Mapper 加 `@Mapper`，XML 放在 `src/main/resources/mapper/` 下，并以模块名分目录。

## 审计接入

角色分配、分类启停、审核、发布和下架的成功记录在业务事务内调用 `AuditService.recordSuccess`。业务回滚时，对应成功记录一同回滚。写入失败记录时，由调用方捕获业务异常，在业务事务结束后调用 `recordFailure`，按需保留原业务异常并在服务端记录审计写入失败。动作和对象类型使用 `AuditConstants` 中定义的服务端常量，例如 `ASSIGN_ROLE`、`ENABLE_CATEGORY`、`DISABLE_CATEGORY`、`APPROVE`、`RETURN`、`PUBLISH`、`OFFLINE` 和 `USER`、`CATEGORY`、`ARTICLE`。

表中保存操作者 ID、动作、对象类型及 ID、发生时间和结果。查询接口 `GET /api/audit/logs` 要求 `audit.read`，支持按 `actorId`、`action` 和时间区间筛选。`from` 含边界，`to` 不含边界，使用 UTC 的 ISO 本地时间格式（例如 `2026-11-26T00:00:00`）。A 提供会话实现前，接口返回 401。

## 持久层约定

时间字段使用 UTC。MyBatis-Plus 实体的 `createdAt` 使用 `@TableField(fill = FieldFill.INSERT)`，`updatedAt` 使用 `@TableField(fill = FieldFill.INSERT_UPDATE)`。需要逻辑删除的实体在字段上标注 `@TableLogic`，默认 0 表示未删除、1 表示已删除。手写 SQL 由业务模块显式处理软删除过滤。
