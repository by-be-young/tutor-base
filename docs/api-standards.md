# HTTP 接口规范

`backend/api/openapi.yaml` 是机器可校验的接口 contract，本文定义跨 endpoint 的稳定约定和目标接口目录。OpenAPI 只描述已经实现或正在同一变更中实现的 endpoint；规划中的接口在本文维护，避免客户端误以为已经可用。

## 1. 基础约定

- Production frontend：`https://learn.be-young.top`（GitHub Pages custom domain）。
- Production API base URL：`https://api.be-young.top/api/v1`，由 `VITE_API_BASE_URL` 注入。
- API 只允许配置中的精确 origin，并返回 `Access-Control-Allow-Credentials: true`；禁止 `*`。
- Content-Type：请求和响应使用 `application/json`；Problem Details 使用 `application/problem+json`。
- JSON 字段使用 `camelCase`；枚举 wire value 使用小写 `snake_case`。
- 时间使用 RFC 3339 UTC，例如 `2026-08-14T08:30:00Z`。
- `articleId`、`accountId` 使用 JSON integer，并保证不超过 JavaScript safe integer；`questionKey` 始终是 string。
- 新资源优先使用 UUID string；不得把数据库自增策略写进接口语义。
- 客户端忽略未知 response 字段；服务端拒绝未知 request 字段，尽早发现拼写错误。
- 路径名使用复数名词和 kebab-case；action 仅用于无法用资源状态表达的任务。

## 2. 认证、session 与 CSRF

- 服务端通过 API host-only 的 `__Host-TUTOR_SESSION` opaque cookie 识别 session；cookie 使用
  `HttpOnly; Secure; Path=/; SameSite=Lax`，不设置 `Domain`，前端不得读取或保存该值。
- 登录成功后服务端轮换 session ID，并返回当前 Account 的安全投影。
- 前端跨 origin 请求统一使用 `credentials: 'include'`。
- 前端先从精确白名单保护的 `GET /csrf` 取得 token 并只保存在内存；`POST/PUT/PATCH/DELETE`
  通过 `X-CSRF-TOKEN` header 回传。CSRF token 不写入 localStorage，也不向其他 origin 暴露。
- 未认证返回 `401 unauthenticated`；已认证但无权访问返回 `403 forbidden`，不得用 404 掩盖所有授权错误。
- 登录错误统一返回 `invalid_credentials`，不暴露用户名是否存在。

## 3. 成功响应

- 单资源直接返回资源对象，不套无意义的 `{ data: ... }`。
- collection 返回：

```json
{
  "items": [],
  "nextCursor": null
}
```

- 创建资源返回 `201` 和 `Location`；幂等更新返回 `200` 及更新后表示，无 body 的删除返回 `204`。
- `PUT` 表示调用方提供该任务的完整目标状态，重复调用结果相同；部分修改使用 `PATCH`。

## 4. Problem Details

所有非 2xx 响应采用下列结构：

```json
{
  "type": "https://api.be-young.top/problems/validation-failed",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/sessions",
  "code": "validation_failed",
  "traceId": "01J...",
  "fieldErrors": [
    { "field": "username", "code": "required" }
  ]
}
```

稳定错误码首批定义：

| HTTP | `code` | 语义 |
| --- | --- | --- |
| 400 | `malformed_request` | JSON、参数或路径格式不可解析 |
| 400 | `validation_failed` | 字段可解析但违反输入约束 |
| 401 | `unauthenticated` | 没有有效 session |
| 401 | `invalid_credentials` | 登录凭据无效，用户名是否存在不公开 |
| 401 | `invalid_activation_token` | 激活码无效、过期、已使用或账户已不再待激活 |
| 403 | `forbidden` | 已认证但角色/内容授权不足 |
| 403 | `csrf_invalid` | CSRF token 缺失或无效；前端可重新获取一次后重试 |
| 429 | `rate_limited` | 公开认证操作超过来源地址短时频率限制；按 `Retry-After` 重试 |
| 404 | `resource_not_found` | 在授权范围内资源不存在 |
| 409 | `state_conflict` | 当前状态不允许该操作 |
| 409 | `username_taken` | 激活/创建账户时用户名冲突 |
| 412 | `version_conflict` | `If-Match` 与当前资源版本不符 |
| 429 | `rate_limited` | 请求频率超过限制 |
| 500 | `internal_error` | 未分类服务端错误，不返回内部异常 |
| 503 | `dependency_unavailable` | 必要依赖暂不可用 |

