# 后端完整化与测试交付矩阵

本文以用户旅程追踪“浏览器直连 Supabase”到“Java 后端唯一可信入口”的迁移状态。状态只有 `已接管`、`进行中`、`未接管`；仅有表或 controller 不算完成，必须同时具备服务端授权、事务、OpenAPI 和自动化测试。

## 当前基线（2026-10-31）

| 业务能力 | 当前数据路径 | 后端状态 | 完成所需验证 |
| --- | --- | --- | --- |
| 身份、会话、注册、改密 | Java `/api/v1` | 已接管 | Identity HTTP contract、管理员重置、会话撤销；`POST /accounts` 已补 OpenAPI |
| 教师注册审核 | Java `/api/v1/teacher-applications`、`/admin/teacher-applications` | 已接管 | V17 migration、申请/通过/驳回 contract、超级管理员越权 403、同事务建号、密码哈希复用 |
| 学习者管理与内容授权 | Java `/api/v1/admin/learners` | 已接管 | 管理员/越权 contract、原子替换授权、范围外授权保留 |
| 科目归属与文章授权分离 | Java `/api/v1/admin/learners/{id}/content-grants` | 已接管 | V18 migration、`enrolledSubjects` 契约、开通科目零授权、取消科目同步移除该科文章、科目范围校验 |
| 学员管理认领约束 | Java `/api/v1/admin/learners`、`/admin/analytics`、`/identity` | 已接管 | 查看详情/设密/进入/删除均校验认领、`403 learner_not_claimed`、特殊测试删除 409 contract |
| 多管理员学员认领 | Java `/api/v1/admin/learners` | 已接管 | V11 migration、FOR UPDATE 认领/取消认领、409 冲突 contract、删除学员级联 |
| 多管理员课件认领 | Java `/api/v1/admin/courseware-claims` | 已接管 | V12/V15/V19 migration、目录同步、学科多对多认领、单篇认领与学科覆盖下单独释放（排除）、放弃课件（`scope=abandoned`、不计入待认领）、业务接口按“我的课件”收敛 |
| 测试学员与特殊测试学员 | Java `/api/v1/admin/learners`、`/rewards` | 已接管 | V16 migration、测试标记、唯一特殊测试、全课件权限、固定高积分、待批阅/提交/趋势/待批阅目标全部排除测试学员、管理端新增入口固定创建测试学员、特殊测试禁止删除 |
| 管理端统计与学员详情 | Java `/api/v1/admin/analytics`、`/admin/learners/{id}/profile` | 已接管 | 只读聚合、按“我的课件”过滤、概览不含待认领课件、`pendingReviewTarget` 待批阅直达、测试学员排除、积分/签到全局口径、HTTP contract |
| 学员最近动态 | Java `/api/v1/admin/learners` | 已接管 | 服务端聚合作答/错题/签到/登录时间，前端只用于排序与展示 |
| 每日签到 | Java `/api/v1/checkins` | 已接管 | 时区、月界、7/14 日奖励、并发幂等 |
| 积分与卡片收藏 | Java `/api/v1/rewards` | 已接管 | 后端目录决定奖励；数据库唯一键阻止重复领取 |
| 成长等级 | 前端推导自 `/rewards` 积分 | 展示层 | 等级只读积分；升级动画与跨页面一致性 |
| 分级成就 | Java `/api/v1/achievements` | 进行中 | V6 migration、领取幂等、积分原子累加、HTTP contract |
| 周期/限时任务 | Java `/api/v1/tasks` | 进行中 | 日/周边界、接取截止、过期重接、领取幂等、HTTP contract |
| 学习状态与保存作答 | Java `/api/v1/articles` | 已接管 | 内容授权、自动批阅与错题收集位于同一 transaction |
| 参考答案管理 | Java `/api/v1/admin/articles` | 已接管 | 管理员角色、整组保存、输入校验、仅限“我的课件” |
| 人工批阅 | Java `/api/v1/admin/articles` | 已接管 | 管理员角色、合法结果、错题收集；只处理“我的课件”且学员已授权文章 |
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

## 课件托管化（2026-11-01）

