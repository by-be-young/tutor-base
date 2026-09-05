# Agent 协作指南

本仓库是 Vue 3 前端、Java 模块化单体和 PostgreSQL（当前托管于 Supabase）的单仓库项目。所有 Agent 在修改代码前先阅读本文件、`CONTEXT.md`、`docs/code-standards.md`、`docs/api-standards.md` 以及所触及 module 的测试。

## 开发原则

- 从最新 `main` 创建 `codex/<topic>` 分支；保留并说明已有未提交修改，不覆盖他人工作。
- 按业务能力组织 Java package。controller 是 HTTP adapter；事务、授权、状态转换和 SQL 位于对应深 module 的 implementation 内。
- 浏览器是不可信 caller。不得接受客户端提供的当前学习者 ID、奖励积分、角色、批阅时间或资源归属作为事实。
- Vue 页面、composable 和 Pinia store 不得直接导入 Supabase client。新业务数据统一通过 `tutor-nest-vue/src/gateways/backendClient.js` 调用 `/api/v1`。
- 数据库结构只通过新的 Flyway migration 演进。已经共享或执行的 migration 不得修改；生产对齐 SQL放在 `database/reconciliation/` 并保持可重复执行。
- 不提交 `.env`、密码、数据库连接串、cookie、token、证书、构建产物或真实学习答案。

## 必须同步维护的契约

一次行为变更通常同时涉及以下资产：

1. `CONTEXT.md`：只记录领域词汇，不写实现细节。
2. `backend/api/openapi.yaml`：已实现的 HTTP interface。
3. `docs/api-standards.md`：跨 endpoint 约定和目标目录。
4. `docs/backend-completion-matrix.md`：迁移覆盖率与验收状态。
5. 对应的 Java、前端 gateway/store、migration 和测试。

## 验证层级

- 纯业务规则：JUnit 单元测试，不启动 Spring 或数据库。
- SQL、约束、并发和事务：Testcontainers PostgreSQL 集成测试；禁止用 H2 代替。
- HTTP、安全和 JSON contract：MockMvc 测试成功、校验失败、未认证、越权和稳定 Problem Details code。
- 前端 gateway：Node/Vitest 测试 CSRF、请求/响应映射和失败恢复。
- 用户旅程：Playwright 覆盖登录、授权、作答/批阅/错题、签到/积分/卡片和管理员越权。

提交前至少运行：

```powershell
npm ci
npm run test:frontend
npm run test:openapi
npm run test:secrets
npm run build

cd backend
.\mvnw.cmd -B -ntp verify
```

涉及跨前后端用户旅程时再运行 `npx playwright install chromium` 和 `.\scripts\e2e-local.ps1`。

如果本机没有 Java 21，可在已安装 Docker 的环境运行 CI 对应容器命令；不得用 Java 8 验证 Spring Boot 4。

## CI/CD 分工

- `backend-ci.yml`（工作流名 `CI`）：PR 和 `main` 的统一质量门；secret 扫描、Java 单元测试、
  HTTP/PostgreSQL 集成测试、前端测试/构建和浏览器 E2E 使用五个并行 job，保持独立失败状态。
- `backend-deploy.yml`（工作流名 `Backend CD`）：只在 `main` 的 `CI` 成功后自动运行；先发布或复用不可变
  commit SHA 镜像，再经 `staging` Environment 执行迁移、替换、健康检查和失败回滚。
- `deploy.yml`（工作流名 `Pages CD`）：只在 `main` 的 `CI` 成功后自动构建同一 commit 并发布 GitHub Pages。

集中 CI 是编排入口，不是单个串行 job。测试与发布必须分离工作流，因为发布需要 GHCR、Pages、OIDC、SSH secret
和 Environment 等高权限，而 PR 检查只保留 `contents: read`。开发分支只通过 `pull_request` 触发 CI，不同时监听
`codex/**` push，避免同一提交重复运行两套检查。手动触发 CD 仅用于运维恢复，不属于自动发布路径。

## 完成定义

- HTTP interface 与 OpenAPI 一致，且没有浏览器直连已迁移业务表。
- 业务写入在单个后端 transaction 中完成，重试和并发语义有测试。
- 资源所有权和管理员角色由服务端会话校验。
- migration 能从空 PostgreSQL 运行，也能通过 reconciliation 对齐现有 Supabase 数据库。
- 单元、集成、HTTP contract、前端和 E2E 测试均通过；CI 使用与本地相同的命令。
- 部署产物以 commit SHA 标记，数据库迁移先于应用替换，失败时保留可执行回滚路径。
