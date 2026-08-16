# Tutor Base 后端

Tutor Base 的可信后端，采用 Java 21、Spring Boot 4.1 和模块化单体架构。

## 环境要求

- Java 21，不使用预览特性
- Docker，用于 PostgreSQL 集成测试和容器镜像构建

项目使用 Maven Wrapper 固定 Maven 版本，因此不要求系统单独安装 Maven。

## 当前集成状态

Java 后端已经可以独立启动和验证，Vue 身份切片也已经改用 Java 的密码登录、CSRF 和服务端会话。学习、作答、批阅和
错题切片目前仍直接调用 Supabase，因此这是可验证的新身份开发环境，但还不是全部业务都由 Java 托管的最终状态。

### 开发阶段与部署门槛

当前已完成后端工程骨架、模块化 MVC、数据库 V1 至 V3、身份激活与登录、服务端会话、CSRF、管理员鉴权、管理员学员
查询、OpenAPI、容器镜像和 CI。现有 Supabase schema 已在 2026-08-15 完成可恢复备份验证、结构对齐和 Flyway V3
baseline；首位管理员也已完成一次性自举和真实登录/登出验证。Vue 身份接口切换已完成，尚未实现其余学习、作答、
批阅和错题接口。

可以进入云服务器 staging 部署的门槛是：

1. 修复真实数据库与 V1/V2 的结构差异，并让接管检查全部为 0。
2. 完成可恢复备份验证和 Flyway V3 baseline。
3. 一次性激活管理员账户。
4. 使用 staging API 域名启动后端，通过 readiness、登录、CSRF、授权和会话 smoke test。

正式生产切换还需要：Vue 改用 Java identity gateway，移除特殊用户名管理员和本地伪会话；完成所需业务 API；在 staging
跑通关键用户旅程；部署 `api.be-young.top` HTTPS/CORS；最后再撤销浏览器对 Supabase 业务表的直接写权限。可以先部署
后端 staging，不应等全部前端改造完成才第一次部署。当前数据库接管门禁、管理员自举和受限运行角色均已完成；
运行角色设置独立密码后即可部署 API-only staging 并执行 smoke test。

## 首次完整启动

### 1. 准备环境

需要安装：

- Java 21；可以用 `java -version` 检查。
- Node.js 20 或更新的 LTS 版本；可以用 `node --version` 检查。
- Docker Desktop；本地 PostgreSQL 和集成测试都会使用它。

以下命令均从仓库根目录执行：

```powershell
cd D:\newdir\cc\tutor-base
```

### 2. 启动一次性本地 PostgreSQL

首次创建开发数据库容器：

```powershell
docker run --name tutor-base-postgres `
  -e POSTGRES_DB=tutor_base_dev `
  -e POSTGRES_USER=tutor_base_dev `
  -e POSTGRES_PASSWORD=local-dev-password `
  -p 5432:5432 `
  -d postgres:17-alpine
```

以后容器停止时只需执行：

```powershell
docker start tutor-base-postgres
```

这个数据库只用于本地开发。不要在本地验证阶段把 `DATABASE_URL` 指向 Supabase 生产库。

### 3. 启动 Java 后端

项目提供不含真实密钥的 `.env.example`。首次运行先复制成本地 `.env`：

```powershell
cd D:\newdir\cc\tutor-base\backend
Copy-Item .env.example .env
```

Spring Boot 会从当前 `backend` 工作目录自动读取 `.env`。该文件已经被 Git 忽略，可以按本机情况修改。然后执行：

```powershell
.\mvnw.cmd spring-boot:run
```

首次启动会由 Flyway 自动执行 V1、V2、V3；以后启动只校验历史，不会重复执行已经成功的 migration。出现
`Started TutorBaseApplication` 后，后端地址为 `http://localhost:8080`。

系统环境变量的优先级高于 `.env`，因此仍可以在 CI、Docker 或云服务器中直接注入变量。生产环境不要复制本地示例值；
推荐通过 systemd `EnvironmentFile`、容器 secret 或云服务密钥管理配置，并限制配置文件读取权限。

