# V26 主键迁移 Runbook（`courseware_catalog` 收窄到老师维度）

> 状态：**脚本已写好并在临时 PostgreSQL 容器中演练通过，尚未纳入 Flyway、尚未上线。**
> 脚本位置：
> - 正向：[`database/planned/V26__teacher_scoped_courseware_keys.sql`](../database/planned/V26__teacher_scoped_courseware_keys.sql)
> - 回滚：[`database/planned/V26__teacher_scoped_courseware_keys.rollback.sql`](../database/planned/V26__teacher_scoped_courseware_keys.rollback.sql)
>
> 刻意放在 `database/planned/`（不在 Flyway 扫描路径内），避免被部署流水线意外执行。
> 设计依据见 [`docs/courseware-hosting-design.md`](./courseware-hosting-design.md) §11.2。

---

## 1. 这次迁移到底改什么

`V21` 给 `courseware_catalog` 加了 `teacher_account_id` 并回填，但主键仍是全局
`article_id`，所以「老师 A 和老师 B 各自拥有 第 3 篇」无法表达。V26 做四件事：

| # | 变更 | 说明 |
| --- | --- | --- |
| 1 | `teacher_account_id` 收紧为 `NOT NULL` | 主键列不能为空 |
| 2 | 解绑三张旧认领表到 `courseware_catalog(article_id)` 的外键 | `admin_courseware_claim` / `admin_courseware_exclusion` / `admin_courseware_abandonment` |
| 3 | 主键 `article_id` → `(teacher_account_id, article_id)` | 唯一性范围从"全站"收窄到"老师内" |
| 4 | 新增 `courseware_catalog_teacher_legacy_unique` 与 `courseware_catalog_legacy_idx` | 保住"同一老师内 legacy id 唯一"，支撑权限/答案键按 legacy id 反查 |

### 1.1 不可逆的地方

- 三张旧认领表**失去外键**后，只作**只读历史**保留。写入侧另有开关
  `tutor.courseware.legacy-claims-enabled`（`false` → 410 `courseware_claims_retired`）兜住。
- 回滚脚本能恢复主键与三个外键，但**只能在"回滚前不存在跨老师重复 `article_id`"时成功**；
  一旦真的有两位老师各自拥有第 3 篇，回滚会先被断言拦下（这是有意的：强行回滚会丢行）。

---

## 2. 演练结果（已验证）

临时容器 `v26-scratch`（`postgres:16`）：V1–V25 全量应用 → 插入跨老师同名数据 → 正向 → 回滚 → 再正向。

| 用例 | 期望 | 实测 |
| --- | --- | --- |
| V1–V25 + V26 全量应用 | 主键变为 `(teacher_account_id, article_id)` | ✅ `pg_get_constraintdef` 确认 |
| `(7, 3)` 与 `(8, 3)` 并存 | 允许 | ✅ 两行同时存在 |
| 插入 `teacher_account_id IS NULL` | 被拒 | ✅ `violates not-null constraint` |
| 存在跨老师重复时执行回滚 | 被断言拦下 | ✅ `RAISE EXCEPTION`，退出码 3 |
| 删除重复行后执行回滚 | 主键回到 `(article_id)`、三个外键回归 | ✅ 三个 FK 全部 `-> courseware_catalog` |
| 回滚后再次执行正向 | 可重复执行 | ✅ 幂等（`DROP CONSTRAINT IF EXISTS` / `IF NOT EXISTS`） |

> 演练只覆盖 DDL 与约束行为，不覆盖应用侧代码兼容性（见 §3）。

---

## 3. 上线前置条件（**缺一不可**）

V26 换主键后，任何"只按 `article_id` 定位目录行"的代码都会变成多行/歧义。以下必须在同一
发布窗口内先改完：

| 位置 | 现有写法 | 为什么不兼容 | 处置 |
| --- | --- | --- | --- |
| [`JdbcCoursewareClaimAdministration#syncCatalog`](../backend/src/main/java/com/tutorbase/administration/courseware/JdbcCoursewareClaimAdministration.java:46) | `INSERT ... (article_id, subject, title, updated_at) ON CONFLICT (article_id)` | 缺 `teacher_account_id` → 违反 `NOT NULL`；`ON CONFLICT (article_id)` 不再命中任何唯一约束 | **已处理**：该接口已受 `legacy-claims-enabled` 收口（`false` → 410），V26 前必须确保开关为 `false` |
| [`JdbcCoursewareClaimAdministration`](../backend/src/main/java/com/tutorbase/administration/courseware/JdbcCoursewareClaimAdministration.java:427) | `WHERE article_id = :articleId` | 跨老师会命中多行 | 同上，随认领链路停用 |
| [`JdbcCoursewareScope`](../backend/src/main/java/com/tutorbase/administration/courseware/JdbcCoursewareScope.java:16) | `scoped_catalog.article_id = %s` 反查可见性 | 会匹配到任意老师的同名行，越权放行 | **已处理**：新增「发布归属」判定（`course_release_head` + `course_article.legacy_article_id`），不再只靠旧认领表；旧分支保留到认领链路彻底删除 |
| `JdbcContentGrantQuery` / `JdbcLearnerAdministration` / `AdminAnalyticsService` | `FROM courseware_catalog` 聚合 `article_id` | 同名会重复计数（`subjectProgress` 的 `count(*)` 会翻倍、特殊测试学员的 permissions 数组会出现重复值） | **已处理**：`JdbcContentGrantQuery` 改 `array_agg(DISTINCT …)`；`AdminAnalyticsService.coursewareStats` 改用 `SELECT DISTINCT article_id, subject` 子查询；`subjectProgress` 的 catalog join 增加 `teacher_account_id` 匹配（`answer.teacher_account_id IS NULL` 时保持旧行为）；`JdbcLearnerAdministration` 与其余位置是 `EXISTS`/`DISTINCT` 语义，无需改动；`visibleArticleIds` 已保证去重排序（`Set.copyOf` 不会因重复元素抛异常） |

