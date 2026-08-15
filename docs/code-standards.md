# 代码规范

本文是 `tutor-base` 的工程约束。它适用于新增 Java 后端、前端 API 迁移代码、数据库 migration、测试和部署脚本；若代码与本文冲突，应在合并前修改代码或通过 ADR 明确修改规范。

## 1. 总体原则

1. 生产行为以测试、OpenAPI contract 和 versioned migration 为依据，不以口头约定或控制台状态为依据。
2. 优先完成纵向业务能力，不创建只转发调用的 `Controller -> Service -> Repository` 空壳链路。
3. Module 对 callers 暴露小 interface，把事务、授权、状态转换和 adapter 隐藏在 implementation 内。
4. 浏览器不可信。身份、角色、资源归属、内容授权和状态转换全部由后端验证。
5. Migration 必须向后兼容当前已部署版本；破坏性变更使用 expand/backfill/switch/contract。
6. 不顺手重构无关代码，不提交 secret、生成物、IDE 私有文件或本地数据库数据。

## 2. 仓库与模块结构

```text
backend/
|- api/openapi.yaml
|- pom.xml
|- .mvn/ + mvnw + mvnw.cmd
`- src/
   |- main/java/com/tutorbase/
   |  |- TutorBaseApplication.java
   |  |- system/
   |  |- identity/
   |  |- access/
   |  |- learning/
   |  |- wrongbook/
   |  |- catalog/
   |  `- shared/
   |- main/resources/
   |  |- application.yaml
   |  `- db/migration/
   `- test/java/com/tutorbase/
```

- 顶层 package 按业务 module 划分，不按技术层全局划分。
- `shared` 只放真正跨 module 的稳定技术能力，例如 Problem Details、时钟和 ID 类型；不能成为杂物目录。
- 每个 module 的 public 类型保持最少。默认 package-private，只有 caller 必须使用的 interface、command/result 和明确的 adapter 配置才公开。
- 跨 module 调用通过对方的 interface；禁止读取对方 repository、entity 或内部表映射。
- 数据库 adapter 是 module implementation，不在 interface 中暴露 JDBC/JPA 类型。

## 3. Java 规范

- 基线为 Java 21 LTS、Spring Boot 4.1；禁止依赖 preview feature。升级 JDK 必须先通过完整 `mvn verify` 和容器构建。
- 使用 Maven Wrapper；依赖版本优先交给 Spring Boot dependency management，不在子 module 随意覆写。
- 使用 4 空格缩进、UTF-8、LF、末尾换行；一行建议不超过 120 字符。
- package 全小写；class/record 使用 PascalCase；method/variable 使用 camelCase；常量使用 UPPER_SNAKE_CASE。
- DTO、command 和不可变值优先使用 `record`；不要把数据库 row object 直接作为 HTTP response。
- dependency 使用 constructor injection；禁止 field injection 和在业务方法中 `new` 远程/数据库 dependency。
- 禁止使用 Lombok。少量样板代码换取清晰的编译期 interface 和更少的 IDE/plugin 隐式行为。
- 禁止返回 `null` collection；返回空 collection。仅在“缺失”是正常结果时使用 `Optional`，不作为 DTO 字段。
- 时间使用 `Instant` 并按 UTC 持久化/传输；业务代码通过注入的 `Clock` 获取当前时间。
- 金额、ID、状态等概念使用专用类型或 enum，不在 module 间传播无语义的 `String/Object/Map`。
- catch 必须恢复、转换或补充上下文；禁止 catch 后静默忽略。不要向客户端暴露 SQL、stack trace 或 secret。

## 4. HTTP 与 DTO

- controller 是 HTTP adapter，只负责协议解析、认证上下文映射和结果序列化；不包含业务分支和事务。
- request/response 与 module command/result 分离，只有字段完全稳定且语义一致时才允许复用。
- 所有输入使用 Jakarta Validation；字符串在 module interface 前完成 trim/normalization，但不擅自改变答案正文。
- 错误统一为 RFC 9457 Problem Details，并带稳定的 `code`；前端禁止依赖英文/中文 `detail` 做逻辑判断。
- 新 endpoint 必须先更新 `backend/api/openapi.yaml` 或与实现同一提交更新。
- 不暴露数据库表 CRUD；endpoint 表达账户激活、保存作答、批阅、收集错题等任务。

## 5. 安全规范