### 4. 验证后端

再打开一个 PowerShell 窗口：

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/system/status
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

系统状态应包含 `status = ok`，两个健康检查都应返回 `status = UP`。

验证匿名 CSRF Cookie 和 token：

```powershell
$csrf = Invoke-RestMethod `
  -Uri http://localhost:8080/api/v1/csrf `
  -SessionVariable browser

$csrf
$browser.Cookies.GetCookies('http://localhost:8080')
```

响应应包含 `token` 和 `headerName = X-CSRF-TOKEN`，Cookie 应为 `TUTOR_SESSION` 且带有 `HttpOnly`。匿名 CSRF
不会在 `account_session` 表中创建记录。完整的登录、激活和管理员授权流程建议先运行自动化契约测试，因为全新的本地库
没有可直接登录的账户。

### 5. 启动 Vue 前端

在仓库根目录的另一个 PowerShell 窗口执行：

```powershell
cd D:\newdir\cc\tutor-base
npm install
```

在前端目录创建不提交 Git 的 `tutor-nest-vue/.env.local`（Vite 的环境文件根目录是
`tutor-nest-vue`，不是仓库根目录）：

```text
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_SUPABASE_URL=<你的 Supabase Project URL>
VITE_SUPABASE_ANON_KEY=<你的 Supabase anon key>
```

然后启动：

```powershell
npm run dev
```

浏览器访问 `http://localhost:3000`。身份登录连接本机 Java API，其余业务在迁移期仍连接 Supabase；不要在浏览器里
放入数据库密码、service role key 或后端 `TUTOR_CSRF_SECRET`。

### 6. 启动完成检查清单

- `http://localhost:3000` 可以打开 Vue 页面。
- `http://localhost:8080/api/v1/system/status` 返回 `status = ok`。
- liveness 和 readiness 都返回 `UP`。
- `GET /api/v1/csrf` 返回 token 和 HttpOnly Cookie。
- 密码登录、刷新恢复会话和退出通过 Java API；学习、作答、批阅和错题仍通过 Supabase，这是当前阶段的预期状态。

停止开发环境时，在前端和后端窗口分别按 `Ctrl+C`。本地数据库容器可以保留；不使用时执行：

```powershell
docker stop tutor-base-postgres
```

## 自动化验证

在 Linux 或 macOS 中执行：

```shell
./mvnw clean verify
./mvnw spring-boot:run
```

在 Windows PowerShell 中执行：

```powershell
.\mvnw.cmd clean verify
```

完整验证会启动临时 PostgreSQL 17 容器，执行 V1 至 V3，并再次调用 Flyway，确认第二次 migrate 执行数为 0。

前端构建验证：

```powershell
cd D:\newdir\cc\tutor-base
npm run build
```

公开的运行状态接口包括：

- `GET http://localhost:8080/api/v1/system/status`
- `GET http://localhost:8080/actuator/health/liveness`
- `GET http://localhost:8080/actuator/health/readiness`

管理员学员查询接口为：

- `GET http://localhost:8080/api/v1/admin/learners?limit=50`
- `PUT http://localhost:8080/api/v1/admin/learners/{learnerId}/password`

查询接口按学员 ID 稳定排序，通过不透明的 `nextCursor` 获取下一页，默认每页 50 条、最大 100 条。密码接口接收
`{"password":"..."}`，密码长度必须为 12～128 个字符。首次设置会把待激活学员转为可登录状态；再次设置会重置密码，
同时撤销该学员已有的全部会话和未使用激活码。两个接口都要求当前账户具有 `ADMINISTRATOR` 角色，写请求还必须携带
与会话 Cookie 绑定的 `X-CSRF-TOKEN`。项目不提供绕过生产认证的临时开关。

只验证学员查询的完整 HTTP 契约：

```shell
./mvnw -Dtest=AdminLearnerHttpContractTest test
```