配套的运维动作：

1. `TUTOR_COURSEWARE_LEGACY_CLAIMS_ENABLED=false`（写入侧一律 410），并保持至少一个发布周期；
2. 确认线上已无 `syncCatalog` 调用（旧认领页「旧版认领」入口已隐藏）；
3. 确认 `tutor.courseware.source=storage` 或 `static` 两种模式下前端都能取到课件（迁移不影响正文）。

---

## 4. 前置检查（执行前跑，全部通过才继续）

```sql
-- 1) 是否还有未回填老师维度的目录行（期望 0）
SELECT count(*) AS missing_teacher
FROM public.courseware_catalog
WHERE teacher_account_id IS NULL;

-- 2) 老师维度回填是否合理：每位老师各多少篇（人工核对，尤其是"无人认领落初始老师"的那批）
SELECT teacher_account_id, count(*) AS articles
FROM public.courseware_catalog
GROUP BY teacher_account_id
ORDER BY teacher_account_id;

-- 3) legacy id 是否回填完整（期望 0）
SELECT count(*) AS missing_legacy
FROM public.courseware_catalog
WHERE legacy_article_id IS NULL;

-- 4) 同一老师内 legacy id 是否有重复（期望返回空）——否则第 4 步的唯一索引会失败
SELECT teacher_account_id, legacy_article_id, count(*)
FROM public.courseware_catalog
WHERE legacy_article_id IS NOT NULL
GROUP BY teacher_account_id, legacy_article_id
HAVING count(*) > 1;

-- 5) 旧认领表仍有多少行（只作留档，确认业务已不依赖）
SELECT 'claim' AS tbl, count(*) FROM public.admin_courseware_claim
UNION ALL SELECT 'exclusion', count(*) FROM public.admin_courseware_exclusion
UNION ALL SELECT 'abandonment', count(*) FROM public.admin_courseware_abandonment;
```

任一项不符合预期就先修数据，不要硬跑。

---

## 5. 备份

```powershell
# 1) 全库备份（自定义格式，可 pg_restore 单表）
docker exec <pg-container> pg_dump -U <user> -d <db> -Fc -f /tmp/pre-v26.dump
docker cp <pg-container>:/tmp/pre-v26.dump .\backups\pre-v26-<date>.dump

# 2) 只备份受影响的两类表（回滚时的最后手段）
docker exec <pg-container> pg_dump -U <user> -d <db> `
  -t public.courseware_catalog `
  -t public.admin_courseware_claim -t public.admin_courseware_exclusion `
  -t public.admin_courseware_abandonment `
  -Fc -f /tmp/pre-v26-catalog.dump
```

对象存储无需处理：blob 内容寻址，V26 不改任何正文与图片。

---

## 6. 执行（需要停机窗口）

DDL 会短暂持有 `courseware_catalog` 与三张认领表的排他锁；数据量小（当前 42 篇量级），
实际持锁时间在**秒级**，但建议仍在维护窗口内执行。

### 6.1 正式路径（纳入 Flyway）

1. 把正向脚本移动到 `backend/src/main/resources/db/migration/V26__teacher_scoped_courseware_keys.sql`；
2. 在 `database/reconciliation/` 增加幂等版本 `2026-xx-xx_teacher_scoped_courseware_keys.sql`（与仓库惯例一致）；
3. 同步 [`FlywayMigrationTest`](../backend/src/test/java/com/tutorbase/database/FlywayMigrationTest.java)
   （`EXPECTED_TABLES`、当前版本 25 → 26、增量执行数）；
4. 走正常 CD：迁移先于应用替换执行（见 `backend-deploy.yml`）。

### 6.2 应急路径（运维手动执行）

```powershell
# 单事务执行，任何断言失败整体回滚，不会留下半成品
Get-Content .\database\planned\V26__teacher_scoped_courseware_keys.sql |
  docker exec -i <pg-container> psql -U <user> -d <db> -v ON_ERROR_STOP=1
```

