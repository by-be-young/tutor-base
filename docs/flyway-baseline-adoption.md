# Supabase 现有数据库接入 Flyway

## 当前状态

Supabase 中 V3 的三张身份表、字段、约束、索引和账户回填已经验证完整。2026-08-15 已使用项目固定的 Flyway
12.4.0 完成一次性 baseline，`public.flyway_schema_history` 当前只有一条成功记录：版本 `3`、类型 `BASELINE`、描述
`verified-existing-schema-through-v3`。正常后端启动已成功校验迁移并确认 schema 版本为 3，没有重新执行 V1 至 V3。

## 接管原则

- 不删除现有表、约束、索引或数据。
- 不给 V1、V2、V3 增加 `IF NOT EXISTS` 来掩盖结构差异。
- 不手工向 `flyway_schema_history` 插入记录或伪造 migration checksum。
- 先证明现有数据库与仓库 V1 至 V3 的最终状态兼容，再使用 Flyway 官方 baseline 操作将当前状态标记为 V3。
- baseline 前必须创建可恢复备份，并停止其他 schema 修改。

## 门禁一：只读结构核验

在 Supabase SQL Editor 运行：

```text
database/audit/flyway_adoption_checks.sql
```

保存最后返回的 `flyway_adoption_report`。所有 `anomaly_count` 必须为 `0`，行数应与迁移盘点一致：

- `student_rows = 2`
- `answer_key_rows = 261`
- `submission_rows = 208`
- `wrong_question_rows = 22`
- `account_rows = 2`

如果任何检查非零，停止接管，先比较真实数据库对象和对应 migration；不得通过删除对象让检查归零。
此时继续运行只读的 `database/audit/flyway_adoption_details.sql`，保存其 JSON。该报告列出四张原有业务表的实际字段、
约束定义、索引定义、触发器和更新时间函数，用于区分真正缺失与对象名称差异。

## 门禁二：备份和恢复能力

结构核验通过后，至少创建一个 Supabase 可恢复备份或使用 Supabase CLI 导出角色、结构和数据，并在隔离数据库完成一次
恢复验证。没有验证过恢复能力的备份不能作为本次接管的回滚保障。

本次接管已生成 `roles.sql`、`schema.sql`、完整 `data.sql` 和仅含 `public` schema 的 `data-public.sql`，并保存 SHA-256
清单。隔离的 Supabase PostgreSQL 17.6 容器已成功恢复角色、结构和 `public` 数据；四张学习平台业务表的恢复行数分别为
2、261、208、22。完整数据中的 `auth.audit_log_entries` 因云端 Auth schema 比单独数据库镜像更新而未在该容器中恢复，
因此完整原始备份继续保留，学习平台本次会修改的 `public` schema 恢复能力已验证。

## 对齐 V1/V2 漂移

真实 catalog 已确认缺少部分 V2 不变量，而三个唯一约束和两个索引只是名称不同。完成备份门禁后，在维护窗口执行：

```text
database/reconciliation/2026-08-15_align_existing_schema_through_v3.sql
```

该脚本在一个事务中执行，设置 5 秒锁等待和 30 秒语句超时，并在变更前重新检查 NULL 权限、批阅状态、非法错题计数和
孤儿引用。任一前置条件不满足时会整体失败；不要绕过检查。脚本不会删除等价唯一约束，也不会创建重复索引。

执行成功后重新运行 `database/audit/flyway_adoption_checks.sql`。所有 `anomaly_count` 必须为 0，业务表行数必须保持不变，
然后才能进入 baseline。

## 一次性 baseline（已完成）

baseline 是会写入数据库的操作。本次在前两道门禁通过后，通过 Spring Boot 集成的 Flyway 12.4.0 创建历史表，并以
版本 `3`、描述 `verified-existing-schema-through-v3` 建立 baseline。连接凭据只保存在被 Git 忽略的 `backend/.env`，
未使用浏览器 anon key，也未把数据库密码写入仓库或命令文本。

baseline 完成后必须立即执行：

1. 历史表只读核验：记录数为 1，当前版本为 3，类型为 `BASELINE`，状态成功。
2. 正常后端启动：Flyway 成功校验，当前 schema 版本为 3，没有待执行 migration。
3. readiness：`GET /actuator/health/readiness` 返回 `UP`，测试服务随后关闭，未开放公网流量。
4. 业务数据：baseline 后行数为 `student=2`、`article_answer_keys=266`、
   `article_question_submissions=208`、`wrong_questions=23`。相较 18:13 的备份多出的 5 条答案键和 1 条错题均带有
   baseline 执行前约一小时的创建时间，Flyway 日志也确认没有执行 migration，因此不是 baseline 写入。

以后不得删除或手工修改 `flyway_schema_history`，也不得再次启用 `baseline-on-migrate`。新增数据库变更必须使用高于 V3
的新 migration，并先在隔离数据库和 staging 验证。

