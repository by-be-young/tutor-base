---
status: proposed
---

# 以 Java 模块化单体接管浏览器的数据访问

平台拟采用 Java 21 LTS、Spring Boot 4.1 和 PostgreSQL 构建模块化单体，通过版本化 HTTP 接口承接身份、授权、作答、批阅和错题业务；浏览器最终不再直连 Supabase Data API。迁移先让 Java 后端连接现有 Supabase PostgreSQL，待接口切换稳定后再把数据库迁往目标 PostgreSQL，以避免同时替换应用接口和数据库。

## Considered Options

- 继续让 Vue 直接访问 Supabase：改动最少，但无法可靠隐藏管理员能力、集中业务规则或摆脱数据库供应商接口。
- 一次性重写并同时迁库：最终状态更快出现，但认证、接口和数据三类风险无法隔离，回滚困难。
- 拆分微服务：当前业务规模和团队维护成本不支持独立部署、消息一致性与跨服务可观测性的额外复杂度。

## Consequences

- Java 后端是唯一可信的授权与写入入口，管理员身份不再由用户名或前端路由决定。
- 第一阶段继续使用 Supabase PostgreSQL，只把 Supabase 当作 PostgreSQL 托管方；这为后续迁库保留标准 SQL/备份恢复路径。
- 后端按业务能力形成模块，数据库 adapter 是模块内部 implementation；不创建仅转发调用的多层空壳。
- 数据库变更采用 Flyway 的 expand/backfill/switch/contract 顺序，应用切换和物理迁库分别验收。
