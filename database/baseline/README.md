# 数据库结构基线

`schema.sql` 是从 Supabase 导出结果中清理出的应用结构基线，只保留当前学习平台实际使用的 `public` schema 对象。它用于审查和生成后续 Flyway migration，不应直接推送到生产数据库。

## 保留的业务表

- `student`
- `article_answer_keys`
- `article_question_submissions`
- `wrong_questions`

虽然最初提供的表清单没有列出 `wrong_questions`，但前端的错题列表、错题训练和自动收集功能均直接读写该表，因此不能删除。

## 已移除的误导出表

- `exam_records`
- `game_history`
- `operators`
- `questions`
- `training_questions`
- `user_answers`
- `users`

同时移除了这些表独占的 sequence、constraint、index、RLS policy 和 grant。

## 未纳入应用基线的 Supabase 对象

以下内容由 Supabase 平台管理，不能直接复制到普通云 PostgreSQL：

- `pg_graphql`、`pg_stat_statements`、`supabase_vault` 等 extension
- `anon`、`authenticated`、`authenticator`、`service_role` 等平台角色设置
- `supabase_realtime` publication
- Supabase 默认 privilege 和面向浏览器直连的 grant/RLS policy

`roles.sql` 保留为原始审计材料，但不会成为 Flyway migration 的输入。

生产数据复制或增加更严格约束前，先在 Supabase SQL Editor 中执行
`database/audit/pre_migration_checks.sql`。脚本使用只读事务，不会修改数据；除明确标为
`informational` 的题目标识统计外，其余 `anomaly_count` 应为 `0`。

2026-08-14 的生产数据审计中所有检查项均为 `0`，因此 Flyway V2 已加入非空、外键、
计数和批阅状态一致性约束，以及统一的 `updated_at` 触发器。

## 已确认的结构差异

- 数据库中的主键和文章 ID 使用 `bigint`，不是 32 位 `int`。
- 两张答题表中的 `question_id` 是 `text`，前端也按文本题目标识使用。
- 原导出文件对 `(blog_id, student_id, question_id)` 定义了两个完全相同的唯一约束；清理后只保留一个。
- 原导出结构中 `wrong_questions.student_id` 没有外键约束；生产数据确认不存在孤儿记录后，
  Flyway V2 已增加指向 `student.id` 的级联删除外键。