## 5. 并发、幂等和分页

- 当前作答使用 `PUT`，同一 request 重试不会增加错题次数；只有从“非错误”转换为新的错误结果时才产生一次收集事件。
- 需要防止覆盖的管理更新返回 `ETag`，客户端通过 `If-Match` 更新；版本不符返回 `412 version_conflict`。
- 创建型非幂等 endpoint 若允许网络重试，接受 `Idempotency-Key` UUID header，并在 24 小时窗口返回首次结果。
- collection 使用 opaque cursor，不使用可被修改数据破坏的 page number。`limit` 默认 50、最大 100。
- 排序必须由 endpoint 固定或显式白名单参数控制，禁止把任意 SQL 字段作为 sort key。

## 6. 目标接口目录

### System

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/system/status` | public | 返回应用版本和服务状态；不泄露配置或数据库信息 |
| GET | `/actuator/health/liveness` | infrastructure | 进程存活检查 |
| GET | `/actuator/health/readiness` | infrastructure | 接流量前的必要依赖检查 |

### Identity

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/csrf` | public | 为受信任的 GitHub Pages origin 返回当前 CSRF token |
| POST | `/account-activations/complete` | activation token | 设置初始密码并激活现有账户 |
| POST | `/sessions` | public | 用户名/密码登录并创建 session |
| GET | `/session` | account | 返回当前 Account 投影，并刷新 CSRF cookie |
| DELETE | `/session` | account | 注销当前 session |

### Learning Access and Catalog

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/articles` | account | 管理员返回全部，学习者只返回已授权学习文章 |
| GET | `/articles/{articleId}/study-state` | content grant | 返回参考答案可见性规则下的作答状态 |
| PUT | `/admin/learners/{learnerId}/content-grants` | administrator | 原子替换某学习者的内容授权集合 |

### Learning and Review

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| PUT | `/articles/{articleId}/submissions/{questionKey}` | content grant | 保存当前账户的当前作答并执行自动批阅 |
| PUT | `/admin/articles/{articleId}/answer-keys/{questionKey}` | administrator | 创建或替换参考答案与自动批阅规则 |
| PUT | `/admin/submissions/{submissionId}/review` | administrator | 保存人工批阅结果 |

### Wrong Book

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/wrong-book` | learner | 查询当前学习者的未移除错题条目 |
| POST | `/wrong-book/entries` | learner | 手动收集一道题；支持 Idempotency-Key |
| PATCH | `/wrong-book/entries/{entryId}` | owner | 修改错因、笔记或掌握状态 |
| DELETE | `/wrong-book/entries/{entryId}` | owner | 按来源语义软删除或硬删除条目 |

### Administration

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/admin/learners` | administrator | 按学习者 ID 游标查询学习者及内容授权；账户状态在身份迁移后增加 |
| POST | `/admin/account-activations` | administrator | 为待激活账户生成一次性激活码 |

## 7. System Status contract（第一阶段）

第一阶段只实现 `/system/status` 和 Actuator health：

```json
{
  "status": "ok",
  "service": "tutor-base-backend",
  "version": "0.1.0-SNAPSHOT",
  "time": "2026-08-14T08:30:00Z"
}
```

- `status` 第一阶段只有 `ok`；依赖不可用由 readiness 和 HTTP 状态表达，不在这里堆叠子系统详情。
- `version` 来自 build metadata，未知时返回 `dev`，不能硬编码生产 commit。
- `time` 由注入的 `Clock` 产生，便于 interface test 固定时间。
- response 不返回 profile、hostname、JVM 参数、数据库 URL、用户名、environment variable 或 secret。

## 8. Contract 变更规则

- 增加 optional response 字段是兼容变更；删除/重命名字段、收紧 enum、改变状态码或认证要求是 breaking change。
- request 新字段默认 optional；要变为 required，先让客户端发布并发送，再在后续版本收紧。
- breaking change 优先在 `/api/v2` 引入；不允许仅靠前后端“同时发布”掩盖不兼容。
- OpenAPI 示例必须通过 schema 校验；实现的 MockMvc contract test 至少覆盖成功、未认证、无权、校验失败和 Problem Details。
