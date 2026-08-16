# 生产数据库迁移前盘点（2026-08-14）

本文件记录 Supabase 生产数据库在迁移准备阶段的非敏感规模信息，用于数据复制后的行数和 identity sequence 校验。不包含用户名称、答案内容或连接凭据。

| 表 | 行数 | 最大 ID |
|---|---:|---:|
| `student` | 2 | 5 |
| `article_answer_keys` | 261 | 1549 |
| `article_question_submissions` | 208 | 521 |
| `wrong_questions` | 22 | 28 |

## 使用方式

- 数据复制完成后，四张表的行数应与迁移窗口内取得的最终盘点一致。
- 每张表的 identity sequence 必须重置到不小于迁移后 `MAX(id)` 的位置。
- 行数小于最大 ID 表明历史上存在正常的 ID 空洞，迁移时不得通过重新编号压缩主键。

## 数据一致性检查

同日重新执行统一 JSON 审计报告，结果如下：

| 检查 | 异常数 |
|---|---:|
| 学习者用户名重复 | 0 |
| `student.permissions` 为空 | 0 |
| 参考答案业务键重复 | 0 |
| 作答业务键重复 | 0 |
| 作答引用不存在的学习者 | 0 |
| 错题引用不存在的学习者 | 0 |
| 非法批阅状态或结果 | 0 |
| 待批阅作答包含批阅数据 | 0 |
| 已批阅作答缺少批阅数据 | 0 |
| `wrong_count` 小于 1 | 0 |
| 非数字形式的题目标识（信息项） | 0 |

生产数据满足当前业务唯一键、引用关系和批阅状态不变量，可以通过后续 Flyway migration 增加对应的严格约束。题目标识即使当前全部是数字形式，也继续按 `text` 保存，避免把显示编号误当成数据库整数身份。

## Identity migration 补充门禁

V3 会为用户名建立“去除首尾空白并转小写”后的唯一索引。原审计只验证了原始用户名完全相同的重复项，因此在把
V3 应用到生产库之前，还必须重新运行 `pre_migration_checks.sql` 中新增的以下两项，并确认异常数为 0：

- `student normalized username duplicate`
- `student blank normalized username`

这两项尚未包含在 2026-08-14 的审计结果中；在取得实际查询结果前，不得把 V3 直接应用到生产数据库。

## 2026-08-15 V3 确认与 Flyway 接管

用户报告再次执行 SQL 时收到 `42P07: relation "account" already exists`，第一次执行结果未保存。随后运行
`post_v3_identity_checks.sql`，得到：

- 两项规范化用户名门禁异常数均为 0。
- 三张身份表、账户字段、身份约束和身份索引缺失数均为 0。
- 学习者账户回填和 `young` 管理员角色回填异常数均为 0。
- `student_rows = 2`、`account_rows = 2`、激活码和会话行数均为 0。
- `flyway_history_exists = false`。

V3 数据库结构和回填结果完整。随后已经完成可恢复备份演练、V1/V2 reconciliation 和全部接管审计，并使用项目固定的
Flyway 版本建立 V3 baseline；`flyway_schema_history` 当前记录 version `3`、type `BASELINE`、success `true`。
正常后端启动已确认只做 validate，不会重复执行 V1 至 V3。不得删除身份表、再次执行 V3 或手工修改 Flyway 历史。

### V1/V2 漂移明细

后续 catalog 明细确认：

- `student.permissions` 仍允许 NULL，是真实漂移。
- 作答的 review state 组合约束、错题学习者外键和 `wrong_count >= 1` 约束缺失，是真实漂移。
- `set_updated_at()` 及三张内容数据表的更新时间触发器全部缺失，是真实漂移。
- 三个业务唯一约束和两个 question ID 索引实际存在，只是名称与 V1 不同，初次门禁属于名称误报。
- 作答表同时存在 `unique_blog_student_question_id` 和 `unique_submission` 两个等价唯一约束。该冗余不阻塞 baseline，
  当前不在生产接管窗口删除，后续单独评估锁影响并清理。
- `student.id` 使用 sequence default 而非 identity 声明，但 `pg_get_serial_sequence` 可识别且自动生成语义完整，不阻塞接管。

仓库的一次性 reconciliation 脚本只补充真实缺失对象，并把审计门禁改为接受等价名称；该脚本已在完成可恢复备份后
于 2026-08-15 在生产执行。接管检查的 10 项 `anomaly_count` 均为 0。

## 2026-08-15 身份与运行账户状态

- `young` 独立管理员账户已完成一次性自举，状态为 `active`、角色为 `administrator`，真实登录、登出和会话失效验证通过。
- 已创建无登录权限组 `tutor_base_runtime` 和暂未设置密码的登录角色 `tutor_base_app`。
- 运行角色不具备 superuser、createdb、createrole、replication、bypassrls 或 `public` schema CREATE 权限。
- 当前仅授予身份、会话和管理员学习者查询所需表权限，并为三张 RLS 身份表建立命令级 policy。
- `runtime_role_checks.sql` 的 6 项 `anomaly_count` 均为 0，连接限制为 5。
- 旧 Vue 尚未切换到 Java，原有 `anon` 业务表权限暂时保留；完成前端切换后再单独撤销。
