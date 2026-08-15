# Tutor Base 后端

Tutor Base 的可信后端，采用 Java 21、Spring Boot 4.1 和模块化单体架构。

## 环境要求

- Java 21，不使用预览特性
- Docker，用于 PostgreSQL 集成测试和容器镜像构建

项目使用 Maven Wrapper 固定 Maven 版本，因此不要求系统单独安装 Maven。

## 运行与验证

在 Linux 或 macOS 中执行：

```shell
./mvnw clean verify
./mvnw spring-boot:run
```

在 Windows PowerShell 中执行：

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

公开的运行状态接口包括：

- `GET http://localhost:8080/api/v1/system/status`
- `GET http://localhost:8080/actuator/health/liveness`
- `GET http://localhost:8080/actuator/health/readiness`

管理员学员查询接口为：

- `GET http://localhost:8080/api/v1/admin/learners?limit=50`

该接口按学员 ID 稳定排序，通过不透明的 `nextCursor` 获取下一页，默认每页 50 条、最大 100 条，并要求
当前账户具有 `ADMINISTRATOR` 角色。项目不提供绕过生产认证的临时开关。

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
- `POST /api/v1/account-activations/complete`：使用一次性激活码设置初始密码。
- `POST /api/v1/admin/account-activations`：管理员为待激活账户发行新激活码。

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

## 数据库

PostgreSQL 结构只通过 Flyway 管理：

- `V1__application_baseline.sql`：建立四张已经确认的原有业务表。
- `V2__strengthen_data_invariants.sql`：增加生产数据审计已经证明安全的约束，并为参考答案、作答和错题表安装统一的
  `updated_at` 触发器。
- `V3__add_identity_and_sessions.sql`：增加账户、一次性激活码和服务端会话，并把原有 `student` 回填为待激活账户；
  原有 `young` 账户只在数据库回填时获得管理员角色，运行时代码不再按用户名授予权限。

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
