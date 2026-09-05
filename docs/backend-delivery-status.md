# Java 后端交付状态与验收门槛

更新时间：2026-09-04。

## 已完成

- Java 模块化单体已接管身份/会话、学习者/内容授权、签到、积分/卡片、作答/自动批阅、答案管理、人工批阅和错题本。
- Vue 的动态业务数据统一经 `/api/v1` gateway；运行时 `supabase-js`、Supabase URL 和 anon key 已删除。
- Flyway V1–V5 可从空 PostgreSQL 17 完整迁移，现有 Supabase 的 V5 前置审计、运行角色授权和浏览器权限收口脚本已就绪。
- OpenAPI、57 项 Java 单元/HTTP/PostgreSQL 集成测试、9 项前端 gateway 测试和 Playwright 全链路 E2E 已在本地通过。
- PR CI、GHCR commit-SHA 镜像、GitHub Pages、staging 后端部署、Environment 审批和仓库密钥扫描工作流已配置；Pages 发布由完整后端验证和 E2E 门禁阻挡，后端健康检查失败会自动恢复旧镜像。

详细能力与测试映射见 `backend-completion-matrix.md`，本地启动见 `../backend/README.md`，服务器步骤见
`../deploy/README.md`。

## 首次 V5 发布仍需完成

1. V4 签到表接管和 V5 前置审计已完成，全部异常数为 0；Flyway 当前版本为 4。
2. 服务器 `deploy/migration.env` 和 GitHub `staging` Environment secrets 已配置并验证。
3. 修复 migration-only 非 Web 启动后，重新运行 staging CD，确认 V5 migration、readiness 和 smoke test 成功。
4. 发布新版 Pages，真实验证管理员、学习者、作答/批阅、错题、签到和奖励旅程。
5. 最后撤销 Supabase 浏览器角色的业务表权限并执行对应审计。

## 保留的产品边界

文章 Markdown 仍是 GitHub Pages 静态文件，因此“内容授权”控制导航和学习数据，不保证正文 URL 保密。如果未来要求文章
正文保密，需要单独迁移到 Java 鉴权下载接口或私有对象存储；这不影响当前轻量化平台的动态业务接管。
