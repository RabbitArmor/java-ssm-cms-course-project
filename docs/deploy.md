# 本地构建与部署

公共工程使用 JDK 17 或更高版本、Maven 3.9、Tomcat 10.1 和 MySQL 8.0。当前构建目标为 Java 17。应用通过 Spring MVC 初始化 Servlet，通过 MyBatis-Plus 配置 Mapper 和分页。

实验二 EMP 参考工程使用 JDK 11、Spring 5.3、Tomcat 9 和 `javax.servlet`。课程设计延续其全注解 SSM 和 WAR 部署结构，公共基线采用 Spring 6.2 及本机可用的 Tomcat 10.1，Servlet 包名使用 `jakarta.servlet`。

## 初始化数据库

使用 MySQL 客户端登录具有建库权限的账号，在仓库根目录执行以下 SQL。为应用配置指向该库的账号。运行时账号至少需要读取和写入应用所用表。

```sql
CREATE DATABASE IF NOT EXISTS lab_safety_cms CHARACTER SET utf8mb4;
USE lab_safety_cms;
SOURCE db/init/001-platform.sql;
```

## 构建和启动

在仓库根目录运行 `mvn clean verify`。WAR 位于 `cms-app/target/cms.war`。复制 WAR 到 Tomcat 的 `webapps`，在启动 Tomcat 的终端设置下列环境变量。示例使用 PowerShell。

```powershell
$env:CMS_DB_URL = 'jdbc:mysql://127.0.0.1:3306/lab_safety_cms?connectionTimeZone=UTC'
$env:CMS_DB_USER = 'lab_cms'
$secret = Read-Host 'MySQL password' -AsSecureString
$env:CMS_DB_PASSWORD = [Net.NetworkCredential]::new('', $secret).Password
& "$env:CATALINA_HOME\bin\catalina.bat" run
```

默认健康检查地址为 `http://localhost:8080/cms/api/health`，它只检查 HTTP 服务。访问 `http://localhost:8080/cms/api/health/db` 会执行 MySQL 的 `SELECT 1`，数据库不可达时返回 HTTP 503。将 [OpenAPI 定义](apifox.openapi.json) 和 [Postman 验收集合](apifox.postman_collection.json) 导入 Apifox。调整集合中的 `baseUrl` 与实际 Tomcat 端口一致，数据库停机用例在隔离测试实例上单独执行。

## 审计事务验证

在专用测试库初始化日志表后，使用测试库连接参数并设置 `CMS_VERIFY_MYSQL=true`，再执行 `mvn clean verify`。测试通过真实 MySQL 验证审计记录的提交和回滚，并清理本次测试写入的记录。