> 脚本内部已有 `BEGIN; ... COMMIT;`，`ON_ERROR_STOP=1` 保证断言报错时停止。

---

## 7. 执行后验证清单

```sql
-- 1) 主键已换（期望 PRIMARY KEY (teacher_account_id, article_id)）
SELECT pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'public.courseware_catalog'::regclass AND contype = 'p';

-- 2) teacher_account_id 不可空（期望 attnotnull = true）
SELECT attname, attnotnull
FROM pg_attribute
WHERE attrelid = 'public.courseware_catalog'::regclass AND attname = 'teacher_account_id';

-- 3) 三张旧认领表不再有指向 courseware_catalog 的外键（期望 0 行）
SELECT conrelid::regclass AS child
FROM pg_constraint
WHERE contype = 'f' AND confrelid = 'public.courseware_catalog'::regclass;

-- 4) 新索引存在
SELECT indexname FROM pg_indexes
WHERE tablename = 'courseware_catalog'
  AND indexname IN ('courseware_catalog_teacher_legacy_unique', 'courseware_catalog_legacy_idx');

-- 5) 行数与迁移前一致
SELECT count(*) FROM public.courseware_catalog;
```

应用侧：

- [ ] `GET /api/v1/courseware/manifest` 正常返回，篇数与库里 `course_release_head` 一致；
- [ ] `GET /admin/courseware/overview` 各老师分组正确；
- [ ] 登录学员 → 课件列表 → 打开正文 → 作答 → 错题本题干正常（快照链路不受影响）；
- [ ] 旧认领接口返回 410 `courseware_claims_retired`（开关为 false 时）；
- [ ] 管理端「共享」「编辑器」「我的课件」三个页面无 500。

---

## 8. 回滚

### 8.1 触发条件

- 迁移后出现跨老师越权（同一 `article_id` 的正文/权限串号）；
- 应用侧改动未能同批上线，导致 `syncCatalog` 等旧路径持续 500；
- 主键变更引发未预料的查询歧义（同一篇在列表里重复出现）。

### 8.2 步骤

```powershell
# 0) 先确认没有跨老师重复（有的话回滚必被断言拦下，需先决定这些行怎么并/删）
docker exec -i <pg-container> psql -U <user> -d <db> -c @"
SELECT article_id, count(*) FROM public.courseware_catalog
GROUP BY article_id HAVING count(*) > 1;
"@

# 1) 执行回滚（单事务，断言失败整体回滚）
Get-Content .\database\planned\V26__teacher_scoped_courseware_keys.rollback.sql |
  docker exec -i <pg-container> psql -U <user> -d <db> -v ON_ERROR_STOP=1

# 2) 验证：主键回到 (article_id)、三个 FK 回归、teacher_account_id 可空
```

```sql
SELECT pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid = 'public.courseware_catalog'::regclass AND contype = 'p';      -- PRIMARY KEY (article_id)

SELECT conrelid::regclass FROM pg_constraint
WHERE contype = 'f' AND confrelid = 'public.courseware_catalog'::regclass;     -- 3 行

SELECT attnotnull FROM pg_attribute
WHERE attrelid = 'public.courseware_catalog'::regclass AND attname = 'teacher_account_id';  -- false
```

3. 回滚后把 `TUTOR_COURSEWARE_LEGACY_CLAIMS_ENABLED` 调回 `true`（旧认领链路恢复可用），
   否则会出现"表已可写、接口仍 410"的半截状态。

### 8.3 如果回滚也被拦住

说明已经有两位老师各自拥有同一个 `article_id`（这是 V26 的目的）。此时不要强改主键，
用 §5 的表级备份单独恢复 `courseware_catalog`，或按业务决定把重复行迁移到新 id 后再回滚。

---

## 9. 上线顺序（与设计稿 §13.1 对齐）

1. 部署应用改动（`syncCatalog` 下线、`JdbcCoursewareScope` 改归属判定、聚合加老师维度）；
2. 置 `TUTOR_COURSEWARE_LEGACY_CLAIMS_ENABLED=false`，观察一个发布周期；
3. 维护窗口执行 V26；
4. 按 §7 验证；
5. 若失败按 §8 回滚，并记录到 `docs/backend-completion-matrix.md`。

---

## 10. 变更记录

| 日期 | 内容 |
| --- | --- |
| 2026-10-31 | 初版：脚本完成 + 临时容器演练通过（正向/回滚/幂等/断言均实测），runbook 建立 |
| 2026-10-31 | 代码侧前置项落地：`/admin/courseware-catalog/sync` 受 `legacy-claims-enabled` 收口（410）；`JdbcCoursewareScope` 增加「发布归属」判定并保留旧认领分支；新增 `CoursewareScopeOwnershipTest` |
| 2026-10-31 | 聚合去重：`JdbcContentGrantQuery`（`array_agg(DISTINCT …)`）、`AdminAnalyticsService.coursewareStats`（`DISTINCT (article_id, subject)` 子查询）与 `subjectProgress`（catalog join 加 `teacher_account_id`） |
