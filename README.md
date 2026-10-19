# NEST · 学巢

轻量学习平台：学科讲义、答题与批阅、错题本与训练、每日签到、积分收藏卡。
前端 Vue 3 + Vite，后端 Spring Boot + PostgreSQL（Supabase）。

## 技术栈

- 前端：Vue 3、Vite 5、Pinia、Vue Router、Font Awesome、KaTeX（CDN）
- 后端：Java 21、Spring Boot 4、Flyway、PostgreSQL / Supabase
- 测试：Node Test（前端 gateway）、JUnit / MockMvc / Testcontainers、Playwright E2E

## 本地开发

```powershell
# 1. 安装依赖
npm ci

# 2. 启动后端（8080，读取 backend/.env 连接 Supabase）
cd backend
.\mvnw.cmd -B -ntp spring-boot:run

# 3. 另开终端启动前端（3000）
cd ..
npm run dev
```

前端开发服务器会把 `/api` 代理到 `http://127.0.0.1:8080`，无需处理 CORS。
如需连接其他后端，修改 `tutor-nest-vue/.env.local` 或设置 `VITE_API_BASE_URL`。

> 登录鉴权由后端 `/api/v1` 承担，前端不直接调用 Supabase。

## 数据库：补齐全量表结构

本地后端使用的 `DATABASE_USERNAME` 是受限应用角色，**没有 DDL 权限**，不能自动建表。
如果 Supabase 库只有 `account / student / user_points / daily_check_in` 等表，`/wrong-book`、
`/rewards`、`/articles/study-progress` 会返回 503。

在 Supabase Dashboard → SQL Editor 用**数据库所有者 / postgres 角色**执行：

```text
database/reconciliation/2026-10-19_create_missing_learning_tables.sql
```

该脚本可重复执行，会补齐：

- `article_answer_keys`、`article_question_submissions`、`wrong_questions`
- `task_claims`、`card_collection`
- 对应索引、`updated_at` 触发器与约束

建表后还需要给后端角色授权并启用 RLS 策略，否则接口仍会 503（`permission denied for table ...`）。
在 SQL Editor 继续执行：

```text
database/operations/provision_runtime_role.sql
```

执行后无需重启后端，刷新页面即可。

## 数据库：成就与周期 / 限时任务（V6）

成就、每日/每周任务和限时任务使用新表。已有 Supabase 库需要再用 postgres 角色执行：

```text
database/reconciliation/2026-10-19_growth_achievements_tasks.sql
database/operations/provision_runtime_role.sql
```

第一条创建 `achievement_claims`、`task_assignments` 并扩展 `task_claims`；
第二条授予运行角色权限、启用 RLS 策略并补齐序列权限。执行后重启后端，`/api/v1/achievements`
与 `/api/v1/tasks/board` 才会返回 200。

## 常见问题

- **改完配置或依赖后白屏 / 页面空白**：Vite dev 的旧模块缓存导致，`Ctrl+C` 重启 `npm run dev`，浏览器 `Ctrl+Shift+R` 硬刷新。页面已内置路由进度条、chunk 加载失败自动刷新与渲染异常兜底页。
- **登录或数据请求 503**：数据库缺表，按上一节执行 reconciliation SQL。
- **登录 401**：未登录的正常响应，不是错误；登录后应变为 200。
- **登录或接口间歇性 401 / 超时**：Supabase 跨境连接较慢时，5 秒默认连接超时会触发 `SocketTimeoutException`，表现成随机的 401 或 11–30 秒延迟。请保持：
  ```properties
  DATABASE_CONNECTION_TIMEOUT_MS=20000
  DATABASE_POOL_MIN_IDLE=1
  ```
  并重启后端。
- **`xapi.yhchj.com` / `jquery.js` 报错**：来自浏览器扩展，不是站点代码。

## 常用命令

```powershell
npm run dev            # 本地开发（含文章数据同步）
npm run build          # 生产构建
npm run preview        # 预览构建产物
npm run check:sfc      # SFC 语法/模板编译检查（不写文件）
npm run test:frontend  # 前端 gateway 测试
npm run test:openapi   # OpenAPI 规范校验
npm run test:secrets   # 密钥扫描
npm run test:e2e       # Playwright 用户旅程
```

## 目录

```text
tutor-nest-vue/         Vue 前端
  src/assets/theme.css  设计系统（Riso Campus：纸感 + 墨线 + 印刷色）
  src/components/       公共组件与页面子组件
  src/views/            页面：首页 / 目录 / 文章 / 任务 / 错题 / 管理 / 404 / 设置
  src/gateways/         后端 API 客户端
  src/stores/           Pinia 状态
backend/                Spring Boot 后端
database/               Flyway migration 与 reconciliation SQL
docs/                   设计契约、API 规范、完成度矩阵
scripts/                构建与质量脚本
```

## 设计

当前视觉为 **Riso Campus 印刷学院风**：奶油纸底、墨黑排版、朱红/钴蓝/黄绿平涂色块、1.5px 墨线与错位硬阴影。设计 token 与组件类见 [theme.css](tutor-nest-vue/src/assets/theme.css)，协作契约见 [design-system.md](docs/design-system.md)，预览图见 [design-preview](docs/design-preview)。

## 部署

推送到 `main` 后由 GitHub Actions 依次执行 CI → 后端镜像发布 → 数据库迁移 → 部署与健康检查；前端发布到 GitHub Pages。详见 [AGENTS.md](AGENTS.md)。