PowerShell 需要为测试选择器加引号：

```powershell
.\mvnw.cmd '-Dtest=AdminLearnerHttpContractTest' test
```

测试会启动临时 PostgreSQL 17 容器、执行全部 Flyway migration，并覆盖数据库映射、分页、参数校验和授权规则。

## 账户、会话与 CSRF

后端已经实现以下身份接口：

- `GET /api/v1/csrf`：签发无状态匿名 CSRF token；匿名访问不会写入会话表。
- `POST /api/v1/sessions`：用户名和密码登录，并轮换会话 ID。
- `GET /api/v1/session`：查询当前账户的安全投影。
- `DELETE /api/v1/session`：撤销当前服务端会话并清除 Cookie。
- `PUT /api/v1/admin/learners/{learnerId}/password`：管理员直接设置或重置学生密码，并撤销该学生的旧会话。
- `POST /api/v1/account-activations/complete`：使用一次性激活码设置初始密码。
- `POST /api/v1/admin/account-activations`：管理员为待激活账户发行新激活码。

当前前端采用管理员直接设置密码的流程，不生成激活码。激活码接口暂时保留为兼容能力，但不作为当前管理操作入口。
管理员不能通过学生密码接口修改独立管理员、已禁用账户或没有学习者身份的账户。明文密码不会写入日志、响应或浏览器存储。

浏览器必须先请求 `/api/v1/csrf`，随后为所有 `POST`、`PUT`、`PATCH` 和 `DELETE` 请求发送
`X-CSRF-TOKEN`。登录和账户激活同样需要 CSRF token。密码使用 Argon2id 保存；会话和激活码只在数据库中保存
SHA-256 摘要。管理员发行激活码时，明文只在创建响应中出现一次。

登录和账户激活默认按来源地址分别限制为每分钟 10 次，可通过 `TUTOR_PUBLIC_MUTATION_LIMIT` 和
`TUTOR_PUBLIC_MUTATION_WINDOW` 调整。该内存限流适用于当前单实例部署；扩展为多实例时还必须在统一网关增加共享限流。
已撤销或过期的已认证会话每小时自动从数据库清理。

本地 HTTP 开发使用 `TUTOR_SESSION` Cookie 且默认关闭 `Secure`；生产 profile 强制使用
`__Host-TUTOR_SESSION; HttpOnly; Secure; Path=/; SameSite=Lax`。生产环境必须设置随机高强度的：

```text
TUTOR_CSRF_SECRET=<至少 32 字节的随机值>
```

只验证认证、CSRF、激活和服务端会话契约：

```powershell
.\mvnw.cmd '-Dtest=IdentityHttpContractTest' test
```

首次管理员不能通过公共 HTTP 接口自举。请按照 `../docs/bootstrap-administrator.md` 在受控环境执行一次性离线流程；
成功后立即清除相关环境变量，正常运行服务时不得启用 `bootstrap-admin` profile。

当前 Supabase 生产库已于 2026-08-15 完成 `young` 管理员自举，并通过登录、管理员角色、登出和会话失效验证。
不得在当前生产库再次运行该流程；`.env` 中的 `TUTOR_BOOTSTRAP_PASSWORD` 应保持为空，
`TUTOR_BOOTSTRAP_ENABLED` 必须保持为 `false`。

本地或 staging 启动后，可以在 `../deploy` 目录运行交互式完整验证：

```powershell
.\api-smoke-test.ps1 -BaseUrl http://127.0.0.1:8080
```

脚本验证健康检查、CSRF、管理员登录、会话角色、管理员学员查询、登出和会话失效，管理员密码不会显示或写入命令历史。

## 数据库

PostgreSQL 结构只通过 Flyway 管理：

- `V1__application_baseline.sql`：建立四张已经确认的原有业务表。
- `V2__strengthen_data_invariants.sql`：增加生产数据审计已经证明安全的约束，并为参考答案、作答和错题表安装统一的
  `updated_at` 触发器。
