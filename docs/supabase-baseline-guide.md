# Supabase 生产基线导出指南

后端身份和数据 module 开工前，需要从真实 Supabase PostgreSQL 建立只读 schema 基线。不要把数据库密码、access token、生产数据或包含个人信息的 seed 提交到 Git。

## 需要准备

- Supabase project ref：Dashboard URL 中 `/project/` 后面的值。
- 数据库密码：只在本机 Supabase CLI 提示或 `SUPABASE_DB_PASSWORD` 环境变量中使用。
- Supabase CLI；CLI 通过 npm 安装时要求 Node.js 20+。
- Docker Desktop 或兼容容器运行时；`supabase db dump` 使用容器内的 `pg_dump`。

## 首次只读导出

在仓库根目录执行：

```powershell
supabase init
supabase login
supabase link --project-ref <project-ref>

New-Item -ItemType Directory -Force database\baseline
supabase db dump --linked -f database\baseline\schema.sql
supabase db dump --linked --role-only -f database\baseline\roles.sql
```

`schema.sql` 默认不包含生产数据；`roles.sql` 单独记录自定义角色。第一轮不要运行 `db push`、`db reset --linked` 或任何修改远端的命令。

如果当前不准备把 Supabase CLI 配置提交进仓库，可以只把以下文件放进共享工作区供审查：

- `database/baseline/schema.sql`
- `database/baseline/roles.sql`

不要提供数据库密码或 Supabase personal access token。

## 审查内容

收到 dump 后，开发任务将依次确认：

1. PostgreSQL 大版本、extension、sequence、function 和 trigger。
2. `student`、`article_answer_keys`、`article_question_submissions`、`wrong_questions` 的真实字段类型。
3. primary/foreign/unique/check constraint 和索引。
4. RLS policy 以及 `anon`、`authenticated`、`service_role` privilege。
5. 代码假定的组合唯一键是否真的存在。
6. 哪些 SQL 可作为 Flyway baseline，哪些属于 Supabase 托管对象而不应迁入应用 schema。

## 生产数据

首轮实现不需要生产 data dump。需要迁库演练时再执行 `--data-only`，输出到 Git 忽略、加密且访问受限的位置；清洗后的开发 seed 应手工构造，不能直接提交生产学习者和答案数据。

## 后续迁移管理

schema dump 审查完成后，再决定是否使用 `supabase db pull` 建立 Supabase CLI migration history。该命令会把生成的 baseline 记录为远端已应用 migration，因此不在首次只读盘点阶段执行。
