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
- 凡服务端轮换 session（登录、注册、管理员进入学生账号）都会 Set-Cookie 新会话，
  旧 CSRF token（与旧 Cookie 绑定）随之失效；前端收到轮换响应后必须丢弃内存 CSRF token。
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
  "title": "请求参数校验失败",
  "status": 400,
  "detail": "请求中的部分字段不合法。",
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
| 403 | `learner_context_required` | 当前账户未关联学习者身份（无 `learnerId`） |
| 403 | `csrf_invalid` | CSRF token 缺失或无效；前端可重新获取一次后重试 |
| 429 | `rate_limited` | 公开认证操作超过来源地址短时频率限制；按 `Retry-After` 重试 |
| 404 | `resource_not_found` | 在授权范围内资源不存在 |
| 409 | `state_conflict` | 当前状态不允许该操作 |
| 409 | `username_conflict` | 注册或创建账户时用户名已被占用（不公开具体用户名） |
| 409 | `learner_already_claimed` | 学习者已被其他管理员认领；认领或删除前需由原认领管理员先取消认领 |
| 403 | `learner_not_claimed` | 当前管理员不是该学习者的认领人；查看详情、设置密码、进入账号、删除均拒绝 |
| 409 | `special_test_learner_protected` | 目标学习者是特殊测试学员，禁止删除 |
| 409 | `administrator_learner_protected` | 目标学习者关联管理员账户，不能通过学员管理删除 |
| 403 | `courseware_not_claimed` | 当前管理员未认领该课件；业务写操作与敏感读取被拒绝 |
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
| POST | `/accounts` | public | 注册学习者账户并直接建立 session；新账户无任何内容授权 |
| POST | `/account-activations/complete` | activation token | 设置初始密码并激活现有账户 |
| POST | `/sessions` | public | 用户名/密码登录并创建 session |
| GET | `/session` | account | 返回当前 Account 投影，并刷新 CSRF cookie |
| DELETE | `/session` | account | 注销当前 session |
| PUT | `/password` | account | 校验当前密码后修改密码；撤销除当前会话外的所有会话 |

所有密码（注册、激活、改密、管理员重置）长度约束统一为 6～128 个字符。

### Learning Access and Catalog

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/articles` | account | 管理员返回全部，学习者只返回已授权学习文章 |
| GET | `/articles/{articleId}/study-state` | content grant | 返回参考答案可见性规则下的作答状态 |
| GET | `/articles/study-progress` | learner | 返回当前学习者已授权文章的作答进度 |
| PUT | `/admin/learners/{learnerId}/content-grants` | administrator | 在“我的课件”范围内原子替换科目归属（`enrolledSubjects`）与文章授权（`articleIds`）；范围外既有授权保留；省略 `enrolledSubjects` 时保留旧科目 |

### Learning and Review

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| PUT | `/articles/{articleId}/submissions/{questionKey}` | content grant | 保存当前账户的当前作答并执行自动批阅 |
| GET | `/admin/articles/answer-key-article-ids` | administrator | 返回“我的课件”中已设置参考答案的文章 ID |
| GET | `/admin/articles/{articleId}/answer-keys` | administrator | 查询一篇文章的参考答案与自动批阅规则；非我的课件返回 403 |
| PUT | `/admin/articles/{articleId}/answer-keys` | administrator | 创建或替换该文章的参考答案集合；非我的课件拒绝写 |
| GET | `/admin/articles/{articleId}/study-state?learnerId=` | administrator | 查询指定学习者的文章作答与答案；非我的课件返回 403 |
| GET | `/admin/learners/{learnerId}/study-progress` | administrator | 查询指定学习者在“我的课件”内的作答进度，且只含该学员已授权文章 |
| PUT | `/admin/articles/{articleId}/learners/{learnerId}/submissions/{questionKey}/review` | administrator | 保存人工批阅结果；非我的课件返回 403 |

### Wrong Book

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/wrong-book` | learner | 查询当前学习者的未移除错题条目 |
| POST | `/wrong-book/entries` | learner | 手动收集一道题；已有未移除条目返回冲突 |
| PATCH | `/wrong-book/entries/{entryId}` | owner | 修改错因、笔记或掌握状态 |
| DELETE | `/wrong-book/entries/{entryId}` | owner | 按来源语义软删除或硬删除条目 |