## V4 既有签到表接管（已完成）

生产库在 Flyway V4 发布前已经存在 `public.daily_check_in`。2026-09-05 先执行
`database/audit/pre_v4_daily_checkin_checks.sql`，确认字段、约束、索引和 7 条既有签到数据均与 V4 一致，全部异常数为 0；
同时执行 `database/audit/pre_v5_rewards_checks.sql`，确认既有积分、任务和卡片表不存在结构或数据异常。

在以上只读门禁通过后，使用 Flyway 官方 `skipExecutingMigrations` 并将 `target` 严格限定为 V4，使 Flyway 记录 V4 的
真实 checksum 而不重复执行建表语句。接管后 V4 执行时间为 0 ms，历史版本为 4，签到数据仍为 7 条。V5 不在本次跳过
范围内，继续由正常 CD migration job 执行。

## V11 认领列（2026-10-27）

生产库上先执行了 `database/reconciliation/2026-10-27_learner_claiming.sql`（加 `student.claimed_by` / `claimed_at`
及配套外键、约束、触发器和索引），但**没有**写入历史表：当时历史版本仍是 10、schema 已含 V11。

随后一次部署直接让 `V11__add_learner_claiming.sql` 以普通 `migrate` 运行，因 `column "claimed_by" already exists`
失败（SQLState 42701），CD 按设计自动回滚到上一镜像，线上未中断。

修复方式是让 V11 **幂等**（与 `V7__support_gacha_collection.sql` 相同思路）：列用 `ADD COLUMN IF NOT EXISTS`，
外键/约束/触发器用「不存在才创建」的 `DO` 块，索引用 `CREATE INDEX IF NOT EXISTS`。这样无论数据库是全新还是
已含 V11 对象，migration 都能成功并把真实 checksum 记账进历史表。回归测试见
`FlywayMigrationTest#givenV11SchemaAppliedManuallyWhenFlywayReplaysV11ThenMigrationIsIdempotent`。

规则：

- **不要**再通过 `skipExecutingMigrations` 或手工插入历史表来对齐；幂等 migration 让 Flyway 自己记账即可。
- 反向场景（历史表已记录 V11、对象却丢失）不会自动修复，需要人工比对 V11 的对象清单后补建。

## V12 课件认领（2026-10-29）

`V12__add_courseware_claiming.sql` 从一开始就按幂等编写（`CREATE TABLE IF NOT EXISTS`、`CREATE INDEX IF NOT EXISTS`、
policy 用 `DO` 块判断存在性），并配套可重复执行的
`database/reconciliation/2026-10-29_courseware_claiming.sql`。因此无论走正常 CD migration，还是先在 Supabase
SQL Editor 手工对齐、再由 Flyway 记账，重复执行都不会失败。

- 3 张新表启用 RLS；运行时角色 policy 只在角色已存在时由 migration 创建，空库由 `provision_runtime_role.sql` 补建。
- 部署顺序保持“先运行 `provision_runtime_role.sql`，再执行 Flyway migration”，否则新表在本轮迁移时没有运行时
  policy，需要再跑一次 provision（脚本幂等）。

## V14 运行时权限自愈（2026-10-30）

staging 升级到含 V12 的后端后，管理端接口返回 `503 dependency_unavailable`：数据库已有新表，但运行时角色
`tutor_base_runtime` 没有 `courseware_catalog`/`admin_subject_claim`/`admin_courseware_claim` 的权限
（provision 脚本未在 staging 库重跑）。

修复方式是把授权做成 migration `V14__grant_courseware_runtime_privileges.sql`：以 schema owner 身份、在角色
存在时幂等补发 3 张表的 `SELECT/INSERT/UPDATE/DELETE`、两个 identity 序列 `USAGE` 与 RLS policy。空库上角色
尚不存在时跳过，由 `provision_runtime_role.sql` 创建。配套
`database/reconciliation/2026-10-30_runtime_courseware_grants.sql` 可手工对齐。

规则：新增表的运行时权限尽量通过 migration 自愈（`provision_runtime_role.sql` 仍保留为完整授权与手工兜底），
避免“代码已发布、权限未同步”导致的线上 503。

## V15 课件排除（2026-10-30）

`V15__add_courseware_exclusion.sql` 新增 `admin_courseware_exclusion`，让学科认领覆盖下的课件也能单独释放；
脚本幂等、包含排除表/唯一约束/索引/RLS，并在运行时角色存在时补发权限、序列与 policy（与 V14 同思路）。
配套 `database/reconciliation/2026-10-30_courseware_exclusion.sql` 可手工对齐。可见范围统一为
`(学科已认领且未排除) ∪ 单篇认领`，业务接口与 analytics 自动跟随。

