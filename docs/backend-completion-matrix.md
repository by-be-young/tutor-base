# 后端完整化与测试交付矩阵

本文以用户旅程追踪“浏览器直连 Supabase”到“Java 后端唯一可信入口”的迁移状态。状态只有 `已接管`、`进行中`、`未接管`；仅有表或 controller 不算完成，必须同时具备服务端授权、事务、OpenAPI 和自动化测试。

## 当前基线（2026-09-05）

| 业务能力 | 当前数据路径 | 后端状态 | 完成所需验证 |
| --- | --- | --- | --- |
| 身份、会话、注册、改密 | Java `/api/v1` | 已接管 | Identity HTTP contract、管理员重置、会话撤销 |
| 学习者管理与内容授权 | Java `/api/v1/admin/learners` | 已接管 | 管理员/越权 contract、原子替换授权 |
| 每日签到 | Java `/api/v1/checkins` | 已接管 | 时区、月界、7/14 日奖励、并发幂等 |
| 积分与卡片收藏 | Java `/api/v1/rewards` | 已接管 | 后端目录决定奖励；数据库唯一键阻止重复领取 |
| 学习状态与保存作答 | Java `/api/v1/articles` | 已接管 | 内容授权、自动批阅与错题收集位于同一 transaction |
| 参考答案管理 | Java `/api/v1/admin/articles` | 已接管 | 管理员角色、整组保存、输入校验 |
| 人工批阅 | Java `/api/v1/admin/articles` | 已接管 | 管理员角色、合法结果、错题收集 |
| 错题本 | Java `/api/v1/wrong-book` | 已接管 | owner 隔离、手动加入、编辑、掌握、删除/复活 |
| 文章正文与目录 | GitHub Pages 静态文件 | 保留静态 | 明确授权只控制导航/学习数据，不代表正文保密 |

## 已完成的接管顺序

1. **Rewards**：统一积分余额与卡片领取，先消除客户端自报奖励值和未启用 RLS 的写入。
2. **Learning & Review**：保存作答、自动批阅、答案键和人工批阅；自动错题收集纳入同一 transaction。
3. **Wrong Book**：提供当前学习者的完整错题生命周期，并复用 Learning 的自动收集 interface。
4. **Frontend switch**：已新增 capability gateway，并删除运行时 Supabase client 及其 npm dependency。
5. **Security close**：代码和审计脚本已就绪；需在新版前端发布并验证后执行一次
   `database/operations/revoke_browser_table_access.sql`。

## 测试门槛

| 层级 | 必须覆盖 |
| --- | --- |
| Unit | 答案规范化与自动批阅；奖励目录映射；错题删除/复活规则 |
| PostgreSQL integration | Flyway V1→最新；唯一键/外键；积分原子变更；作答与错题同事务回滚 |
| HTTP contract | 每个 endpoint 的成功、401、403、400/409 与 Problem Details code |
| Release context | migration-only 非 Web context 不装配 Servlet SecurityFilterChain，Flyway 完成后正常退出 |
| Frontend | gateway 请求映射、CSRF 单次刷新、store 状态更新、无 Supabase import |
| E2E | Playwright 启动真实 Vue、Spring Boot 和 PostgreSQL，覆盖管理员建号/授权/设答案/设密、进入学习者、越权拒绝、作答、错题编辑删除、签到、奖励资格拒绝、人工批阅和自动错题收集 |

## CI/CD 完成标准

- 所有 PR 执行前端测试/构建、后端 `verify`、OpenAPI 校验、E2E 和仓库 secret scan。
- `main` 成功后发布 Pages 与不可变 GHCR 镜像；部署环境使用 GitHub Environment 审批和并发锁。
- migration job 成功后才替换应用；readiness 与 API smoke test 失败则停止发布。
- CD 在独立 SSH 会话中核对运行容器的 immutable image SHA，防止迁移成功但应用替换未执行时误报成功。
- dependency 与 action 固定版本，工作流使用最小权限，生产 secret 只存在 GitHub Environment/服务器受限 env 文件。
