# Java 后端最小权限数据库账户

Java 服务正常运行时不得继续使用 Supabase 的 `postgres` 管理员账户。仓库提供：

- `database/operations/provision_runtime_role.sql`：建立无登录权限组 `tutor_base_runtime` 和登录角色
  `tutor_base_app`，只授予当前身份、会话、学习者创建及文章授权维护所需权限。
- `database/audit/runtime_role_checks.sql`：验证危险角色属性、成员关系、表权限、RLS policy 和连接数限制。

权限脚本不会设置密码，也不会撤销旧 Vue 前端其他尚未迁移模块仍依赖的 `anon` 业务表权限。本阶段的
`student` 读取、创建和授权维护已经改走 Java API；答题、批阅和错题完成迁移后，再通过独立 migration
统一撤销浏览器直写权限。

每次部署包含新数据库访问路径的后端版本前，都要先以管理员身份重新运行权限脚本，再运行审计脚本。
当前版本新增了 `student` 的 `INSERT`、`UPDATE`，`account` 的 `INSERT`，以及相关序列权限；未先更新权限时，
旧容器仍可健康运行，但新增学习者或保存文章授权会返回服务端错误。

## 执行顺序

1. 使用 Supabase SQL Editor 的 `postgres` 管理身份运行
   `database/operations/provision_runtime_role.sql`。脚本可以安全重复执行。
2. 为 `tutor_base_app` 生成独立的随机长密码。不要复用项目 `postgres` 密码、管理员登录密码或 CSRF secret。
3. 在受控的交互式 `psql` 会话中执行 `\password tutor_base_app`，按提示输入两次新密码。这样密码不会写入仓库脚本。
4. 运行 `database/audit/runtime_role_checks.sql`。所有 `anomaly_count` 必须为 `0`，并确认
   `connection_limit=5`。PostgreSQL 的受限角色视图不能可靠证明密码是否已设置，必须通过下一步实际连接验证。
5. 使用新密码实际连接一次。Java 后端使用 Session Pooler 时，用户名格式为
   `tutor_base_app.<project-ref>`，端口为 `5432`；
   JDBC URL 继续启用 SSL。

生产运行变量示例：

```text
SPRING_PROFILES_ACTIVE=production
DATABASE_URL=jdbc:postgresql://<region>.pooler.supabase.com:5432/postgres?sslmode=require
DATABASE_USERNAME=tutor_base_app.<project-ref>
DATABASE_PASSWORD=<独立运行账户密码>
DATABASE_MIGRATIONS_ENABLED=false
```

Flyway migration 必须使用单独的发布任务和管理凭据执行。正常应用容器保持
`DATABASE_MIGRATIONS_ENABLED=false`，防止运行账户获得 DDL 权限。