### Check-in

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/checkins?year=&month=` | learner | 返回指定自然月（Asia/Shanghai）的签到日期、本月次数、今日状态与奖励规则 |
| POST | `/checkins` | learner | 执行当日签到；同日重复调用幂等返回 `alreadyCheckedIn`，不重复加分；第 7、14 次签到额外 +100 积分 |

- 「今日」以服务端 Asia/Shanghai 时区判定；月度统计按自然月（每月 1 日重置）。
- 签到记录与积分由服务端写入 `public.daily_check_in` / `public.user_points`，客户端不得直接写表。
- 当前账户无 `learnerId`（如未关联学习者的管理员）调用返回 `403 learner_context_required`。

### Rewards

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/rewards` | learner | 返回当前学习者的可用积分、累计积分、卡片收藏与收藏统计（抽卡次数/总数/不重复数/卡池规模） |
| GET | `/rewards/catalog` | learner | 返回服务端卡池目录：抽卡价格、稀有概率、UP 概率、当期 UP 卡（下周一 00:00 Asia/Shanghai 轮换）与全部启用卡组 |
| POST | `/rewards/draws` | learner | 抽取 `count` 张卡（1–10）；每张 80 积分，服务端决定稀有度、UP 与卡片，写入收藏并累加抽卡次数 |

- 卡组、卡片、概率与 UP 轮换来自数据库表 `card_sets` / `card_definitions`，新增卡组只需插入数据行，无写死数量。
- UP 卡组按启用卡组的 `sort_order, set_key` 顺序，以 ISO 周数取模确定，确定性轮换；抽到稀有卡时 50% 为当期 UP。
- 客户端只能提交 `count`，不得发送或决定 `studentId`、`cardKey`、`setKey`、`rarity` 或 `isUp`。
- 抽卡只扣 `points`，累计积分 `lifetime_points` 只增不减并用于等级计算；`draw_count` 记录累计抽卡次数。
- 积分不足返回 `409 insufficient_points`；抽卡、收藏写入与计数更新在同一数据库事务内完成。

