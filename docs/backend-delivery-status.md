# Java 后端交付状态与验收门槛

更新时间：2026-08-15。

## 现在可以验证什么

Java 后端已经具备并通过自动化测试的能力：

- 系统状态、liveness、readiness。
- CSRF、密码登录、服务端会话恢复、退出和会话撤销。
- 管理员为现有学习者直接设置或重置密码；重置后旧密码、旧会话和未使用激活码立即失效。
- 一次性账户激活和管理员签发激活码。
- 服务端管理员角色鉴权和分页查询学习者。
- PostgreSQL V1 至 V3、生产 Flyway baseline、管理员自举。
- Supabase 专用最小权限运行角色和 RLS policy。
- production 配置门禁、Dockerfile、Compose、宿主机 Nginx 反向代理和 API smoke test。

因此现在可以本地验证 Java API，也可以在不切换现有 GitHub Pages 的情况下部署 API-only staging。

Vue 身份切片也已在开发分支完成：密码登录、CSRF、服务端会话恢复、退出、管理员角色路由守卫均改用 Java；
`localStorage` 伪会话、`young` 特判、公开无密码注册和 `+++` 管理入口已经移除。前端 production build 已通过。

## 整站切换前仍需完成

按用户旅程而不是技术分层依次完成：

1. 学习者管理闭环：管理员创建学习者和授予文章权限。现有学习者的直接密码设置已经完成。
2. 学习主链：文章学习状态、保存作答、自动批阅和错题原子收集。
3. 错题本：列表、手动收集、编辑、掌握、移除和幂等复活。
4. 管理批阅：答案键读取/维护、待批阅查询、人工批阅。
5. 删除 Vue 对四张业务表的 Supabase 直接调用，增加关键旅程 E2E，再撤销 `anon` 业务表写权限。

现在已经可以让使用者本地验收新登录。再完成学习者管理和学习主链后，可以验收主要学习流程；完成全部 5 项、E2E
和权限收口后，才满足整站 staging 和生产切流条件。

## 尚需产品确认

文章 Markdown 当前位于 GitHub Pages 的 `public/articles`，知道 URL 的人可以直接下载。如果“文章权限”只是导航和学习进度，
可以继续静态公开；如果权限表示内容保密，正文必须迁到 Java 鉴权接口或私有对象存储。这一决定不阻塞 API-only staging，
但会阻塞整站生产切流。

## 当前外部阻断

本机 Docker Hub 被不可达 IPv6 网络阻断，无法下载 Maven/JRE 基础镜像。后端 21 项 Maven/Testcontainers 测试和
Compose 静态校验均已通过；GitHub Actions 的 GHCR 工作流会在 GitHub Runner 上重新测试、构建并发布 commit-SHA 镜像。