- 默认拒绝：除健康检查、账户激活和创建会话外，所有 `/api/**` endpoint 都需认证。
- 管理 endpoint 同时检查 `ADMINISTRATOR` 角色；学习者资源从服务端 session 取 account ID，不接受客户端指定“当前学习者”。
- 生产使用 `HttpOnly`, `Secure`, `SameSite=Lax` 的 opaque session cookie；登录和权限提升后轮换 session ID。
- cookie session 的 mutation request 保持 CSRF 防护，前端按接口规范发送 token header。
- 密码使用经过 Spring Security 支持的 Argon2id 配置；激活码和 reset token 只保存 hash。
- 登录、激活和敏感管理动作必须可限流、可审计。日志中禁止密码、session、激活码、Authorization header 和完整答案正文。
- 前端与 API 跨 origin、同 site 部署。CORS 默认拒绝，只从配置读取精确的生产/staging origin；显式列出
  method/header，允许 credentials 时绝不使用 `*`。preflight 必须在认证过滤之前处理。

## 6. PostgreSQL 与 migration

- Flyway SQL 是 schema 的唯一写入来源；启用后禁止直接在 Supabase Dashboard 修改生产结构。
- 第一条生产 baseline 必须来自真实 schema dump，不从 README 或前端调用反推。
- 文件名使用 `V{序号}__{lower_snake_case}.sql`；已在任何共享环境执行的 migration 永不修改，只新增后续 migration。
- DDL 明确 primary key、foreign key、unique、check、默认值和必要索引；外键列通常需要单独索引。
- migration 应可在合理锁等待内完成；大表 backfill 分批且幂等，不在单个 DDL 中长时间锁表。
- SQL 参数必须绑定，禁止字符串拼接。动态排序/字段名只能从代码白名单映射。
- transaction 属于业务 module interface 的一次调用。不要让 controller 拼接多次 repository 调用形成半完成状态。
- 自动批阅和自动收集错题必须在同一 transaction 内完成；原子 upsert 替代“先查再写”。

## 7. 测试规范

- Interface 是主要测试面：测试 observable result、持久化结果和 error mode，不断言私有 method 或内部调用次数。
- 纯规则使用快速单元测试；涉及 SQL、constraint、transaction 和 Flyway 的测试使用 Testcontainers PostgreSQL。
- HTTP adapter 使用 MockMvc 测试状态码、Problem Details、安全规则和 JSON contract。
- 每个 production defect 至少增加一个先失败后通过的回归测试。
- 测试命名采用 `given...When...Then...` 或可读的行为句；一个测试只表达一个失败原因。
- 测试数据 builder 默认生成有效对象，只在当前场景覆盖相关字段。
- 不用 H2 代替 PostgreSQL 验证 SQL 行为。
- PR 最低门槛：`mvn verify`、前端 lint/test/build、OpenAPI 校验、`git diff --check`。

## 8. 日志与可观测性

- 使用结构化日志和参数占位符，不用 `System.out`、字符串拼接或打印 stack trace 到 stdout。
- 每个请求保留/生成 `traceId`；Problem Details 可返回 traceId，但不得包含内部异常文本。
- INFO 记录部署版本和关键业务结果；WARN 记录可恢复异常；ERROR 只用于需要人工处理的失败。
- Actuator 默认只对公网暴露 health/readiness；metrics、env、config 等 endpoint 仅内网或管理员可见。
- health check 不执行昂贵查询；readiness 可验证必要 dependency 是否可用。

## 9. 前端迁移规范

- 页面和 Pinia store 不再直接 import Supabase client。
- 迁移期 gateway 按 `identity/learning/admin/wrongBook` 能力划分，不按数据库表划分。
- HTTP adapter 统一处理 base URL、credentials、CSRF、Problem Details 和超时；页面不重复拼接 fetch 配置。
- staging 和旧生产可分别选择 HTTP/Supabase adapter，但单个浏览器会话禁止双写。
- 最终切换后删除 Supabase adapter 和无价值的转发 interface，避免永久维护两套路径。

## 10. 提交与评审

- 一个提交完成一个可描述的行为变化，并包含相应测试/contract/migration。
- commit message 使用 `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `build:`, `ci:` 前缀。
- PR 描述必须列出行为变化、验证命令、migration/回滚影响和尚未解决的风险。
- 不在没有明确授权时提交、推送、部署或修改生产 Supabase。
