# Java 后端最小权限数据库账户

Java 服务正常运行时不得继续使用 Supabase 的 `postgres` 管理员账户。仓库提供：

- `database/operations/provision_runtime_role.sql`：建立无登录权限组 `tutor_base_runtime` 和登录角色
  `tutor_base_app`，只授予当前身份、会话、学习者创建及文章授权维护所需权限。
- `database/audit/runtime_role_checks.sql`：验证危险角色属性、成员关系、表权限、RLS 已启用、policy 定义和连接数限制。

权限脚本不会设置密码，也不会替代浏览器角色撤权。所有动态业务已改走 Java API；新版前端完成生产
smoke 后，使用 `database/operations/revoke_browser_table_access.sql` 撤销 `anon`、`authenticated` 对身份、
学习、奖励表及其序列的权限，再用对应审计脚本确认全部异常数为 0。

每次部署包含新数据库访问路径的后端版本前，都要先以管理员身份重新运行权限脚本，再运行审计脚本。
当前版本新增了 `student` 的 `INSERT`、`UPDATE`、`DELETE`，`account` 的 `INSERT`、`DELETE`，课件目录与认领表
（`courseware_catalog`、`admin_subject_claim`、`admin_courseware_claim`）的读写权限，以及相关序列权限；
删除学员时运行角色直接删除 `student`/`account` 行，关联的会话、激活码、作答、错题、签到、积分、卡片与任务记录由
外键级联处理。未先更新权限时，旧容器仍可健康运行，但新增学习者、保存文章授权、课件认领或删除学员会返回服务端错误。

`V14__grant_courseware_runtime_privileges.sql` 会在迁移时自动为已存在的运行时角色补发课件表/序列权限与 RLS
policy（角色不存在时跳过，空库由 provision 脚本创建角色后仍建议手工运行一次）；如果升级前遗漏了 provision，
新 migration 会自愈这部分权限。

## 执行顺序

1. 使用 Supabase SQL Editor 的 `postgres` 管理身份运行
   `database/operations/provision_runtime_role.sql`。脚本可以安全重复执行。
   - 报错 `42P01: relation "public.student" does not exist` 不是表缺失，而是当前执行上下文把
     `search_path` 解析到了 `pg_catalog`（Supabase Database → Roles 的 SQL Editor 常见）。仓库脚本已内置
     `SET LOCAL search_path = public`；若仍报错，请确认用的是本仓库最新版本，或在 Supabase 的 SQL Editor
     （Database → SQL Editor）中执行，并在会话执行前追加 `SET search_path = public;`。
   - 先用 `SELECT current_user, current_schema(), current_setting('search_path');`
     确认上下文，再用 `SELECT to_regclass('public.student');` 确认表确实存在。
2. 为 `tutor_base_app` 生成独立的随机长密码。不要复用项目 `postgres` 密码、管理员登录密码或 CSRF secret。
3. 在受控的交互式 `psql` 会话中执行 `\password tutor_base_app`，按提示输入两次新密码。这样密码不会写入仓库脚本。
4. 运行 `database/audit/runtime_role_checks.sql`。所有 `anomaly_count` 必须为 `0`，并确认
   `connection_limit=20`（对应脚本中的 `CONNECTION LIMIT 20`）。PostgreSQL 的受限角色视图不能可靠证明密码
   是否已设置，必须通过下一步实际连接验证。
   - 若 `dangerous table privileges granted` 为 `1`，确认它恰好是 `account.DELETE`：删除学员需要删除 `account`
     行，这是有意授予的权限；`article_answer_keys` / `wrong_questions` 的 `UPDATE` 同样是保存答案和编辑错题所必需。
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
