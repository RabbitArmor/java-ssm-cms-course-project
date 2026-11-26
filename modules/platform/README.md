# 审计与集成

模块 D 负责 Maven 公共工程、运行配置、统一响应与校验、操作日志以及部署和联调。应用由 `cms-app` 统一打成 WAR，业务模块通过公共 API 类型及明确的审计调用接入。

## 交付

- [部署与验证命令](../../docs/deploy.md)
- [共享接口草案](../../docs/shared-contract.md)
- [审计表 SQL](../../db/init/001-platform.sql)
- [Apifox 接口定义](../../docs/apifox.openapi.json)和[验收集合](../../docs/apifox.postman_collection.json)
- [文章列表 SQL 对比方法](performance.md)

## 设计与验证记录

- 审计写入保存操作者、动作、对象、时间和结果，服务端通过 `AuditConstants` 统一动作和对象类型常量并在写入前校验参数合法性。成功记录与业务变更同事务，失败记录可独立提交。日志保留操作者的 ID 快照，业务账号变更后仍能追溯。
- 审计查询按操作者、动作和 UTC 时间区间（`[from, to)`，支持可选单个 1 至 6 位小数秒段以匹配 `TIMESTAMP(6)`，不接受重复小数点或 `Z`/`+08:00` 等时区标记）分页筛选。接口从身份模块提供的会话取得权限，要求 `audit.read`。
- 2026-11-26 使用 JDK 21（构建目标 Java 17）、Maven 3.9.9 和隔离 MySQL 8.0.44。18 项测试通过，覆盖真实数据库连接（含数据源显式配置 `connectionTimeZone=UTC`）、成功随业务提交/回滚、失败独立提交（事务内与事务外）、分页与时间边界、严格 UTC 时间解析、HTTP 权限与参数校验、全局异常处理（含统一对外参数名、JSON 解析失败、类型不匹配、绑定失败及未预期异常脱敏）以及 UTC 自动填充。
- 执行 `mvn clean verify` 通过，产物为 `cms-app/target/cms.war`。
- 部署到独立 Tomcat 10.1.55 后，HTTP 存活和 MySQL 就绪均返回 200，匿名审计查询返回 401（`UNAUTHENTICATED`），非法页大小和带时区偏移的时间参数均返回 400（`VALIDATION_FAILED`）。停止临时 MySQL 后，HTTP 存活仍返回 200，MySQL 就绪返回 503（`DB_UNAVAILABLE`）。

## 全组联调待办

- 与 A 确认 `CurrentUserProvider` 会话实现、管理员 `audit.read` 权限，以及未登录请求与 `@Valid` 参数校验的先后顺序（当前 Spring MVC 参数校验先于方法体/AOP 执行；如需未登录请求一律先返回 401，需在 Filter/Interceptor 层统一拦截）。
- 与 A、B、C 接入角色分配、分类启停、审核、发布和下架的审计写入（统一使用 `AuditConstants`），核对真实会话下的日志与事务回滚行为。
- 与 C 用相同数据、页大小和筛选条件实测 SQL 次数，按 [performance.md](performance.md) 记录优化前后结果。公共接口已通过实际 HTTP 请求验证，Apifox 客户端导入和完整演示场景放在组内联调时跑。

## AI 辅助与复核

本轮使用 Codex 根据共享交接和课程指导书搭建工程、起草接口并生成测试。核对了远程 `main` 的基线、实验二 EMP 的依赖和容器版本，用本机 Maven、MySQL 和 Tomcat 验证实现。首次部署时 Tomcat 未自动发现 JDBC 驱动，显式配置 MySQL 驱动后复测通过。组员可用上面的部署步骤独立复跑，并在个人报告中补充手工核验记录。