| 能力 | 状态 | 已完成 | 待完成 / 验证方式 |
| --- | --- | --- | --- |
| 数据模型（老师命名空间、发布版本、分享/获取、快照列） | 进行中 | V21 基础表与列回填、V22 `course_notification`、V23 `course_layout`，均配幂等 reconciliation；`FlywayMigrationTest` 覆盖空库、增量与幂等（当前版本 25） | 换主键/唯一键（V26+）与代码切换同批上线 |
| 契约 | 进行中 | `openapi.yaml` 新增 `GET /courseware/manifests`、`GET /courseware/articles/{teacherId}/{articleId}/content`、`POST /admin/courseware/releases`；`CoursewareReleaseHttpContractTest` 覆盖发布→清单→正文、409/400/401/403 |
| 发布与读取（P0） | 已完成 | 存储抽象与内容寻址本地实现、发布校验（题干/占位符/答案键/路径/大小/注入）、发布器（不可变 manifest + head 切换）、正文鉴权、存量种子导入 `CoursewareSeeder`、前端 `blogStore`/正文读取切 `/courseware/*` 并保留静态兜底、`tutor.courseware.source=static\|storage` 开关（生产默认 static，待持久卷就绪再切） | 生产可写卷/对象存储后切 `storage` 并重跑种子导入 |
| 分享/获取/更新（P1） | 已完成 | 分享（整科/单篇，幂等刷新）、资源目录、共享树、来源预览（`allow_preview`）、获取（内容寻址复制 → 未发布草稿 + `course_copy` 上游指针；重复获取幂等；同名路径默认 409、`conflictStrategy=rename` 自动改名）、改名改路径、我的获取关系（含 `hasUpdate`）、更新汇总/单篇 diff/覆盖（先备份）/忽略（`expectedSourceHash` 乐观锁）、消息提醒（V22 `course_notification`：被获取、来源更新、来源撤回；未读筛选与全部已读）、管理端「共享」页（分享/资源目录/我的获取/更新） | 通知中心独立页与角标接入导航（可选打磨）；生产切 `storage` 后才会对外供数 |
| 在线编辑器与 Git 工作区（P2/P3） | 已完成 | V23 布局注册表、V24 模板库（通用 + 科目模板，内置 4 套骨架）；草稿 CRUD、预检、从草稿发布；导出 zip 与导入（落草稿、默认跳过、可覆盖）；`/admin/editor`（源码 + 预览 + 大纲上移下移 + 模板选择 + 插入块 + 校验 + 保存 + 发布 + 导入导出）；Git 工作区：`workspace/manifest`（无正文）+ `workspace/files` + `workspace/push`（`baseVersion` 乐观锁，404/409/400 全覆盖）+ CLI `courseware-cli.mjs`（pull/status/push，冲突不覆盖） | 所见即所得、拖拽排序（当前为上移/下移 / CLI） |
| 认领模型下线（P4） | 进行中 | 学习记录快照已落地（作答/错题写 `stem_snapshot`/`answer_snapshot`/`subject`，错题本响应 `stemSnapshot`，课件改版不影响历史题干）；认领写入下线开关 `tutor.courseware.legacy-claims-enabled`（false → 410 `courseware_claims_retired`，读接口保留）；多老师分组 `blogStore.teacherGroups`；新「我的课件」概览 `GET /admin/courseware/overview` + `/admin/courseware` 页，导航「课件」已切换，旧认领页降为「旧版认领」 | 删除旧认领页与 `coursewareStore` 认领逻辑（确认无引用后）；V25 换主键 `(teacher, article_id)` |
| 配额与运维（P5） | 已完成 | 存储配额 `tutor.courseware.quota-bytes`（超限 413 `courseware_quota_exceeded`）、用量统计（我的 / 全站，含孤儿数）、孤儿 blob 回收（默认 dryRun，引用判定覆盖历史版本与草稿）、全站指标 `GET /maintenance/metrics`、老师归档 `POST /maintenance/archive`（V25 审计动作 `archive`） | Prometheus 导出、导出包过期清理 |

回归要求：`npm run test:courseware`、前端 70 项单测、SFC 检查与课件校验保持通过；`mvnw verify` 中 `FlywayMigrationTest` 必须包含新增表与列的断言。