- `V3__add_identity_and_sessions.sql`：增加账户、一次性激活码和服务端会话，并把原有 `student` 回填为待激活账户；
  如果当时存在 `young` 学员账户，它会在回填时获得管理员角色。当前生产数据当时没有该用户名，因此一次性自举流程
  创建了不绑定 `student` 的独立管理员账户。正常运行时代码不会按用户名授予权限。

### Supabase 出现 `relation "account" already exists`

不要再次执行 V3，也不要删除 `account`、`account_activation`、`account_session` 或
`flyway_schema_history`。Flyway migration 是版本化历史，不应通过 `IF NOT EXISTS` 掩盖部分执行状态。

该错误只说明 `public.account` 已存在。请在 Supabase SQL Editor 中运行只读文件：

```text
../database/audit/post_v3_identity_checks.sql
```

保存最后返回的 `post_v3_report` JSON。所有 `anomaly_count` 应为 `0`，并检查：

- `student_rows` 与 `account_rows` 是否一致；当前已知生产盘点应都是 `2`。
- `flyway_history_exists` 当前是否符合预期。
- 三张身份表、字段、约束和索引是否全部存在。

当前 Supabase 报告已经确认 V3 完整，但 `flyway_history_exists = false`。因此不要直接启动生产后端连接该库。继续运行：

```text
../database/audit/flyway_adoption_checks.sql
```

该报告核验 V1/V2 的字段类型、空值约束、主外键、组合唯一键、索引、级联删除、ID 生成器和更新时间触发器。全部为零后，
按照 `../docs/flyway-baseline-adoption.md` 进行备份验证和一次性 baseline 接管。不要手工向历史表插入记录。

实际明细已确认部分 V2 对象确实缺失，另有约束和索引只是名称不同。仓库提供经过生产漂移模拟测试的 reconciliation：

```text
../database/reconciliation/2026-08-15_align_existing_schema_through_v3.sql
```

它是生产写操作，只能在完成可恢复备份并进入维护窗口后执行；不得把“脚本已通过本地测试”当作跳过备份的理由。

题目标识继续使用 `text`，调用方不得假设它一定是数字。`clean verify` 会通过 Testcontainers 在 PostgreSQL 17
上从空数据库执行全部 migration。开发环境缺少 Docker 时，独立 migration 测试可以跳过；GitHub Actions CI
必须具有 Docker 并完整执行测试。

本地开发默认连接：

```text
jdbc:postgresql://localhost:5432/tutor_base_dev
```

可以通过环境变量覆盖：

```text
DATABASE_URL=jdbc:postgresql://localhost:5432/tutor_base_dev
DATABASE_USERNAME=tutor_base_dev
DATABASE_PASSWORD=<本地数据库密码>
```

生产环境必须启用 `production` Spring profile，并显式设置以上三个变量。不要把 Supabase `anon` 或
`service_role` 密钥当作 PostgreSQL 登录凭据，也不要把数据库端口直接暴露到公网。

## GitHub Pages 前端

生产前端继续部署在 GitHub Pages，推荐使用同一主域名下的两个子域名：

- `https://learn.be-young.top`：GitHub Pages 前端
- `https://api.be-young.top`：Java 后端

带凭据的 CORS 默认拒绝所有来源。通过逗号分隔的环境变量配置精确可信来源：

```text
TUTOR_WEB_ALLOWED_ORIGINS=https://learn.be-young.top
```

不要使用 `*`。浏览器请求 API 时必须设置 `credentials: 'include'`。不建议让默认 `*.github.io` 域名直接使用
独立 API 域名的 Cookie 会话，因为两者属于 cross-site，可能受到第三方 Cookie 策略限制。

## 容器镜像

在 `backend` 目录执行：

```shell
docker build -t tutor-base-backend:dev .
docker run --rm -p 8080:8080 tutor-base-backend:dev
```

机器可读的接口契约位于 `api/openapi.yaml`。新增或修改接口时，必须同步更新契约和 MockMvc 测试。