### Growth

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/achievements` | learner | 返回分级成就、当前指标、等级状态与可领取等级 |
| POST | `/achievements/{key}/claims` | learner | 领取下一级已达成成就；由服务端决定奖励积分并原子累加 |
| GET | `/tasks/board` | learner | 返回每日、每周、限时任务及刷新时间、进度、状态 |
| POST | `/tasks/{key}/accept` | learner | 接取限时任务；服务端写入接取时间与宽松截止时间 |
| POST | `/tasks/{key}/claims` | learner | 领取已完成任务奖励；同一周期/同一次接取只能领取一次 |

- 学习者身份只从服务端会话取得；客户端不得提交 `studentId`、奖励积分、截止时间或完成状态。
- 卡片类成就按两种指标区分：“收藏家”按不重复卡片数（`COUNT(DISTINCT card_key)`）并以当前卡池总量百分比缩放档位；“欧气收集家”按累计获得卡片数（含重复）。
- 任务与成就的完成条件、进度窗口和奖励全部由服务端目录与 SQL 推导；周期任务按 Asia/Shanghai 自然日/自然周刷新。
- 限时任务的接取与截止时间由服务端生成，期限刻意覆盖工作日；过期后返回 `409 task_expired`，可重新接取。
- 领取记录使用数据库唯一约束兜底，重复领取返回 `409 achievement_already_claimed` / `409 task_already_claimed`。
- 领取记录写入与积分累加在同一数据库事务内完成。

### Administration

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| GET | `/admin/learners` | administrator | 按学习者 ID 游标查询学习者及内容授权；账户状态在身份迁移后增加 |
| POST | `/admin/learners` | administrator | 新增测试学员（固定 `isTest=true`）并创建待设置密码账户；由创建它的管理员自动认领 |
| PUT | `/admin/learners/{learnerId}/claim` | administrator | 认领学习者；重复认领幂等，已被其他管理员认领返回 `409 learner_already_claimed` |
| DELETE | `/admin/learners/{learnerId}/claim` | administrator | 取消认领；未认领时幂等返回 `204` |
| DELETE | `/admin/learners/{learnerId}` | administrator | 删除学习者及其账户、会话和学习数据；未认领/他人认领返回 `403 learner_not_claimed` 或 `409 learner_already_claimed`，特殊测试学员 `409 special_test_learner_protected`，关联管理员账户 `409 administrator_learner_protected` |
| PUT | `/admin/learners/{learnerId}/password` | administrator | 直接设置或重置自己已认领的学习者密码，并撤销其旧会话和未使用激活码；未认领 `403 learner_not_claimed` |
| POST | `/admin/learners/{learnerId}/impersonate` | administrator | 免密进入自己已认领的学习者账户：创建新会话并轮换 Cookie；仅限 `role=learner` 且已激活的账户，未认领 `403 learner_not_claimed`，不撤销管理员既有会话 |
| POST | `/admin/account-activations` | administrator | 兼容接口：为待激活账户生成一次性激活码；当前前端不使用 |
| GET | `/admin/analytics/overview` | administrator | 管理台概览指标；待批阅/提交数只统计“我的课件” |
| GET | `/admin/analytics/activity?days=` | administrator | 近 7～90 天每日提交/批阅趋势，按 Asia/Shanghai 自然日补零，只统计“我的课件” |
| GET | `/admin/learners/{learnerId}/profile` | administrator | 学员详情：积分/累计/等级、近 30 天签到、分学科进度、最近作答、30 天动态；仅限已认领学员，未认领 `403 learner_not_claimed`，学习数据只含“我的课件” |

- 认领是管理员之间的组织归属，不改变科目归属、内容授权、批阅权限或学习者可见范围；管理员目录排除 `role=administrator` 的账户。
- 学习者响应额外返回 `enrolledSubjects`、`claimedByAccountId`、`claimedByUsername`、`claimedAt`、`lastActivityAt`、`isTest` 与 `isSpecialTest`；最近动态由服务端聚合作答提交、错题、签到和登录会话时间，客户端不得提交这些值。
- `enrolledSubjects` 是科目归属，`contentGrantArticleIds` 是文章授权；开通科目初始不授权任何文章，二者互不推导。
- 测试学员保留正常登录与学习能力，但 analytics 的学员计数、活跃度、提交/批阅趋势排除测试学员；管理端切换器单独分组展示。
- 特殊测试学员全局最多一个，拥有全部课件访问权限，`/rewards` 返回固定 999999999 积分且抽卡不减少；`/me/content-grants` 返回课件目录全量文章并带 `allArticles=true`，前端据此按静态目录全量放行。
- 删除学习者会级联删除账户会话、激活码、作答、错题、签到、积分、卡片和任务/成就记录；必须由认领人操作，特殊测试学员与管理员账户关联的学习者不可通过该接口删除。
- 管理员查询学习者作答进度只返回该学习者当前已授权文章；授权被收回的文章不再参与待批阅与进度统计。

### Courseware Claims

| Method | Path | Auth | 语义 |
| --- | --- | --- | --- |
| POST | `/admin/courseware-catalog/sync` | administrator | 由前端同步静态课件目录（articleId → 学科/标题），幂等 upsert |
| GET | `/admin/courseware-claims` | administrator | 返回当前管理员显式认领的学科、单篇课件与排除的课件 ID |
| PUT | `/admin/courseware-claims/subjects/{subject}` | administrator | 认领学科；自动覆盖该学科当前及未来新增课件，并清理该科排除，幂等 |
| DELETE | `/admin/courseware-claims/subjects/{subject}` | administrator | 释放学科认领；仅靠学科覆盖的课件回到待认领，单篇认领保留，并清理该科排除 |
| PUT | `/admin/courseware-claims/articles/{articleId}` | administrator | 单篇认领/恢复：清除排除；学科未覆盖时物化单篇认领，幂等 |
| DELETE | `/admin/courseware-claims/articles/{articleId}` | administrator | 释放单篇：删除单篇认领；仍被学科覆盖时写入排除，幂等 |
| GET | `/admin/courseware-claims/catalog?scope=mine\|unclaimed\|all` | administrator | 课件认领目录，含学科汇总、认领归属与当前管理员的 `excluded` 标记 |

- “我的课件” = (我认领学科下的全部课件 − 我排除的课件) ∪ 我单篇认领的课件；学科认领覆盖未来新增课件，因此不物化每篇归属行。
- 学科与管理员是多对多：同一学科可被多名管理员认领；单篇课件也可被多名管理员认领。
- 未认领/被自己排除的课件在业务页隐藏，但 `catalog?scope=unclaimed` 仍返回其内容元信息（含 `excluded=true`）；读取正文仍走公开静态内容。
- 未认领课件在业务页隐藏，但 `catalog?scope=unclaimed` 仍返回其内容元信息；读取正文仍走公开静态内容。
- 业务范围：参考答案读写、学员作答状态、人工批阅、学习者作答进度与内容授权替换都限定在“我的课件”内；范围外课件返回 `403 courseware_not_claimed`。
- 内容授权替换只作用于“我的课件”范围，范围外的既有授权必须保留，避免多名管理员互相覆盖。

## 7. System Status contract

`/system/status` 的稳定响应为：

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
