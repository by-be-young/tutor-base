# 课件托管化改造方案

> **状态**：设计稿（本文件不含代码改动）
> **日期**：2026-10-30
> **代码基线**：`feat/high-school-subject-rename` 分支，提交 `746bd0e`
> **技术栈**：Vue 3 + Vite 前端 / Spring Boot 后端 / Supabase PostgreSQL
> **一句话**：平台只做"托管 + 身份 + 学习记录"，课件正文与图片存对象存储、按老师命名空间隔离；老师之间完全独立、没有公共课件；获取别人的课件 = 网页上一键复制；复制后若来源有更新，接收方显示"有可更新资源"。

---

## 1. 目标与边界

### 1.1 产品定位

| 维度 | 结论 |
| --- | --- |
| 平台角色 | 纯托管工具：存内容、渲染内容、记学习数据，不做教学质量背书 |
| 老师之间 | 完全独立，各自一份课件空间，互相不可见（除主动共享/下载） |
| 公共课件 | 取消。不存在"平台统一课件库"这一概念 |
| 认领机制 | 取消。老师对自己空间的课件天然全权 |
| 获取他人课件 | 必须由对方**主动分享**（整科或单篇）后才可见；可整科获取，也可展开目录选单篇；获取时可改名、改路径；也支持整包下载后自行导入 |
| 复制后的关系 | 一次性复制，不自动同步；来源更新/新增时给出"可更新"提示与内容预览，由接收方决定是否更新 |
| 编辑方式 | 在线编辑器（格式模板 + 混合布局）；下载 zip 本地编辑；Git 工作区（仓库只存清单，正文按需拉取） |
| 学员视角 | 一个学员可同时属于多位老师；首页按"老师 → 科目"分组展示，记录也按老师区分 |

### 1.2 明确不做（本方案已排除的选项）

1. 不做"公共题库 + 统一测评"的混合模型。
2. 不做 `canonical.md` / `presentation.md` 的受控层与个性化层拆分。
3. 不做"每位老师一套 release 但仍共享公共题库"。
4. v1 不做 Markdown 自动三方合并（只做整篇覆盖 + 差异预览 + 本地备份）。
5. 不做真实 Git 仓库的自动建仓/自动 fork 作为运行时存储（Git 仅作为可选导入来源）。

### 1.3 必须保住的不变量

| 编号 | 不变量 | 原因 |
| --- | --- | --- |
| I1 | `article_id` 在"老师命名空间"内稳定，不随文件路径/文件名变化 | 权限、答案键、错题本、成绩都引用它 |
| I2 | 已发布内容不可变；改内容 = 发新版本 | 历史记录可复现，回滚简单 |
| I3 | 学习记录（作答、错题、批改）保存"当时的题干与答案快照" | 老师改题后，学员历史仍然读得懂 |
| I4 | 任何跨老师的内容流动只能显式发生（复制/下载/导入） | 杜绝隐式共享导致的"同一课件两种内容" |
| I5 | 正文不进入应用构建产物（`dist/`） | 内容更新不需要重新发版应用 |

---

## 2. 现状盘点

### 2.1 事实清单

| 类别 | 现状 | 位置 |
| --- | --- | --- |
| 课件正文 | 42 篇 Markdown，位于 `tutor-nest-vue/public/articles/` | 学科目录：`高中化学` 24 篇、`高中英语` 16 篇、`初中英语` 2 篇 |
| 课件图片 | 128 张 PNG，位于 `public/articles/图片/` | 通过 `![[高中化学-xxx.png]]` 引用 |
| 目录索引 | `articles.json`（id/title/series/date/path）、`id_map.json`（path→id）、`blogs.json` | `tutor-nest-vue/public/data/` |
| 索引生成 | `scripts/sync-articles-data.js`，`npm run frontend:sync`，按目录路径扫描，新文件分配 `max(id)+1` | ID 与路径强绑定 |
| 课件 ID 范围 | 1–44（42 篇在用） | 全局唯一，没有老师维度 |
| 前端加载索引 | `fetch(BASE_URL + 'data/articles.json')` | `src/stores/blogStore.js`、`src/stores/authStore.js` |
| 前端加载正文 | `fetch(BASE_URL + 'articles/' + blog.path)` | `src/views/ArticleDetailView.vue`、`src/composables/useWrongQuestionsResolve.js` |
| 题目解析 | `src/utils/questionText.js` 解析 `【题干N】` / `【@N】` | 与课件详情页、错题本共用 |
| 课件目录表 | `courseware_catalog(article_id PK, subject, title, updated_at)` | `V12__add_courseware_claiming.sql` |
| 学科认领 | `admin_subject_claim(admin_account_id, subject)`，可多对多 | 同上 |
| 单篇认领 | `admin_courseware_claim(admin_account_id, article_id)` | 同上 |
| 排除/放弃 | `admin_courseware_exclusion`（V15）、`admin_courseware_abandonment`（V19） | 与学科文本无关 |
| 答案键 | `article_answer_keys(blog_id, question_id, answer_text, auto_grade)`，唯一键 `(blog_id, question_id)` | `V1__application_baseline.sql` |
| 作答 | `article_question_submissions(blog_id, student_id, question_id, answer_text, review_status…)` | 同上 |
| 错题 | `wrong_questions(student_id, source_blog_id, source_question_id, correct_answer, my_answer…)` | 同上 |
| 学员归属老师 | `student.claimed_by`（V11，单一老师） | 目标改为多老师：`student_teacher` 关联表，`claimed_by` 仅作"默认/主老师" |
| 学员科目 | `student.subjects text[]`（V18） | 科目开通与文章授权分离 |
| 文章授权 | `student.permissions integer[]`（V1） | 存的是全局 `article_id` 整数 |
| 内容可见性 | 课件 md 与图片是**公开静态资源**，随 `dist/` 发布，任何人可直接拉取 | `dist/articles/` |

### 2.2 现存问题（与新定位冲突的点）

1. **单一内容源**：所有老师共享同一批 Markdown，任何老师的改动都会影响其他老师。
2. **ID 与路径绑定**：`id_map` 按路径生成，改目录/文件名就会重排 ID（已踩过：只改 `articles.json` 未改 `id_map` 会导致 42 篇 ID 全部变化）。
3. **认领语义重**：学科认领 + 单篇认领 + 排除 + 放弃，四张表维护一套"谁能改哪些课件"，系统复杂度高，且本质是"共享同一份内容"的协作模型。
4. **内容改动要发版**：老师改一个字，都要走前端构建 + 部署。
5. **公开暴露**：`dist/articles` 里的 Markdown 与图片无需登录即可读取，权限只在前端生效。
6. **改题破坏历史**：答案键、错题本都引用 `(blog_id, question_id)`，题干一改，旧记录立刻语义失真。

### 2.3 关键代码入口（改造清单的起点）

| 文件 | 当前行为 | 改造动作 |
| --- | --- | --- |
| `tutor-nest-vue/scripts/sync-articles-data.js` | 扫描 `public/articles` 生成全局 `id_map` / `articles.json` | 退化为"种子数据导入工具"，仅开发环境使用；生产索引由后端 manifest 提供 |
| `tutor-nest-vue/public/data/*.json` | 运行时索引 | 保留为默认种子（dev/demo），生产不再读取 |
| `src/stores/blogStore.js` | fetch `data/articles.json` | 改为调用 `GET /courseware/manifest`，按当前老师取 |
| `src/stores/authStore.js` | fetch `data/articles.json` 做标题映射 | 同上，改为 manifest 缓存 |
| `src/views/ArticleDetailView.vue` | 直接渲染 md | md 改为从申请接口取（带鉴权 + 版本） |
| `src/composables/useWrongQuestionsResolve.js` | `fetch(BASE_URL + 'articles/' + blog.path)` | 改为 `GET /courseware/articles/{teacherId}/{articleId}/content`，并用快照兜底 |
| `src/utils/questionText.js` | 解析 `【题干N】`/`【@N】` | 不变（解析规则与存储位置无关） |
| `src/views/admin/AdminContentView.vue` | 认领/排除/放弃 UI | 改为"我的课件 + 资源目录 + 复制/更新" |
| `src/views/admin/PermissionPanel.vue` | 按全局 article_id 授权 | 授权对象改为 `(teacherId, articleId)` |
| `src/gateways/coursewareGateway.js` | `/admin/courseware-claims/...` | 新增复制/更新相关 API，认领接口下线 |
| `backend/api/openapi.yaml` | 现有认领契约 | 增补新接口，标记旧接口废弃 |

---

## 3. 目标架构

### 3.1 分层

```
                       ┌────────────────────────────┐
                       │        浏览器（Vue 3）      │
                       │  学员端：我的课件 / 学习记录 │
                       │  老师端：我的课件 / 资源目录 │
                       └──────────────┬─────────────┘
                                      │ HTTPS（会话 Cookie）
                                      ▼
                       ┌────────────────────────────┐
                       │   应用 API（Spring Boot）   │
                       │  ┌──────────────────────┐  │
                       │  │ 课件服务 courseware   │  │
                       │  │ 发布/复制/更新/导出    │  │
                       │  └──────────────────────┘  │
                       └───┬──────────────┬─────────┘
                           │              │
        ┌──────────────────▼──┐        ┌──▼────────────────────────┐
        │ PostgreSQL（Supabase）│        │ 对象存储（Supabase Storage）│
        │ 发布记录/归属/权限    │        │ bucket: courseware         │
        │ 复制关系/答案/记录    │        │ blobs/ assets/ manifests/  │
        └──────────────────────┘        └────────────────────────────┘
```

- **数据库**：只存元数据、关系、学习记录；不存 Markdown 正文（避免大字段与备份膨胀）。
- **对象存储**：存正文 blob、图片资产、发布 manifest；内容寻址（按 sha256）保证不可变与去重。
- **应用**：唯一写入方，负责鉴权、校验、哈希计算、复制与更新判定。

### 3.2 存储介质选型

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| Supabase Storage（单桶） | 与现有 PG/RLS 同栈，无需新云账号，签名 URL 现成 | 大流量 CDN 能力一般 | **推荐**（当前规模足够） |
| 阿里云 OSS / 腾讯 COS | CDN 与带宽好，成本可控 | 多一套账号、密钥、跨域配置 | 内容量上来后再迁移 |
| S3 兼容 | 生态成熟 | 同上 | 备选 |
| 本地磁盘 / Git 仓库 | 零成本 | 无法多老师隔离、无法免发版更新、备份弱 | 仅限本地开发种子 |

**结论**：v1 用 Supabase Storage 一个私有桶 `courseware`；存储层用接口抽象（`CoursewareStorage`），后续换 OSS 只改实现。

---

## 4. 存储规范

### 4.1 桶与目录

```
courseware/                                  （私有桶，禁止匿名读）
├── blobs/
│   └── <teacher_id>/<sha256>.md             正文 blob，内容寻址
├── assets/
│   └── <sha256前2位>/<sha256>.<ext>         图片/附件，全局去重
├── manifests/
│   └── <teacher_id>/<subject>/v<version>.json  发布清单（不可变）
├── exports/
│   └── <teacher_id>/<export_id>.zip         导出包（临时，7 天过期）
└── backups/
    └── <teacher_id>/<article_id>/<sha256>.md 更新覆盖前的本地备份
```

要点：

1. **正文按内容哈希存放**：同一篇被多次发布、或复制到多个老师，物理上只存一份；manifest 引用 hash，天然不可变。
2. **图片全局去重**：`assets/<sha256>`，引用计数在数据库（见 `course_asset_ref`），删除时先减引用再 GC。
3. **manifest 按版本独立**：`v1.json`、`v2.json` 永不覆盖，回滚 = 把当前指针切回旧版本。
4. **老师之间目录隔离**：`blobs/<teacher_id>/` 前缀即命名空间，服务端按会话校验后才签发下载 URL。

### 4.2 Markdown 文件命名与逻辑路径

逻辑路径（出现在 manifest 与 UI 树中）延续现有约定，便于老师迁移心智：

```
<科目>/<模块>/<文件名>.md          例：高中化学/理论课讲义/3-1 原电池.md
```

物理路径由 `blobs/<teacher_id>/<sha256>.md` 承载，逻辑路径只是 manifest 字段，**移动/改名不影响 `article_id`**（解决 I1）。

### 4.3 发布 manifest 结构

```json
{
  "schemaVersion": 1,
  "teacherId": 42,
  "teacherName": "吴昌骏",
  "subject": "高中化学",
  "version": 7,
  "publishedAt": "2026-10-30T21:40:00+08:00",
  "contentHash": "sha256:9f2c…",          
  "articles": [
    {
      "articleId": 1031,
      "legacyArticleId": 12,
      "title": "3-1 原电池",
      "series": "高中化学",
      "logicalPath": "高中化学/理论课讲义/3-1 原电池.md",
      "contentHash": "sha256:ab12…",
      "answerHash": "sha256:77de…",
      "questionCount": 6,
      "updatedAt": "2026-10-30T20:10:00+08:00",
      "questions": [
        { "questionId": "1", "answer": "B", "autoGrade": true },
        { "questionId": "2", "answer": "增大；正极", "autoGrade": false }
      ]
    }
  ],
  "assets": [
    { "hash": "sha256:cc90…", "path": "assets/cc/cc90….png", "refCount": 3 }
  ]
}
```

字段说明：

| 字段 | 用途 |
| --- | --- |
| `contentHash` | 全量清单哈希，作为"发布版本指纹"；复制与更新比对用 |
| `articles[].contentHash` | 单篇正文哈希，复制记录 `base_content_hash` 存它 |
| `articles[].answerHash` | 答案键哈希，更新时必须与正文同批替换 |
| `articles[].questions[]` | 答案键随发布包一起走，不再依赖库表单独维护 |
| `legacyArticleId` | 兼容存量 `student.permissions` 里的全局整数 ID |

### 4.4 发布前校验（扩展现有 `check-courseware`）

| 规则 | 说明 |
| --- | --- |
| 题干与占位符配对 | 出现 `【题干N】` 就必须有 `【@N】`，反之亦然；`【题干N-M】` 覆盖 N..M |
| 允许缺号 | 题号只作标识，存量删题后不重编号（重编号会打断答案键与错题记录） |
| 允许纯讲义 | 完全不含题目的阅读型课件可直接发布 |
| 作答占位符唯一 | 同一编号不得出现两次 |
| 答案键完整 | 答案键数量 = 题目数量，且 `questionId` 必须存在于正文 |
| 图片引用可解析 | `![[xxx.png]]` 必须在资产清单内 |
| 禁止外链图片 | 或明确标注为外部资源，避免渲染失败 |
| 正文大小上限 | 单篇 ≤ 1 MB，防止误传大文件 |
| 禁止可执行内容 | 渲染层已禁 HTML/脚本；校验层再抓 `<script`、`javascript:` |

---

## 5. 标识与命名空间

### 5.1 `article_id`

- 唯一性范围：**`(teacher_account_id, article_id)`**，不再全局唯一。
- 生成方式：老师首次导入/新建时由后端在本人命名空间内分配 `max(article_id)+1`，起始值 1000（避开存量 1–44，便于识别）。
- 稳定性：`article_id` 一旦分配不再变化，与逻辑路径解耦（改目录、改文件名都不动 ID）。
- 对外暴露：URL 一律带老师维度，例如 `/courseware/teachers/42/articles/1031`。

### 5.2 `question_id` 与答案键

- `question_id` 保留课件内的 `【题干N】` 编号（字符串），复制/导入时**原样保留**。
- 答案键（`answer`、`autoGrade`）随 manifest 的 `questions[]` 走，不单独建表维护；库表仅缓存"当前发布版本"的副本用于快速判分。
- 老师改题后重新发布，答案键随之替换；旧作答记录靠快照解释（见 5.3）。

### 5.3 学习记录快照（关键）

作答、错题入库时冗余保存：

| 列 | 内容 |
| --- | --- |
| `teacher_account_id` | 课件所属老师，定位来源 |
| `release_id` | 当时的发布版本 |
| `stem_snapshot` | 当时的题干全文 |
| `answer_snapshot` | 当时的正确答案 |
| `options_snapshot` | 当时选项（若有） |

渲染错题本时优先使用快照；快照缺失才回落到当前内容。这样老师怎么改题，学员历史都不失配。

### 5.4 与旧全局 ID 的兼容

- 存量 42 篇的 `1–44` **保留为初始老师命名空间内的 `article_id`**，同时写入 `legacyArticleId` 供对照（导出包、旧链接、排查时用）。
- `student.permissions` 里的整数在迁移后仍指向 `(初始老师, 原 article_id)`，不需要重编号；新增授权一律写 `(teacher_id, article_id)`。
- 只有"复制到其他老师命名空间"的内容才分配新 ID，新旧对应关系由 `course_copy`（`local_article_id` ↔ `source_article_id`）记录。
- 迁移完成后 `legacy_article_id` 保留只读，不再参与业务判断。

---

## 6. 数据模型

### 6.1 新增表（DDL 草案）

```sql
-- 共享条目：老师必须主动分享，别人才能看到；范围可以是整个科目，也可以是单篇
CREATE TABLE IF NOT EXISTS public.course_share (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    owner_teacher_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    scope_type text NOT NULL CHECK (scope_type IN ('subject', 'article')),
    subject text NOT NULL,
    article_id bigint,                      -- scope_type='article' 时必填
    allow_preview boolean NOT NULL DEFAULT true,
    allow_copy boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,                 -- 非空表示已撤回
    CONSTRAINT course_share_article_requires_id
        CHECK ((scope_type = 'article' AND article_id IS NOT NULL)
            OR (scope_type = 'subject' AND article_id IS NULL))
);
-- 同一科目的整科共享只能有一条有效记录；单篇共享按 article 去重
CREATE UNIQUE INDEX IF NOT EXISTS course_share_subject_active_unique
    ON public.course_share (owner_teacher_account_id, subject)
    WHERE scope_type = 'subject' AND revoked_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS course_share_article_active_unique
    ON public.course_share (owner_teacher_account_id, subject, article_id)
    WHERE scope_type = 'article' AND revoked_at IS NULL;

-- 整科获取的订阅关系：用于报告"来源新增了课件"
-- 注意：本表引用 course_release，建表顺序需排在其后（下方按逻辑分组展示）
CREATE TABLE IF NOT EXISTS public.course_subscription (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    subscriber_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    source_teacher_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    source_subject text NOT NULL,
    base_release_id bigint NOT NULL REFERENCES public.course_release (id),
    local_root_path text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'source_unavailable', 'revoked')),
    CONSTRAINT course_subscription_unique
        UNIQUE (subscriber_account_id, source_teacher_account_id, source_subject)
);

-- 在线编辑器的草稿（不进入发布版本）
CREATE TABLE IF NOT EXISTS public.course_draft (
    teacher_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    article_id bigint NOT NULL,
    markdown text NOT NULL,
    layout_map jsonb NOT NULL DEFAULT '{}'::jsonb,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (teacher_account_id, article_id)
);

-- 一次发布 = 一个不可变版本
CREATE TABLE IF NOT EXISTS public.course_release (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    teacher_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    subject text NOT NULL,
    version integer NOT NULL,
    manifest_path text NOT NULL,          -- manifests/<teacher>/<subject>/v<n>.json
    manifest_hash text NOT NULL,
    published_at timestamptz NOT NULL DEFAULT now(),
    status text NOT NULL DEFAULT 'published'
        CHECK (status IN ('published', 'archived', 'revoked')),
    CONSTRAINT course_release_teacher_subject_version_unique
        UNIQUE (teacher_account_id, subject, version)
);

-- 当前生效版本指针（避免每次扫最大版本号）
CREATE TABLE IF NOT EXISTS public.course_release_head (
    teacher_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    subject text NOT NULL,
    release_id bigint NOT NULL REFERENCES public.course_release (id),
    PRIMARY KEY (teacher_account_id, subject)
);

-- 课件条目（一次发布内的一篇）
CREATE TABLE IF NOT EXISTS public.course_article (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    release_id bigint NOT NULL REFERENCES public.course_release (id) ON DELETE CASCADE,
    teacher_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    article_id bigint NOT NULL,
    legacy_article_id bigint,
    subject text NOT NULL,
    title text NOT NULL,
    series text NOT NULL,
    logical_path text NOT NULL,
    content_hash text NOT NULL,
    answer_hash text NOT NULL,
    question_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT course_article_release_article_unique UNIQUE (release_id, article_id),
    CONSTRAINT course_article_teacher_article_unique UNIQUE (teacher_account_id, article_id)
);
CREATE INDEX IF NOT EXISTS course_article_content_hash_idx
    ON public.course_article (content_hash);

-- 复制关系（"获取资源"留下的上游指针）
CREATE TABLE IF NOT EXISTS public.course_copy (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    owner_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    local_article_id bigint NOT NULL,
    source_teacher_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    source_article_id bigint NOT NULL,
    source_release_id bigint NOT NULL REFERENCES public.course_release (id),
    base_content_hash text NOT NULL,
    base_answer_hash text NOT NULL,
    source_logical_path text NOT NULL,     -- 获取时来源的逻辑路径（用于报告改名）
    local_logical_path text NOT NULL,      -- 接收方自定义的路径（可改名/改目录）
    local_title text NOT NULL,             -- 接收方自定义的标题
    acquired_via text NOT NULL DEFAULT 'copy'
        CHECK (acquired_via IN ('copy', 'import', 'download')),
    acquired_at timestamptz NOT NULL DEFAULT now(),
    dismissed_hash text,                  -- 已忽略的来源版本
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'source_unavailable', 'source_revoked')),
    CONSTRAINT course_copy_unique
        UNIQUE (owner_account_id, source_teacher_account_id, source_article_id)
);

-- 资产引用计数（图片去重与 GC）
CREATE TABLE IF NOT EXISTS public.course_asset_ref (
    asset_hash text NOT NULL,
    owner_teacher_account_id bigint NOT NULL,
    article_id bigint NOT NULL,
    ref_count integer NOT NULL DEFAULT 1,
    PRIMARY KEY (asset_hash, owner_teacher_account_id, article_id)
);

-- 操作审计（复制/更新/导入/导出）
CREATE TABLE IF NOT EXISTS public.course_operation_log (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    actor_account_id bigint NOT NULL,
    action text NOT NULL
        CHECK (action IN ('publish', 'copy', 'apply_update', 'dismiss_update', 'import', 'export',
                          'delete', 'share', 'revoke_share', 'rename', 'git_push')),
    teacher_account_id bigint NOT NULL,
    article_id bigint,
    detail jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
```

### 6.2 改造表

```sql
-- 课件目录：加老师维度。分步迁移（见 11.2），最终主键 (teacher_account_id, article_id)
ALTER TABLE public.courseware_catalog
    ADD COLUMN IF NOT EXISTS teacher_account_id bigint,
    ADD COLUMN IF NOT EXISTS legacy_article_id bigint,
    ADD COLUMN IF NOT EXISTS content_hash text;

-- 答案键：缓存当前版本用，加老师/版本维度；唯一键随之调整
ALTER TABLE public.article_answer_keys
    ADD COLUMN IF NOT EXISTS teacher_account_id bigint,
    ADD COLUMN IF NOT EXISTS release_id bigint;

-- 作答：加老师、版本与快照列
ALTER TABLE public.article_question_submissions
    ADD COLUMN IF NOT EXISTS teacher_account_id bigint,
    ADD COLUMN IF NOT EXISTS release_id bigint,
    ADD COLUMN IF NOT EXISTS stem_snapshot text,
    ADD COLUMN IF NOT EXISTS answer_snapshot text;

-- 错题：同上
ALTER TABLE public.wrong_questions
    ADD COLUMN IF NOT EXISTS teacher_account_id bigint,
    ADD COLUMN IF NOT EXISTS release_id bigint,
    ADD COLUMN IF NOT EXISTS stem_snapshot text,
    ADD COLUMN IF NOT EXISTS subject text;
```

> 唯一键同步调整：`article_answer_keys` 由 `(blog_id, question_id)` 改为 `(teacher_account_id, blog_id, question_id)`；`wrong_questions` 由 `(student_id, source_blog_id, source_question_id)` 扩展为 `(student_id, teacher_account_id, source_blog_id, source_question_id)`，避免学员换老师后 ID 同名冲突。

### 6.3 停用对象

| 对象 | 处理 |
| --- | --- |
| `admin_subject_claim` | 停止写入，保留只读用于迁移对照；新代码不再引用 |
| `admin_courseware_claim` | 同上（API 标记 deprecated，前端入口移除） |
| `admin_courseware_exclusion` / `admin_courseware_abandonment` | 仅与认领模型有关，随认领一起退役，表保留不删（数据留存） |
| `public/data/id_map.json`、`articles.json` | 生产不再读取，保留为 dev 种子 |
| `scripts/sync-articles-data.js` | 改为"生成种子 manifest"工具，不再参与构建 |

### 6.4 关系图

```
account(老师) ──< course_release ──< course_article ── course_copy(副本 → 上游指针)
      │                 │                 │
      │                 │                 └── course_asset_ref(图片引用)
      │                 └── course_release_head(当前版本指针)
      └──< student(学员, claimed_by) ──< submissions / wrong_questions（带 release + 快照）
```

---

## 7. 核心流程

### 7.1 老师发布

1. 老师在编辑端（网页或 Git 导入）提交一批 md。
2. 后端跑校验（4.4 规则），失败则返回逐条错误。
3. 通过后计算 `contentHash` / `answerHash`，写入对象存储（blobs 内容寻址，assets 去重）。
4. 生成 `manifests/<teacher>/<subject>/v<n>.json`，插入 `course_release`，更新 `course_release_head`。
5. 记录 `course_operation_log(action='publish')`。
6. 学员端下次拉 manifest 即生效；无需重新构建/部署应用。

### 7.2 学员读取（多老师）

```
学员打开首页
  → GET /courseware/manifests
     返回：该学员所属全部老师（student_teacher）的清单数组
           [{ teacherId, teacherName, subject, version, etag, articles[] }, ...]
  → 前端按"老师 → 科目"分组展示，各自缓存（version/etag 变化才重拉）
  → 点开某篇：GET /courseware/articles/{teacherId}/{articleId}/content
  → 后端校验：该学员在 student_teacher 中有该老师，且被授予 (teacherId, articleId)
  → 返回 md（或 302 到短时效签名 URL）
```

要点：

- 因为 `article_id` 只在老师命名空间内唯一，**所有学员侧接口都必须带 `teacherId`**（`/articles/{articleId}` 这类旧路径要加老师维度，属于破坏性变更）。
- 同一学员在不同老师下的学习记录（作答、错题、进度）各成一套，不合并。

### 7.3 一键复制（"获取资源"）

> 本节为单篇复制的最小流程；整科获取、改名改路径、新增/删除/改名汇总等完整口径见 18.2，以 18.2 为准。

```
老师 A 在资源目录里点"复制到我的课件"
  → POST /admin/courseware/copies { sourceTeacherId: B, sourceArticleId: 2031 }
  → 服务端：
      1. 校验 B 的 allow_copy = true
      2. 读 B 当前 release 与 course_article（拿到 contentHash、answerHash、逻辑路径）
      3. 在 A 的命名空间分配新 article_id（max+1）
      4. blobs 直接服务端拷贝（内容寻址，同 hash 则零拷贝）
      5. assets 增加 A 的引用计数
      6. 写 course_article（A 的 release 待下次发布时纳入）
      7. 写 course_copy（base_content_hash = B 的当前 hash）
  → 返回 { localArticleId, copyId, sourceVersion }
```

要点：

- **幂等**：唯一约束 `(owner, source_teacher, source_article)`，重复点击返回已有 `copyId`，前端提示"已复制"。
- **原子性**：复制在单个事务内完成；blob 写失败则整体回滚，不留半个课件。
- **答案键同批**：`answer_hash` 一并记录，后续更新时判断"答案是否也变了"。
- **可改名**：复制后逻辑路径可改（不影响 article_id 与来源追踪）。

### 7.4 更新检测

| 场景 | 判定 |
| --- | --- |
| 来源当前 hash = `base_content_hash` | 无更新 |
| 不相等，且 ≠ `dismissed_hash` | 显示"有可更新资源" |
| 不相等，但 = `dismissed_hash` | 不提示（老师已选择忽略该版本） |
| 来源被删除/关闭共享 | `status='source_unavailable'`，不再提示更新，本地内容可用 |

实现方式：

- 复制列表页一次性批量比对（`course_copy` × `course_article` join，几十条记录，成本可忽略）。
- 也可后端定时扫描缓存"待更新数量"，用于首页角标。
- 不做源端推送（避免发布时耦合接收方）。

### 7.5 应用更新（v1 整篇粒度）

```
老师点"查看更新"
  → GET /admin/courseware/copies/{copyId}/updates
  → 返回：来源版本、正文 diff（行级）、答案变更列表（哪些题答案变了）、本地是否已改动
  → 选择：
      A. 以来源覆盖  → 先把本地当前 md 备份到 backups/<teacher>/<article>/<hash>.md
                       再写入来源 blob，更新 course_copy.base_*，答案键同批替换
      B. 保留本地    → 记录 dismissed_hash = 来源当前 hash，角标消失
      C. 稍后处理    → 不变
```

- **禁止自动合并**：本地若已有改动，覆盖前必须提示"本地改动将存入备份"，并在更新记录里标注"覆盖了 N 处本地修改"。
- **答案键必须与正文同批**：不允许只换正文不换答案；若来源答案键变了，弹窗列明"3 题答案变更"。
- 更新后若学员历史记录指向旧版本，靠快照展示（5.3），不受影响。

### 7.6 下载 / 导入

| 动作 | 行为 |
| --- | --- |
| 下载（导出） | `GET /admin/courseware/export?teacherId=` 生成 zip：`manifest.json` + `articles/<逻辑路径>.md` + `assets/<hash>.<ext>` + `answers.json`，7 天后自动清理 |
| 导入 | `POST /admin/courseware/import`（multipart zip）：校验 manifest schema → 逐篇分配新 `article_id` → 写入本人命名空间 → 生成导入报告（成功/跳过/错误） |
| 语义 | **导入即副本，不与任何来源同步**；UI 明确提示"导入内容已独立，不再接收更新" |
| 冲突 | 同路径已存在 → 默认跳过并报告，老师可选"覆盖"（走备份） |

### 7.7 来源删除 / 关闭共享

- 来源老师删除课件：`course_copy.status='source_unavailable'`；副本照常可用，列表显示"来源已删除"。
- 来源关闭 `allow_copy`：不再出现在资源目录，已有副本不受影响，更新提示继续可用（或一并停止，建议保留，便于内容质量同步）。
- 接收方删除副本：只删本地 `course_article` 与 blob 引用，`course_copy` 一并删除，不影响来源。

### 7.8 老师注销 / 归档

- 账号停用 → 该老师全部 `course_release.status='archived'`，manifest 保留（学员历史仍需可查）。
- 90 天后触发归档导出（zip 落 `exports/archive/`），对象存储内容保留（因学员历史引用），仅回收写权限。

---

## 8. 接口设计

### 8.1 约定

- 前缀：学员侧 `/api/v1/courseware`，老师侧 `/api/v1/admin/courseware`。
- 鉴权：会话 Cookie（沿用现有 `account_session`）；老师侧要求 `role=admin` 且对目标老师有权限。
- 返回：统一 `{ code, message, data }`；错误码见 8.4。
- 幂等：所有写接口带 `Idempotency-Key`（可选），复制接口另有唯一约束兜底。

### 8.2 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/courseware/manifests` | 学员侧取所属全部老师的清单（数组，按老师分组） |
| GET | `/courseware/manifest` | 单老师场景简写（等价于上一接口取唯一元素） |
| GET | `/courseware/articles/{teacherId}/{articleId}/content` | 取正文 md（鉴权后返回或 302 签名 URL） |
| GET | `/admin/courseware/articles` | 我的课件列表（含 `hasUpdate` 标记） |
| POST | `/admin/courseware/articles` | 新建课件（分配 article_id） |
| PUT | `/admin/courseware/articles/{articleId}` | 更新草稿内容（不发布） |
| POST | `/admin/courseware/releases` | 发布当前草稿为一个版本 |
| GET | `/admin/courseware/resources` | 资源目录：列出已主动分享的老师与科目 |
| GET | `/admin/courseware/resources/tree` | 展开某位老师的共享树（保留完整祖先目录，单篇共享只回显路径） |
| GET | `/admin/courseware/resources/articles/{teacherId}/{articleId}/preview` | 预览来源正文（只读，需分享仍有效） |
| POST | `/admin/courseware/shares` | 新建分享（整科 / 单篇，可批量） |
| GET | `/admin/courseware/shares` | 我的分享列表（含有效/已撤回） |
| DELETE | `/admin/courseware/shares/{shareId}` | 撤回分享 |
| POST | `/admin/courseware/copies` | 获取课件（单篇或整科，可指定目标路径与标题） |
| GET | `/admin/courseware/copies` | 我的获取关系列表 |
| GET | `/admin/courseware/updates` | 汇总更新：已更新 / 新增 / 已删除 / 来源改名 |
| GET | `/admin/courseware/copies/{copyId}/updates` | 单篇更新详情，返回 diff、答案变更与预览 |
| POST | `/admin/courseware/copies/{copyId}/updates/apply` | 应用更新（overwrite / keep） |
| POST | `/admin/courseware/copies/{copyId}/updates/dismiss` | 忽略当前来源版本 |
| GET | `/admin/courseware/export` | 导出 zip（整科或选中单篇，保留逻辑路径） |
| POST | `/admin/courseware/import` | 导入 zip（异步任务返回 progress） |
| GET | `/admin/courseware/workspace/manifest` | Git 工作区拉取清单（只含路径与 hash，无正文） |
| POST | `/admin/courseware/workspace/push` | Git 工作区推送变更（上传 blob + 发布） |
| GET | `/admin/courseware/notifications` | 消息列表（更新提醒、分享通知），支持未读筛选 |
| POST | `/admin/courseware/notifications/read` | 标记已读（单条/批量） |

### 8.3 关键请求/响应

**复制**

```http
POST /api/v1/admin/courseware/copies
Content-Type: application/json

{ "sourceTeacherId": 7, "sourceArticleId": 2031 }
```

```json
{
  "code": 0,
  "data": {
    "copyId": 118,
    "localArticleId": 1007,
    "title": "3-1 原电池",
    "sourceVersion": 12,
    "alreadyExists": false
  }
}
```

**检查更新**

```json
{
  "code": 0,
  "data": {
    "hasUpdate": true,
    "sourceVersion": 13,
    "baseHash": "sha256:ab12…",
    "sourceHash": "sha256:ef34…",
    "localModified": true,
    "answerChanges": [
      { "questionId": "2", "from": "增大", "to": "增大；正极" }
    ],
    "diff": "--- base\n+++ source\n@@ -12,3 +12,4 @@\n…"
  }
}
```

**应用更新**

```json
{ "mode": "overwrite", "expectedSourceHash": "sha256:ef34…" }
```

```json
{
  "code": 0,
  "data": { "applied": true, "backupPath": "backups/7/1007/sha256:ab12….md", "answerKeyUpdated": true }
}
```

### 8.4 错误码

| 码 | 含义 | 处理建议 |
| --- | --- | --- |
| `COURSEWARE_SOURCE_FORBIDDEN` | 来源未开放复制 | 提示联系对方开启 |
| `COURSEWARE_SOURCE_GONE` | 来源已删除/停用 | 标记 `source_unavailable` |
| `COURSEWARE_ALREADY_COPIED` | 已复制过 | 返回已有 copyId，前端提示 |
| `COURSEWARE_HASH_CONFLICT` | 应用更新时来源已再次变化 | 要求重新拉取最新 diff |
| `COURSEWARE_VALIDATION_FAILED` | 发布校验失败 | 返回逐条错误（缺答案、编号断号等） |
| `COURSEWARE_QUOTA_EXCEEDED` | 超过存储配额 | 提示清理或扩容 |

### 8.5 并发与一致性
- 复制：单事务；blob 已存在则复用（内容寻址天然幂等）。
- 更新应用：`expectedSourceHash` 做乐观锁，防"A 看到旧 diff、B 已发布新版"。
- 发布：同一老师同一科目串行化（行锁 `course_release_head`）。

### 8.6 P1 已实现（2026-10-30）

| 接口 | 状态 | 说明 |
| --- | --- | --- |
| `POST /admin/courseware/shares` | 已实现 | 整科/单篇；同 (老师,科目,单篇) 重复提交刷新权限；只能分享自己已发布的科目 |
| `GET /admin/courseware/shares` | 已实现 | 支持 `includeRevoked` |
| `DELETE /admin/courseware/shares/{shareId}` | 已实现 | 置 `revoked_at`，资源目录与共享树立即消失 |
| `GET /admin/courseware/resources` | 已实现 | 只列他人生效分享；整科按当前版本篇数计 `articleCount` |
| `GET /admin/courseware/resources/tree` | 已实现 | 保留完整 `logicalPath`，逐篇带 `allowCopy` |
| `POST /admin/courseware/copies` | 已实现 | 内容寻址复制 → `course_draft`（未发布）+ `course_copy` 上游指针；重复获取幂等；本地同名路径 409 `courseware_target_exists` |
| `GET /admin/courseware/copies` | 已实现 | 与来源当前 hash 比对给出 `hasUpdate` |
| `GET /admin/courseware/updates` | 已实现 | 汇总：有更新 / 同科目尚未获取的新增 / 来源已下线 |
| `GET /admin/courseware/copies/{copyId}/updates` | 已实现 | 行级 LCS diff、`localModified`、`answerChanged`、来源版本 |
| `POST /admin/courseware/copies/{copyId}/updates/apply` | 已实现 | `overwrite` 先把本地草稿存成备份 blob 再覆盖并推进 `base_*`；`expectedSourceHash` 做乐观锁，冲突 409 |
| `POST /admin/courseware/copies/{copyId}/updates/dismiss` | 已实现 | 写 `dismissed_hash`，之后同一来源版本不再提示 |
| `GET /admin/courseware/resources/articles/{teacherId}/{articleId}/preview` | 已实现 | 需 `allow_preview=true`，返回 markdown 原文 |
| `PATCH /admin/courseware/copies/{copyId}` | 已实现 | 改标题 / 逻辑路径（不动 `article_id` 与来源追踪），同名路径 409 |
| `GET /admin/courseware/notifications` · `POST .../notifications/read` | 已实现 | `course_notification`（V22）；获取、发布新版本、撤回分享都会写提醒，支持未读筛选与全部已读 |

获取接口额外支持 `conflictStrategy=rename`（同名路径自动加 `（2）` 后缀），默认 `fail` 返回 409。

**P1 状态：已完成。** 待实现（P2/P5）：导出导入 zip、Git 工作区、在线编辑器、通知中心独立页面。

### 8.7 P2 已实现（2026-10-31）

| 接口 | 说明 |
| --- | --- |
| `GET /admin/courseware/layouts` · `GET .../layouts/{id}/skeleton` | 布局注册表（V23 `course_layout`，A 讲义 / B 练习 / C 对比表 / D 图文卡片）与新建骨架 |
| `GET/POST /admin/courseware/drafts` · `GET/PUT/DELETE .../drafts/{articleId}` | 草稿 CRUD；草稿不出现在学员端，只有发布才生效 |
| `POST /admin/courseware/drafts/precheck` | 与发布同一套规则（含布局校验）的预检，不阻塞保存 |
| `POST /admin/courseware/drafts/publish` | 当前版本篇目 + 该科目全部草稿 → 一次 release；未知布局或 `{{…}}` 残留直接 400 |
| `GET /admin/courseware/export` | zip：`manifest.json` + `articles/<逻辑路径>.md` + `answers.json`（条目数 ≤500、单篇 ≤2 MB） |
| `POST /admin/courseware/import` | multipart zip → 全部落草稿；同路径默认跳过，`overwrite=true` 覆盖；结果不与任何来源建立 `course_copy` 关系 |

布局指令（`<!-- @layout:A -->`）与 front-matter（`defaultLayout`）都只是 Markdown 注释/元数据，
`questionText.js` 与 `check-courseware` 的解析不受影响；没有指令的存量课件照旧发布。

前端：`/admin/editor`（左草稿列表 + 中源码 + 右预览，插入布局/题目、校验、保存、发布、导出导入），
`/admin/sharing`（P1）。

### 8.8 P2 模板库与大纲（2026-10-31）

| 能力 | 实现 |
| --- | --- |
| 模板库 | `course_template`（V24）：`subject IS NULL` 为全科通用；内置新授课 / 习题课 / 复习课 / 专题课四套骨架；`GET/POST /admin/courseware/templates`、`GET/DELETE .../templates/{id}` |
| 新建走模板 | 编辑器选模板 → 取正文作为新草稿；不选则按布局生成骨架 |
| 存为模板 | 编辑器「存为模板」把当前正文落库（同科目/类型/名字覆盖） |
| 大纲 | 编辑器左侧按 `# ` 切分 section，显示标题与布局标签，支持上移/下移（连同布局指令一起移动） |

设计稿原本的 `templates/<科目>/<类型>.md` 落库实现的原因：生产容器 `read_only` 且不含仓库文件，
运行时读不到模板文件；落库后模板与布局注册表同源，可在线维护。

**P2 剩余**：真正的所见即所得、可拖拽排序（当前是上移/下移按钮）。

### 8.9 P3 Git 工作区（2026-10-31）

仓库里只有 Markdown，正文与版本仍由服务端托管：

| 接口 | 说明 |
| --- | --- |
| `GET /admin/courseware/workspace/manifest?subject=` | 当前版本每篇的 `logicalPath` / `contentHash` / `questionCount`，**不含正文** |
| `GET /admin/courseware/workspace/files?subject=` | `逻辑路径 → markdown`，供 CLI 落盘 |
| `POST /admin/courseware/workspace/push` | `files` 覆盖、`deletedPaths` 移除、其余保持；`baseVersion` 乐观锁（过期 409 `courseware_release_conflict`）；一次推送即一个 release |

CLI：`tutor-nest-vue/scripts/courseware-cli.mjs`

```powershell
$env:TUTOR_SESSION='<cookie 值>'; $env:TUTOR_CSRF='<token>'
node tutor-nest-vue/scripts/courseware-cli.mjs pull   --subject 高中化学 --dir ./courseware
node tutor-nest-vue/scripts/courseware-cli.mjs status --subject 高中化学 --dir ./courseware
node tutor-nest-vue/scripts/courseware-cli.mjs push   --subject 高中化学 --dir ./courseware --dry-run
```

CLI 行为要点：

- `pull` 只写「缺失或 hash 变了且本地未改动」的文件；本地改过但未 push 的文件**保留并提示冲突**，不静默覆盖；
- 状态存在 `<dir>/.courseware-state.json`（base 版本 + 每个文件的 hash）；
- `push` 先比对服务端版本，本地 base 过期直接失败（要求先 pull）；`--dry-run` 只打印计划；
- 推送走与在线编辑器完全相同的发布路径，因此布局校验、manifest、审计日志口径一致。

### 8.10 P4 学习记录快照（2026-10-31）

V21 已给 `article_question_submissions` / `wrong_questions` 加了 `stem_snapshot`、`answer_snapshot`、
`teacher_account_id`、`release_id`、`subject` 列，但一直是空的 —— 错题本题干仍靠前端按课件原文现场解析。
P4 把写入补齐：

| 位置 | 行为 |
| --- | --- |
| `CoursewareStemExtractor` | 按格式规范抽题干：`【题干N】` 到下一个标记/`---`；`【题干N-M】` 共享材料 + 各自小问；去 `>`/`#`/`**`；≤400 字按行截断 |
| `CoursewareSnapshotResolver` | `legacy_article_id` 优先、回落 `article_id`，解析到当前版本的 `teacherAccountId/releaseId/subject/markdown` |
| 作答写入 | `article_question_submissions` 同批写 `stem_snapshot`/`answer_snapshot`；冲突更新用 `COALESCE` 保留已有快照 |
| 错题写入 | `wrong_questions` 同批写 `stem_snapshot`/`subject` |
| 错题本读取 | 响应新增 `stemSnapshot`/`subject`；前端优先用快照，缺失才回退解析原文 |

效果：**课件改版不再改变历史错题里的题干**（`CoursewareSnapshotHttpContractTest` 验证
"提交 → 错题带快照 → 发布全新题干 → 错题里的题干不变"）。解析不到托管数据时写空值，不阻塞学员作答。

---

## 9. 前端改造

### 9.1 数据加载链路

| 当前 | 改造后 |
| --- | --- |
| `fetch('data/articles.json')` → `blogStore.blogData` | `fetch('/api/v1/courseware/manifest')` → 同一 store 结构，字段兼容（`id/title/series/path` 保留） |
| `fetch('articles/' + blog.path)` | `fetch('/api/v1/courseware/articles/{teacherId}/{articleId}/content')` |
| 全局学科卡片（首页） | 改为"老师 → 科目 → 课件"，按 `student_teacher` 分组（多老师场景见 19.7） |
| 后台认领页 | 改为"我的课件 + 资源目录 + 获取" |

### 9.2 逐文件改动

| 文件 | 改动 |
| --- | --- |
| `src/stores/blogStore.js` | 数据源换成 API；保留 `blogData` 结构，避免大范围改动 |
| `src/stores/authStore.js` | 标题映射改从 manifest 取 |
| `src/views/ArticleDetailView.vue` | 正文取 API；渲染前做 HTML 清洗（禁 `<script>`） |
| `src/composables/useWrongQuestionsResolve.js` | `loadArticle` 改 API；优先使用后端返回的 `stem_snapshot`，缺失才解析 md |
| `src/views/HomeView.vue` | 封面映射保留；学科卡片数据源改为本人 manifest |
| `src/views/CategoryView.vue` | 目录树由 manifest 的 `series/logicalPath` 生成，不再扫静态文件 |
| `src/views/admin/AdminContentView.vue` | 移除认领/排除/放弃；新增"资源目录"入口与"有更新"角标 |
| `src/components/admin/PermissionPanel.vue` | 授权改为 `(teacherId, articleId)`，UI 增加老师维度 |
| `src/components/admin/AnswerPanel.vue` / `ReviewPanel.vue` | 读取答案改为从当前 release；写入走新接口 |
| `src/gateways/coursewareGateway.js` | 新增复制/更新/导出导入 API，认领接口移除 |
| `src/router/index.js` | 移除 `/chemistry`、`/english` 之类的兼容重定向（已由学科改名替代），新增 `/resources` 路由 |

### 9.3 新组件

| 组件 | 作用 |
| --- | --- |
| `ResourceLibrary.vue` | 资源目录：只列已主动分享的老师与科目 |
| `ResourceTree.vue` | 共享资源树：保留完整祖先路径，支持展开单篇、多选获取（见 18.1） |
| `AcquireDialog.vue` | 获取对话框：整科/单篇、目标路径、改名、冲突策略（见 18.2） |
| `CopyUpdateBadge.vue` | "有可更新资源"角标（列表 + 详情） |
| `UpdateDigestPanel.vue` | 更新汇总：已更新 / 新增 / 已删除 / 来源改名，逐条预览 |
| `UpdateDiffDialog.vue` | 单篇差异预览、答案变更、覆盖/保留/稍后 |
| `ShareSettings.vue` | 分享范围（整科/单篇）与 `allow_preview` / `allow_copy` 开关 |
| `ArticleEditor.vue` | 在线编辑器：大纲、布局插入、块插入、模板选择、预览（见 18.3） |
| `ImportProgress.vue` | 导入进度与报告 |

### 9.4 UI 流程

1. 老师在"我的课件"里对整科或单篇点"分享"，设置预览/获取权限。
2. 其他老师进入"资源目录"，只看到已分享的老师与科目；展开树时保留完整路径（如 `高中英语/课后练习/课后练习-1`）。
3. 选择整科或单篇 → 获取对话框里改标题、改目标路径 → 确认后立即出现在"我的课件"，显示"来源：A 老师 · 原路径"。
4. 来源更新后，"我的课件"出现"有可更新资源"，汇总面板区分"更新/新增/删除/改名"，可逐条预览。
5. 单条点开 → 差异弹窗 → 覆盖（自动备份）/ 保留 / 稍后。

### 9.5 P0 前端实现状态（2026-10-30）

已落地（双读，失败自动回退静态目录，保证 GitHub Pages 与后端未就绪时可用）：

| 位置 | 实现 |
| --- | --- |
| `src/gateways/backendClient.js` | 新增 `requestText`：正文按 `text/markdown` 读取，非 2xx 抛 `BackendGatewayError` |
| `src/gateways/coursewareGateway.js` | 新增 `fetchManifests()` 与 `fetchArticleContent(teacherId, articleId)` |
| `src/utils/coursewareManifest.js` | `flattenManifests()`：清单 → `{id,title,series,date,path,teacherId,teacherArticleId}`，跨老师撞号退回 `articleId` |
| `src/stores/blogStore.js` | 先取 `/courseware/manifests`，为空或失败时回退 `data/articles.json`；`source` 暴露当前来源 |
| `src/utils/articleContent.js` | `loadArticleMarkdown(blog)`：托管接口优先，403/404/网络失败回退静态文件 |
| `ArticleDetailView` / `useWrongQuestionsResolve` / `admin/AnswerPanel` | 正文读取改走上述共享函数（题干解析逻辑不变） |

尚未落地（属 P1/P2）：学科卡片按 `student_teacher` 分组、`stem_snapshot` 优先、资源目录/获取/更新汇总、在线编辑器。
`authStore.loadAllArticleIds()` 仍以静态索引为准（管理员与特殊测试学员的全量放行集合），随 P4 换主键一并改造。

---

## 10. 后端改造

### 10.1 模块划分

```
backend/src/main/java/com/tutorbase/courseware/
├── storage/            CoursewareStorage 接口 + SupabaseStorage 实现
├── publish/            ReleaseService：校验、hash、写 manifest
├── article/            ArticleService：CRUD、命名空间 ID 分配
├── copy/               CopyService：复制、更新检查、应用更新
├── transfer/           ExportService / ImportService（zip）
├── share/              ShareService（allow_browse/allow_copy）
└── web/                Controller + DTO
```

### 10.2 存储抽象

```java
public interface CoursewareStorage {
    String putBlob(long teacherId, byte[] content);        // 返回 sha256
    byte[] getBlob(long teacherId, String hash);
    void copyBlob(long fromTeacherId, long toTeacherId, String hash);
    String signUrl(String path, Duration ttl);
    void putManifest(long teacherId, String subject, int version, byte[] json);
    byte[] getManifest(long teacherId, String subject, int version);
}
```

- 本地开发用 `LocalFileStorage`（落 `var/courseware/`），生产用 `SupabaseStorage`。
- 所有读写走该接口，换云厂商只改实现。

### 10.3 服务要点

| 服务 | 要点 |
| --- | --- |
| `PublishService` | 校验 → 计算 hash → 写 blob/assets → 写 release → 切 head；失败全回滚 |
| `CopyService` | 读源 release 快照 → 分配本地 ID → 服务端拷贝 → 写 `course_copy`；唯一约束兜底幂等 |
| `UpdateService` | 比对 `base_content_hash` 与源当前 hash；生成 diff（行级）；应用时校验 `expectedSourceHash`；覆盖前写备份 |
| `ExportService` | 打包当前 head 的 manifest + 所有 blob + assets；生成 zip 到 `exports/` |
| `ImportService` | 解析并校验 zip → 分配新 ID → 写入；事务分批提交，失败生成报告不回滚已成功项（或整体回滚，二选一，建议整体回滚 + 报告） |
| `QuotaService` | 每位老师默认 500 MB（免费版阶段，见 19.1 / 19.8），超限拒绝发布/复制；平台总量水位按 19.8 监控 |

### 10.4 契约与测试

- 更新 `backend/api/openapi.yaml`：新增约 15 个接口（见 8.2）；旧 `courseware-claims` 标记 `deprecated: true`。
- 契约测试新增：`CoursewareCopyHttpContractTest`、`CoursewareUpdateHttpContractTest`、`CoursewareExportImportHttpContractTest`。
- 单测覆盖：hash 计算、manifest 生成、幂等复制、并发覆盖冲突、快照写入。

---

## 11. 数据迁移

### 11.1 迁移步骤

| 步骤 | 动作 | 校验 |
| --- | --- | --- |
| 1 | 全库备份 + 对象存储建桶 | 备份可恢复 |
| 2 | 指定"初始老师"（当前唯一有课件的管理员），把 42 篇归入其命名空间 | 篇数 = 42，ID 集合 = 1–44 |
| 3 | 生成 v1 manifest（`legacy_article_id` 写入） | manifest 篇数、hash 与磁盘一致 |

> 步骤 2–4 由 `CoursewareSeeder` 承担：`TUTOR_COURSEWARE_SEED_ENABLED=true` 启动时读取
> `public/data/articles.json` 索引与 `public/articles` 文件、按 `blog_id` 取 `article_answer_keys`，
> 逐科目发布初始老师的 v1 release（已有版本默认跳过，`TUTOR_COURSEWARE_SEED_OVERWRITE=true` 可重发）。
| 4 | 上传正文与图片到对象存储 | 124 条图片引用全部可解析 |
| 5 | 改造 `courseware_catalog`（加列 → 回填 → 换主键） | 行数不变，无重复 |
| 6 | 迁移 `article_answer_keys`：按 `(blog_id, question_id)` 关联到 `course_article` | 答案条数一致 |
| 7 | 为 `submissions` / `wrong_questions` 回填 `teacher_account_id`、`release_id`、快照 | 抽检 20 条题干与原 md 一致 |
| 8 | 给 `student.permissions` 补老师维度（ID 不变，按学员 `claimed_by` 归属） | 授权数量不变，旧 ID 仍可解析 |
| 9 | 建 `student_teacher`，按 `claimed_by` 回填为"主老师"；`claimed_by` 仅保留作默认老师 | 关联数 ≥ 学员数；无学员丢失 |
| 10 | 下线认领 UI/接口，前端切 manifest API | 页面无认领入口 |
| 11 | 停止构建 `public/articles` 进 `dist` | `dist/articles` 为空或移除 |

### 11.2 `V21__teacher_scoped_courseware_columns.sql` 要点

```sql
-- 1) 加列（可空，便于回填）
ALTER TABLE public.courseware_catalog
    ADD COLUMN IF NOT EXISTS teacher_account_id bigint,
    ADD COLUMN IF NOT EXISTS legacy_article_id bigint;

UPDATE public.courseware_catalog
SET legacy_article_id = article_id
WHERE legacy_article_id IS NULL;

-- 2) 回填 teacher_account_id：取该文章当前认领人；无人认领则落初始老师
UPDATE public.courseware_catalog AS c
SET teacher_account_id = COALESCE(
    (SELECT MIN(cl.admin_account_id)
       FROM public.admin_courseware_claim cl
      WHERE cl.article_id = c.article_id),
    (SELECT MIN(s.admin_account_id)
       FROM public.admin_subject_claim s
      WHERE s.subject = c.subject),
    /* :initial_teacher_id —— 执行前替换为初始老师账号 ID 的字面量，例如 7 */
    7
)
WHERE c.teacher_account_id IS NULL;

-- 3) 换主键（本段属于 V22，需在低峰执行）
ALTER TABLE public.courseware_catalog DROP CONSTRAINT IF EXISTS courseware_catalog_pkey;
ALTER TABLE public.courseware_catalog
    ADD CONSTRAINT courseware_catalog_pkey PRIMARY KEY (teacher_account_id, article_id);

-- 4) 新表（见 6.1）由 V22 创建；此处略
```

注意：

- `admin_courseware_claim.article_id` 有外键指向 `courseware_catalog(article_id)`，换主键前需先调整该外键（改为引用 `legacy_article_id` 或先删约束）。
- 建议把这次迁移拆成两个 Flyway 脚本：`V21` 加列/回填，`V22` 换主键/建新表，便于失败回退（Flyway 版本号只允许数字与点/下划线，不要用 `V21a` 这类字母后缀）。
- 同步提供 `database/reconciliation/2026-11-xx_teacher_scoped_courseware.sql`（幂等版），与现有仓库惯例一致。

### 11.3 存量数据映射

| 存量 | 迁移后 |
| --- | --- |
| `article_id` 1–44 | **保留原 ID**，只把唯一性范围改为 `(teacher_id, article_id)`；复制/导入/新建的内容在该老师空间内从 1000 起分配 |
| `student.permissions` 整数数组 | 补 `teacher_id` 维度（旧 ID 不变）；新授权建议存独立表 `student_article_grant(teacher_id, article_id)`，`permissions` 逐步只读 |
| 答案键 `(blog_id, question_id)` | `(teacher_id, article_id, question_id)`，并写入 v1 manifest |
| 错题 `source_blog_id` | 加 `teacher_account_id`；`source_question_id` 不变 |
| `admin_subject_claim` | 只读留档；新的"科目展示"可由 `course_article.subject` 聚合 |

> 建议：**保留原 ID 1–44**，只把唯一性范围改为 `(teacher, article_id)`，这样 `permissions`、答案键、错题都只需补老师维度，不必重编号。只有跨老师复制/导入时才在该老师空间内分配新 ID（从 1000 起），新旧对应关系记在 `course_copy`。

### 11.4 回滚与备份

- 迁移前：`pg_dump` 全库 + 对象存储清单（hash 列表）。
- 迁移脚本幂等，可重复执行。
- 应用侧保留双读开关（`COURSEWARE_SOURCE=static|storage`），出问题可切回静态索引。
- 内容层：blob 内容寻址，不会因迁移丢失；旧 `public/articles` 在一个发布周期内保留在仓库中作为兜底。

---

## 12. 权限与安全

| 风险 | 措施 |
| --- | --- |
| Markdown 公开可读 | 正文走鉴权 API 或短时效签名 URL（≤10 分钟）；桶默认私有。图片可放独立"公开读前缀/公开桶"以便 CDN 缓存（不含题干答案） |
| 跨老师越权访问 | 所有接口按 `(teacherId, articleId)` 校验；学员可读其 `student_teacher` 名下、且被授权的文章 |
| 路径穿越 | 逻辑路径入库前规范化，拒绝 `..`、绝对路径、反斜杠 |
| XSS / 脚本注入 | 渲染层禁 HTML；服务端发布校验抓 `<script`、`onerror=`、`javascript:` |
| 图片外链 | 默认禁止；确有需要则标记并加 CSP |
| 复制滥用 | `allow_copy` 开关 + 每老师配额 + 操作审计 |
| 越权导出 | 导出仅限本人命名空间；管理端导出他人需显式授权 |
| 数据泄露 | 对象存储密钥仅后端持有，前端只拿签名 URL |

---

## 13. 运维

| 项目 | 方案 |
| --- | --- |
| 发布流水线 | 课件仓库（若使用 Git）CI 跑 `check-courseware` + 布局注册表校验 + manifest 校验，再由 CLI/后端 API 发布 |
| 备份 | 对象存储开启版本化；每周 `pg_dump`；每老师可自助导出 zip |
| 监控 | 发布失败率、复制失败率、存储用量、manifest 404、签名 URL 过期率 |
| 归档 | 老师停用 → release 归档 → 90 天后打包归档，内容保留供学员历史查询 |
| 成本 | 正文与图片去重后，42 篇约几十 MB；按老师数量线性增长，2 GB/人 配额足够 |
| 清理 | 导出包 7 天过期；`backups/` 保留 30 天；孤儿 blob 月度 GC |

### 13.1 存储开关与上线顺序

后端以 `tutor.courseware.source` 控制是否对外供数：

| 取值 | 行为 | 适用 |
| --- | --- | --- |
| `static` | `/courseware/manifests` 返回空清单、正文返回 404、发布接口 400；前端自动回退 `data/articles.json` 与静态 md | 当前生产默认；尚未准备持久卷/对象存储时 |
| `storage` | 清单与正文走 `course_release` + blob，发布接口可写 | 配置了可写目录或对象存储后 |

生产容器是 `read_only: true`，因此切到 `storage` 前必须：

1. 给 backend 容器挂一个可写持久卷并设置 `TUTOR_COURSEWARE_ROOT`（例如 `/srv/tutor/courseware:/app/var/courseware`）；
2. 在能读到该卷的进程里跑一次 `TUTOR_COURSEWARE_SEED_ENABLED=true` 导入存量 42 篇（默认跳过已有科目）；
3. 再设置 `TUTOR_COURSEWARE_SOURCE=storage` 并重启，前端随即切到托管清单。

直接切 `storage` 而没有卷时，正文会 404（前端回退静态），不影响可用性但会多一次请求与一条告警日志。

---

## 14. 风险与取舍

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 同一科目多种版本 | 学员体验不一致，跨老师不可比 | 产品上明确"每位老师独立"，不提供跨老师排名 |
| 内容重复存储 | 成本上升 | 正文按 hash 去重，图片全局去重 |
| 复制后长期分叉 | 上游改进无法下沉 | 提供更新提示 + 差异预览；不强制同步 |
| 大量老师复制同一篇 | 存储与查询压力 | 内容寻址天然去重；列表分页 |
| 来源删除 | 接收方内容变"孤儿" | 标记 `source_unavailable`，本地仍可用 |
| 迁移期双 ID | 查询混乱 | `legacy_article_id` 过渡，一个版本后清理 |
| 老师不熟 Git | 无法编辑 | 提供网页编辑/上传；Git 仅作可选导入 |
| 存储服务故障 | 课件打不开 | 本地静态种子兜底 + 重试缓存 |

**核心取舍**：牺牲"平台统一课件与横向可比"，换取老师完全自治、零合并冲突、历史可复现。这是用户已确认的产品方向。

---

## 15. 分期计划

| 阶段 | 内容 | 产出 | 估时（单人） | 依赖 |
| --- | --- | --- | --- | --- |
| **P0 基础设施** | 存储抽象、桶与目录、manifest 生成、发布接口、学员取 manifest/正文、鉴权 | 内容可托管、可发布、可读取；42 篇迁入 | 6–8 天 | Supabase Storage 开通 |
| **P1 分享与获取** | `course_share`（整科/单篇，全员可见）、资源树、预览、获取（整科/单篇）、改名改路径、同名冲突询问、更新汇总、消息提醒 | 主动分享 + 获取闭环 | 8–10 天 | P0 |
| **P2 在线编辑器** | 布局注册表（4 种 + 扩展位）、混合布局渲染、模板库、大纲编辑、草稿/发布 | 网页端可独立编辑 | 6–8 天 | P0 |
| **P3 Git 工作区** | 仓库模板、CLI（pull/status/diff/push）、一键脚本、零基础向导、push 协议 | 本地 Git 编辑可用，仓库无正文 | 4–6 天 | P0/P1 |
| **P4 迁移与下线** | V21 迁移、`student_teacher` 多老师改造、权限/答案/错题加维度与快照、认领下线、前端切换 | 旧模型退场，多老师可用 | 7–9 天 | P1/P3 |
| **P5 导出导入与加固** | zip 导出/导入、配额、GC、监控、归档、CI 校验、容量监控 | 可长期运行 | 4–6 天 | P1–P4 |

总计约 6–7 周（单人），可与现有课件改名分支并行推进（改名只影响静态种子与学科标签）。

---

## 16. 验收清单

| # | 验收项 | 判定标准 |
| --- | --- | --- |
| 1 | 存量迁移 | 42 篇全部可读；ID 与旧 `articles.json` 一一对应；124 条图片引用 0 缺失 |
| 2 | 发布 | 老师发布后学员端刷新即可见，无需重新构建应用 |
| 3 | 隔离 | 老师 A 无法读取老师 B 未共享的正文（接口返回 403） |
| 4 | 一键复制 | 点击后 1 秒内出现在"我的课件"，重复点击返回"已复制"，不产生第二份 |
| 5 | ID 稳定 | 复制后重命名目录/文件，`article_id` 不变，权限与错题仍可解析 |
| 6 | 更新提示 | 来源发布新版后，接收方列表出现"有可更新资源"；忽略后不再提示该版本 |
| 7 | 更新应用 | 覆盖前生成备份；答案键与正文同批更新；学员历史记录仍按快照显示 |
| 8 | 快照 | 老师改题干后，旧错题仍显示原题干与当时答案 |
| 9 | 导入导出 | 导出 zip 可被另一老师导入；导入后 article_id 重新分配，来源关系为"无" |
| 10 | 权限 | 学员只能看自己被授权的 `(teacher, article)`；越权返回 403 |
| 11 | 安全 | 桶私有；匿名访问 md 返回 401/403；渲染不执行脚本 |
| 12 | 回归 | 现有 67 项前端单测、46 个 SFC 检查、课件校验全部通过 |
| 13 | 构建 | `dist/` 不再包含课件 md；构建体积下降，索引 hash 不再参与构建 |
| 14 | 契约 | `openapi.yaml` 与实现一致，契约测试通过 |
| 15 | 分享门禁 | 未分享的老师的课件在资源目录中完全不可见；分享后才出现 |
| 16 | 目录完整性 | 单篇共享时资源树显示 `高中英语 / 课后练习 / 课后练习-1` 全路径；不暴露同目录兄弟文件 |
| 17 | 改名改路径 | 获取时可改标题与目标路径；来源路径变更只提示，不覆盖接收方路径 |
| 18 | 增量更新 | 整科订阅能列出"已更新 / 新增 / 已删除 / 来源改名"，且可逐条预览正文与答案差异 |
| 19 | 混合布局 | 一篇文档内第一个 h1 用布局 A、第二个用布局 B，学员端按各自布局渲染，题目解析不受影响 |
| 20 | Git 往返 | `pull → 本地编辑 → push` 成功发布；仓库 `git status` 无正文文件；版本冲突返回 409 |
| 21 | 多老师学员 | 同一学员绑定两位老师，首页出现两组课件；两边的错题、进度互不混淆 |
| 22 | 同名冲突 | 获取时目标路径已存在 → 弹窗询问覆盖/改名/取消；选"不覆盖"时必须改名或改路径才能继续 |
| 23 | 消息提醒 | 来源新增/更新、收到分享时生成站内消息；可标记已读，不重复轰炸 |
| 24 | Git 零基础 | 按向导 3 步完成：下载工作区 → 输入 token → `一键拉取`、`一键推送`，无需命令行知识 |
| 25 | 容量水位 | 免费版使用率可见（DB/存储/流量），超过 80% 触发告警与降级方案提示 |

---

## 17. 决策与待确认问题

### 17.1 已确认（2026-10-30 第三轮）

| # | 问题 | 决定 |
| --- | --- | --- |
| 1 | 老师规模 | 现阶段最多约 10 位，单人维护、单库单桶即可 |
| 2 | 分享可见范围 | 分享后**所有老师可见**，不做指定接收人 |
| 3 | 同名课件冲突 | **询问是否覆盖**；不覆盖则必须改名或改路径后才能继续 |
| 4 | 布局模板数量 | 首批 4 种（A 讲义 / B 练习 / C 对比 / D 图文），注册表留扩展位 |
| 5 | Git 使用 | 每位老师都可用，但多数不熟 → 提供零基础向导与一键脚本（见 19.4） |
| 6 | 撤回共享后 | 只保留本地内容，**不能预览来源新版本**，也不再提示更新 |
| 7 | 消息提醒 | 需要站内消息（更新、新增、分享通知），邮件可后续再加 |
| 8 | 学员与老师关系 | **一个学员可属于多位老师**，课件/记录按老师分组 |
| 9 | Supabase 免费版 | 先继续用；给出容量评估与替代方案，但**暂不迁移**（见 19.8） |

### 17.2 仍需确认（不影响开工，可边做边定）

1. 消息提醒是否只做站内（默认是），邮件通知何时加。
2. 免费版容量告警阈值（建议 80%）与触发后的降级顺序（先压缩图片 → 再迁图片到 R2）。
3. Git token 发放方式（建议平台内自助生成、按老师吊销，不做集中代管）。

---

## 18. 需求增补（2026-10-30 第二轮确认）

> 本章覆盖并修订第 1、6、7、8、9、13、15、16 节中的相关设计，以下内容为最终口径。

### 18.1 分享：必须主动分享，且保留完整目录

**规则**

| 项 | 规则 |
| --- | --- |
| 默认状态 | 不共享。老师必须主动创建分享，其他老师才可见 |
| 分享范围 | ① 整科：动态跟随该科目当前发布版本，科目内新增/修改的课件自动共享；② 单篇：只共享指定 `article_id`，后续该篇更新仍可见 |
| 可见内容 | 仅共享范围内的课件；祖先目录以"路径上下文"形式呈现，不暴露未共享的兄弟文件 |
| 权限 | 每条分享独立控制 `allow_preview`（能否预览正文）与 `allow_copy`（能否获取/下载） |
| 撤回 | `revoked_at` 置位后立即从资源目录消失；已获取的副本保留，状态改为 `source_revoked` |
| 多老师 | 同一篇可被多位老师分别获取，各自独立副本 |

**资源树构造（后端）**

1. 取该老师的有效分享记录，解析其**当前 head manifest**（整科取全部文章，单篇取指定文章），而不是分享时的快照——这样"更新自动共享"天然成立。
2. 用 `logicalPath` 按 `/` 切段建树；缺失的祖先节点补为 `synthetic: true` 的虚拟目录。
3. 单篇分享时，祖先节点标 `partial: true`，前端不展示兄弟课件，只展示"该路径下已共享的课件"。
4. 多篇分享落在同一祖先下时自动合并，虚拟目录转为普通目录。

**前端呈现要求（对应"目录结构依旧完整"）**

- 树节点必须显示完整层级：`高中英语 / 课后练习 / 课后练习-1`，**不能只显示文件名**。
- 面包屑、标题都带全路径；复制/下载后的默认目标路径同样带全路径。
- `partial` 目录可展开但只有已共享条目；空目录位置显示"未共享"占位，不显示兄弟数量。
- 整科共享时展示完整树，视觉上与自己的课件树一致。

**示例（A 只共享一篇）**

```json
{
  "teacherId": 7,
  "teacherName": "A 老师",
  "subject": "高中英语",
  "shareScope": "article",
  "tree": {
    "name": "高中英语", "path": "高中英语", "synthetic": true, "partial": true,
    "children": [
      { "name": "课后练习", "path": "高中英语/课后练习", "synthetic": true, "partial": true,
        "children": [
          { "name": "课后练习-1", "path": "高中英语/课后练习/课后练习-1.md",
            "articleId": 2031, "isShared": true, "allowCopy": true }
        ]
      }
    ]
  }
}
```

**接口**

- `POST /admin/courseware/shares`：`{ scopeType: "subject" | "article", subject, articleIds?: number[] }`，支持批量单篇。
- `GET /admin/courseware/resources`：列出有有效分享的老师与科目（没有有效分享则不出现在列表）。
- `GET /admin/courseware/resources/tree?teacherId=&subject=`：返回上述树结构。
- `DELETE /admin/courseware/shares/{shareId}`：撤回。
- `GET /admin/courseware/resources/articles/{teacherId}/{articleId}/preview`：只读预览，校验分享有效。

### 18.2 获取：整科 / 单篇、改名改路径、更新汇总与预览

**三条获取通道**

| 通道 | 行为 | 来源记录 |
| --- | --- | --- |
| 平台内复制 | 服务端直接拷入我的命名空间，立即可用，可继续在线编辑 | `course_copy` + 整科时 `course_subscription` |
| 下载 zip | 打包到本地，供离线编辑或留存；再次导入时重新发 ID | `course_copy`（`acquired_via='download'`） |

> Git 工作区（18.4）是老师编辑**自己**课件的通道，也可先下载他人的 zip 再纳入自己的 Git 仓库；它本身不是"获取他人课件"的入口。

**获取对话框字段**

| 字段 | 说明 |
| --- | --- |
| 范围 | 整科 / 勾选的单篇（树里可展开多选） |
| 目标根路径 | 默认继承来源科目名，可改（如 A 的 `高中英语/课后练习` → B 的 `英语/练习`） |
| 文件名 / 标题 | 单篇可改名（`课后练习-1` → `练习3`） |
| 冲突策略 | 自动改名（`练习3(2)`）/ 跳过 / 覆盖（覆盖走备份） |
| 订阅更新 | 整科默认勾选；单篇按篇记录 |

**改名示例**

B 获取 A 的 `高中英语/课后练习/课后练习-1`，改为 `英语/练习/练习3.md`：

- `course_copy.source_logical_path = '高中英语/课后练习/课后练习-1.md'`（保留原值，用于对照与更新）
- `course_copy.local_logical_path = '英语/练习/练习3.md'`
- `course_copy.local_title = '练习3'`
- 界面上显示"来源：A 老师 · 高中英语/课后练习/课后练习-1"，本地路径独立，互不影响。

**更新汇总（整科订阅）**

`GET /admin/courseware/updates` 返回按订阅分组的结果：

```json
{
  "subscriptions": [
    {
      "sourceTeacherId": 7,
      "sourceTeacherName": "A 老师",
      "subject": "高中英语",
      "baseVersion": 12,
      "currentVersion": 15,
      "updated": [
        { "sourceArticleId": 2031, "localArticleId": 1007, "localTitle": "练习3",
          "contentChanged": true, "answerChanged": true, "pathChanged": true }
      ],
      "added": [
        { "sourceArticleId": 2040, "logicalPath": "高中英语/课后练习/课后练习-8.md",
          "title": "课后练习-8" }
      ],
      "removed": [
        { "sourceArticleId": 2022, "title": "课后练习-2" }
      ],
      "renamed": [
        { "sourceArticleId": 2033,
          "from": "高中英语/语法课讲义/语法-3.md",
          "to": "高中英语/语法课讲义/语法专题-3.md" }
      ]
    }
  ]
}
```

- **单篇获取**只返回 `updated / removed / renamed`，不返回 `added`（没有"整科新增"概念）。
- **新增课件**可单篇获取，也可"获取整科增量"（把 `added` 批量拉入，按当前目标根路径落位）。
- **预览**：每条更新/新增都能点开预览（复用渲染器，只读），并显示正文 diff 与答案变更；预览依赖分享仍有效。
- **撤回分享**后：预览返回 403 并提示"来源已撤回共享"，已获取副本照常可用，只是不再提示更新。

### 18.3 在线编辑器与格式模板（混合布局）

**文档格式（保持 Markdown，Git 可直接编辑）**

```markdown
---
title: 练习3
subject: 高中英语
defaultLayout: A
---

<!-- @layout:A -->
# 一、知识点回顾

- 一般现在时的构成
- 第三人称单数变化规则

<!-- @layout:B -->
# 二、当堂练习

【题干1】He ___ (go) to school every day.
【@1】

<!-- @layout:C -->
# 三、易错对比

| 项目 | 一般现在时 | 现在进行时 |
| --- | --- | --- |
| 标志词 | every day | now |
```

- 每个 `# ` 开启一个新的 section；`<!-- @layout:X -->` 作用于其后的 section，缺省用 front-matter 的 `defaultLayout`。
- 指令是 HTML 注释：普通 Markdown 渲染会忽略它，**不影响现有 `questionText.js` 对 `【题干N】` / `【@N】` 的解析**。
- 布局注册表（服务端校验 + 前端渲染共用）：

```sql
CREATE TABLE IF NOT EXISTS public.course_layout (
    layout_id text PRIMARY KEY,            -- A / B / C / D
    name text NOT NULL,                    -- 讲义式 / 练习式 / 对比表 / 图文卡片
    component text NOT NULL,               -- 前端组件名，如 LayoutPractice
    allowed_blocks text[] NOT NULL DEFAULT '{}',
    version integer NOT NULL DEFAULT 1,
    active boolean NOT NULL DEFAULT true
);
```

**渲染流程**

1. 按 `^# ` 切分 section；
2. 读取每个 section 前的布局指令；
3. 查 `course_layout` 取组件，按 section 渲染（题目块复用现有题干/作答组件）；
4. 未知/停用布局 → 回落 `defaultLayout` 并在发布时告警（发布校验会直接拒绝未知 id）。

**编辑器形态**

- 左侧大纲：section 列表 + 布局标签（如"A 讲义"、"B 练习"），可拖拽排序。
- 中间编辑区：Markdown 源码 / 所见即所得双模式，同一份内容。
- 右侧实时预览：按最终布局渲染，等同学员视角。
- "插入布局"：选 A/B/C/D，自动生成 `# 标题` + `<!-- @layout:X -->` + 骨架。
- "插入块"：段落、要点、题目（自动生成 `【题干N】` 与 `【@N】`，编号连续）、图片、表格、提示框、公式。
- 模板库：`templates/<科目>/<类型>.md`（新授课、习题课、复习课、专题课），新建课件时选择，模板即带 front-matter 与多段布局骨架。
- 草稿保存到 `course_draft`；点"发布"才生成 release，半成品不会出现在学员端。

**发布校验新增项**

| 规则 | 说明 |
| --- | --- |
| 布局 id 已注册且 active | 未知布局拒绝发布 |
| section 非空 | 每个 `#` 后必须有正文或块 |
| 题目块完整 | 题干与 `【@N】` 配对，编号连续 |
| 图片已上传 | 引用的资产存在于 assets 清单 |
| 模板变量未残留 | 检出 `{{...}}` 占位符即拒绝 |

### 18.4 Git 工作区：仓库不含课件正文

**结论：可以做到。** 仓库里只放"清单 + 工具脚本"，正文与图片放对象存储，本地按需拉取、改完推送；Git 历史里只有 hash 与路径变化。

**仓库结构**

```
tutor-courseware-workspace/
├── .courseware/
│   ├── config.json          # serverUrl、teacherId（不含 token）
│   └── manifest.json        # 当前版本清单：articleId / 逻辑路径 / 标题 / hash / version
├── content/                 # .gitignore —— 正文工作副本
├── assets/                  # .gitignore —— 图片工作副本
├── scripts/courseware.mjs   # pull / status / diff / push / template
├── .githooks/pre-commit     # 阻止 content/、assets/ 被提交
├── .gitignore
└── README.md
```

**CLI 行为**

| 命令 | 行为 |
| --- | --- |
| `courseware pull` | 读 `.courseware/manifest.json` → 调 `GET /workspace/manifest` → 下载 blob 到 `content/`、图片到 `assets/` |
| `courseware status` | 本地文件 hash 与 manifest 比对，列出新增 / 修改 / 删除 |
| `courseware diff` | 本地与服务端逐篇 unified diff（Git 看不到正文差异，用这个补） |
| `courseware push` | 上传变更 blob → `POST /workspace/push` → 服务端校验并发布 → 回写 `manifest.json` |
| `courseware template` | 拉取模板库到 `templates/`，新课件从模板起手 |

**push 协议**

1. `PUT /admin/courseware/workspace/blobs/{sha256}`：上传正文或图片（内容寻址，已存在则秒传）。
2. `POST /admin/courseware/workspace/push`：

```json
{
  "baseVersion": 12,
  "articles": [
    { "articleId": 1007, "logicalPath": "英语/练习/练习3.md",
      "title": "练习3", "contentHash": "sha256:aa11…", "answerHash": "sha256:bb22…" }
  ]
}
```

3. 服务端校验 `baseVersion == course_release_head`，不相等返回 `409`，CLI 提示先 `pull`（防止覆盖他人/网页端的改动）。
4. 校验布局、题目编号、答案键完整后生成新 release，返回新版本号。
5. CLI 回写 `manifest.json`，由使用者 `git add/commit`（提交信息建议写明改了哪几篇）。

**为什么满足"仓库里没有课件内容"**

- `content/`、`assets/` 写入 `.gitignore`，`pre-commit` 钩子再拦一次，误提交会被直接拒绝。
- 仓库里只有 `manifest.json` 中的路径与 sha256，正文实体只在对象存储与本机工作副本。
- 代价：**Git 里看不到正文 diff**，只有 hash 变化；评审与对比要用 `courseware diff` 或平台预览。若必须可读 diff，就要接受正文入库（与当前要求冲突）。

**安全与并发**

- token 从环境变量 `COURSEWARE_TOKEN` 读取，`config.json` 不落密钥；泄漏可单独吊销。
- push 只能写本人命名空间；`baseVersion` 乐观锁防并发覆盖。
- 图片走内容寻址（`assets/<sha256>`），避免 Git 仓库膨胀与重复存储。

**与在线编辑器共存**

- 两者操作同一套 manifest/blob：网页保存草稿，Git push 直接发布；网页发布后再 push 会因版本不一致被拒，需先 pull。
- 学员端只认"已发布版本"，草稿与本地工作副本都不影响线上内容。

### 18.5 对前述设计的修订点

| 章节 | 修订 |
| --- | --- |
| 1.1 | 获取改为"主动分享后可见"；新增"编辑方式"一行 |
| 6.1 | `course_share` 改为范围式；新增 `course_subscription`、`course_draft` |
| 6.1 | `course_copy` 增加 `local_logical_path`、`local_title`、`source_logical_path`、`acquired_via` |
| 7.3 | 获取支持整科/单篇、改名、目标路径、冲突策略 |
| 7.4 / 7.5 | 更新汇总增加"新增/删除/改名"，支持预览 |
| 8.2 | 新增分享、资源树、预览、workspace 接口 |
| 9 | 前端新增资源树、获取对话框、更新汇总、在线编辑器 |
| 4.4 | 发布校验增加布局 id 与模板残留检查 |
| 13 | CI 增加布局与 manifest 校验 |
| 15 | 总工期由 3–4 周调整为约 5–6 周（第三轮多老师/通知/Git 向导后再调至 6–7 周，见 19.9） |
| 16 | 验收增加分享门禁、目录完整性、改名、增量更新、混合布局、Git 往返 |

### 18.6 工期增补

| 新增项 | 估时 |
| --- | --- |
| 分享模型 + 资源树 + 预览 | 3–4 天 |
| 获取（整科/单篇）+ 改名改路径 + 更新汇总（新增/删除/改名） | 4–5 天 |
| 在线编辑器 + 布局渲染 + 模板库 | 6–8 天 |
| Git 工作区 CLI + push 协议 + 预提交钩子 | 3–5 天 |
| **合计增量** | **16–22 天**（叠加原 3–4 周，第三轮追加多老师/通知/Git 向导后总计约 6–7 周，见 19.9） |

---

## 19. 决策落地（2026-10-30 第三轮确认）

> 本章把第 17 节的 9 条决定落成具体设计；与第 18 章冲突时以本章为准。

### 19.1 规模：最多约 10 位老师

- 单 Supabase 项目、单桶、单库即可，不需要分库分表或独立部署。
- 每位老师一个命名空间前缀（`blobs/<teacher_id>/`、`content/<teacher_id>/`），逻辑隔离足够。
- 配额建议：每位老师 300–500 MB（含历史版本），平台总量随免费版额度调整（见 19.8）。
- 管理端只需一个"老师列表 + 存储用量"页面，无需复杂的租户体系。

### 19.2 分享：全员可见

- 不做 `grantee` 表，分享即对平台内全部老师可见；资源目录只展示"有有效分享"的老师与科目。
- 可见 ≠ 可改：其他老师只能预览、获取；原文只有所有者能改。
- 撤回后立即从资源目录消失，已获取副本保留。
- 隐私边界：课件对同平台老师可见，但对学员仍按授权控制，不因分享而放开。

### 19.3 同名冲突：询问是否覆盖

获取（复制/下载/导入）时，若目标路径已存在：

```
检测到同名课件：英语/练习/练习3.md（已存在，本地标题「练习3」）
  [覆盖]  先备份现有版本，再用来源内容替换
  [改名]  输入新文件名（默认建议「练习3(2)」），可同时改目录
  [取消]  不执行
```

- 默认焦点在"取消"或"改名"，**不做静默覆盖**。
- 服务端在 `POST /copies` 返回 `409 COURSEWARE_TARGET_EXISTS`，带上已存在条目的 `articleId/title/path`；前端弹窗后带 `onConflict: "overwrite" | "rename"` 重新提交。
- 选择"改名"时必须提交一个不同的 `localLogicalPath`，否则再次 409。
- 覆盖时保留本地 `article_id`（沿用现有课件条目），先写备份到 `backups/`，再更新内容与来源指针。
- 下载 zip 时若解压目标已存在，同样弹窗，不静默改名。

### 19.4 Git：全员可用 + 零基础引导

老师不必会命令行，平台提供"向导 + 一键脚本"，Git 只是进阶通道；不会用也能全程用网页编辑器。

**首次使用六步（平台内图文向导）**

1. 在"课件设置 → Git 工作区"点"生成令牌"（按老师签发，可随时吊销）。
2. 下载预配置的工作区 zip（已含 `config.json`、脚本、示例）。
3. 安装 Git 与 VS Code（页面给官方下载链接，Windows 默认选项即可）。
4. 双击 `一键拉取.bat`（内部执行 `node scripts/courseware.mjs pull`），首次运行粘贴令牌。
5. 在 `content/` 下编辑 Markdown（或直接用编辑器里的模板文件起手）。
6. 双击 `一键推送.bat`，看到"发布成功 v13"即完成。

**平台侧配套**

| 能力 | 说明 |
| --- | --- |
| `courseware doctor` | 自检 Node、Git、令牌、仓库状态，输出中文结论 |
| `一键拉取.bat` / `一键推送.bat` | 封装命令，双击即用；失败时窗口停留显示原因 |
| `.vscode/tasks.json` | VS Code 里"运行任务 → 拉取/推送"，不用记命令 |
| 令牌存储 | 写入 `.courseware/credentials`（`.gitignore` 排除），或环境变量 `COURSEWARE_TOKEN` |
| 错误提示 | 409 → "来源已更新，请先拉取"；401 → "令牌失效，请重新生成" |
| 教程 | `README.md` 图文 + 常见问题（换电脑、冲突、图片放哪、如何回滚） |

**老师不会用怎么办**：默认仍走网页编辑器；Git 向导只在"高级编辑"入口出现，不阻塞日常使用。

### 19.5 撤回共享后的语义

- 资源目录立即移除；接收方的 `course_copy.status = 'source_revoked'`。
- 更新检查停止：不再提示新版本，`GET .../preview` 返回 `403 COURSEWARE_SOURCE_REVOKED`。
- 本地内容完整保留，可继续编辑、发布、授课；界面标注"来源已撤回共享"。
- 若来源重新分享，是否恢复订阅另行确认（建议：不自动恢复，需重新获取或手动"恢复关联"）。

### 19.6 消息提醒

**触发场景**

| 类型 | 触发 | 聚合 |
| --- | --- | --- |
| `update` | 订阅的来源发布新版本，且涉及接收方已获取的课件 | 按"来源老师 + 科目 + 版本"聚合成 1 条 |
| `new_article` | 整科订阅下来源新增课件 | 与同批 `update` 合并为一条摘要 |
| `share` | 有老师新建分享 | 按"老师 + 科目"聚合，低优先级 |
| `system` | 配额告警、令牌吊销等 | 单条 |

**表结构**

```sql
CREATE TABLE IF NOT EXISTS public.course_notification (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    recipient_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    type text NOT NULL CHECK (type IN ('update', 'new_article', 'share', 'system')),
    teacher_account_id bigint,
    subject text,
    article_id bigint,
    title text NOT NULL,
    body text NOT NULL DEFAULT '',
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    dedupe_key text NOT NULL,              -- 含接收者，如 update:42:7:高中英语:v13
    read_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT course_notification_dedupe_unique UNIQUE (dedupe_key)
);
```

- 生成时机：来源发布成功后，服务端对订阅关系做一次差异计算（10 位老师规模，毫秒级），写入通知；不依赖接收方打开页面。
- UI：顶栏铃铛 + 未读数；列表按类型分组，点击跳到"更新汇总"并定位到对应课件。
- 不做实时推送（WebSocket），轮询 60 秒或页面加载时刷新即可；邮件后续再加。

### 19.7 一个学员可属于多位老师

这是本轮最大的结构变化，旧模型 `student.claimed_by`（单一老师）不再够用。

**数据模型**

```sql
-- 学员—老师关系（多对多）
CREATE TABLE IF NOT EXISTS public.student_teacher (
    student_id bigint NOT NULL REFERENCES public.student (id) ON DELETE CASCADE,
    teacher_account_id bigint NOT NULL REFERENCES public.account (id) ON DELETE CASCADE,
    is_primary boolean NOT NULL DEFAULT false,
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'ended')),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, teacher_account_id)
);
-- 每位学员最多一个主老师
CREATE UNIQUE INDEX IF NOT EXISTS student_teacher_primary_unique
    ON public.student_teacher (student_id) WHERE is_primary;

-- 文章授权按老师维度（替代 student.permissions 的全局整数语义）
CREATE TABLE IF NOT EXISTS public.student_article_grant (
    student_id bigint NOT NULL,
    teacher_account_id bigint NOT NULL,
    article_id bigint NOT NULL,
    granted_at timestamptz NOT NULL DEFAULT now(),
    granted_by bigint,
    PRIMARY KEY (student_id, teacher_account_id, article_id)
);
```

**兼容与迁移**

- `student.claimed_by` 保留为"主老师"字段，仅用于默认展示；真实关系以 `student_teacher` 为准。
- 迁移时按 `claimed_by` 回填一条 `is_primary = true` 的关系；`student.permissions` 的整数按主老师写入 `student_article_grant`。
- 新增"学员—老师"入口在教师端：添加/移除老师，主老师可切换（同一时间只有一位）。

**接口与前端变化**

| 位置 | 变化 |
| --- | --- |
| 学员首页 | `GET /courseware/manifests` 返回数组，按"老师 → 科目"分组；顶部可切换老师 |
| 文章详情 | 必须带 `teacherId`（`/courseware/articles/{teacherId}/{articleId}/content`）；旧的不带老师路径废弃 |
| 学习接口 | `/articles/{articleId}/study-state`、`submissions`、`review` 等全部加 `teacherId` |
| 错题本 | 记录增加 `teacher_account_id`；列表按老师分组，筛选项含老师 |
| 学习进度 | 按 `(studentId, teacherId)` 独立统计，不跨老师合并 |
| 教师端学员列表 | 只显示"属于本老师"的学员；同一学员可出现在多位老师名下 |
| 权限面板 | 授权对象为 `(teacherId, articleId)`，仅能操作本老师命名空间 |

**边界情况**

- 同一学员在两个老师处都有同名课件（甚至同 `article_id`）：错题、作答、进度按老师分开，互不覆盖。
- 唯一键调整：`wrong_questions` 唯一约束加入 `teacher_account_id`；`article_question_submissions` 同样加老师维度。
- 统计：单老师维度完整可用；跨老师只做"总量"类指标，不做排名比较。

### 19.8 Supabase 免费版容量评估（暂不迁移）

> 免费额度以官网当期为准，以下为撰写时的大致值，仅用于估算。

| 资源 | 免费版约 | 本项目估算（10 位老师） | 判断 |
| --- | --- | --- | --- |
| 数据库 | 500 MB | 元数据 + 作答/错题 3 年内约 50–150 MB | 够用，需关注错题表增长 |
| 文件存储 | 1 GB | 正文约 2–5 MB；图片 128 张约 25–50 MB；版本历史 ×3 ≈ 100–200 MB | 初期够，半年内需看水位 |
| 月流量（egress） | 5 GB | 100 学员 × 每天 1 张图 × 200 KB × 30 ≈ 600 MB/月；500 学员 × 每天 3 张 ≈ 8.6 GB/月 | **最先吃紧**，图片是大头 |
| 项目暂停 | 连续约 7 天无请求暂停 | 假期可能触发 | 需保活或接受暂停（数据保留） |
| 备份 | 免费版保留窗口较短 | 需自建每周 `pg_dump` | 必须补 |

**不迁移前提下的优化（先做）**

1. 图片统一转 WebP、长边 ≤ 1280、目标单张 < 100 KB（可减 50–70% 流量）。
2. 图片懒加载 + 长缓存；图片走"公开读前缀 + CDN 缓存"（只放图片，不含题目答案），正文仍走鉴权接口。
3. 正文与图片内容寻址，避免重复下载与重复存储。
4. 每周 `pg_dump` + 导出 zip，落到本地或云主机。
5. 轻量保活：定时查询一次（如每天），避免假期项目被暂停。

**替代方案（备选，不执行）**

| 方案 | 适用 | 成本 | 迁移改动 |
| --- | --- | --- | --- |
| Supabase Pro | 流量/存储都吃紧 | 约 $25/月 | 无，原地升级 |
| 图片搬 Cloudflare R2（DB 留免费版） | 只是流量紧张 | 免费 10 GB、出站免费 | 只改 `CoursewareStorage` 资产实现，约 1–2 天 |
| 自建（现有 Linux 云主机：Postgres + MinIO） | 想完全可控 | 服务器成本 + 运维 | 存储与 DB 都要迁，3–5 天 |
| 阿里云 OSS / 腾讯 COS + 现有 PG | 国内访问速度优先 | 按量计费，便宜 | 只改存储实现 |

**建议触发顺序**：存储 > 80% → 先压缩/清历史；流量 > 80% → 先上 CDN 缓存，再搬 R2；数据库 > 80% → 归档旧错题/旧版本，最后才考虑 Pro。当前阶段维持免费版。

### 19.9 对工期与验收的影响

- 多老师学员改造 +3 天（表、接口、前端分组、错题维度）。
- 消息提醒 +2 天（表、发布时差异计算、铃铛 UI）。
- Git 零基础向导 +1–2 天（一键脚本、doctor、图文教程）。
- 同名冲突询问 +0.5 天。
- 总工期由 5–6 周调整为约 6–7 周；验收新增第 21–25 项。

---

## 附录 A：manifest 完整示例（多篇）

```json
{
  "schemaVersion": 1,
  "teacherId": 7,
  "teacherName": "李老师",
  "subject": "高中英语",
  "version": 13,
  "publishedAt": "2026-10-30T22:05:00+08:00",
  "contentHash": "sha256:0a1b…",
  "articles": [
    {
      "articleId": 2001,
      "legacyArticleId": null,
      "title": "语法课讲义-1",
      "series": "高中英语",
      "logicalPath": "高中英语/语法课讲义/语法课讲义-1.md",
      "contentHash": "sha256:aa11…",
      "answerHash": "sha256:bb22…",
      "questionCount": 8,
      "updatedAt": "2026-10-30T10:00:00+08:00",
      "questions": [
        { "questionId": "1", "answer": "A", "autoGrade": true },
        { "questionId": "2", "answer": "which", "autoGrade": false }
      ]
    }
  ],
  "assets": []
}
```

## 附录 B：导出 zip 结构

```
export-7-20261030.zip
├── manifest.json
├── articles/
│   └── 高中化学/理论课讲义/3-1 原电池.md
├── assets/
│   └── cc/cc90….png
└── answers.json            （与 manifest 内 questions 冗余，便于人工核对）
```

## 附录 C：改动文件清单（初版估算）

**后端**
- 新增 `courseware/` 模块（8 个类 + 3 个契约测试）
- `backend/api/openapi.yaml`
- `V21__teacher_scoped_courseware_columns.sql`、`V22__teacher_scoped_courseware_keys.sql`
- `database/reconciliation/2026-11-xx_teacher_scoped_courseware.sql`

**前端**
- `src/stores/blogStore.js`、`src/stores/authStore.js`
- `src/composables/useWrongQuestionsResolve.js`
- `src/views/ArticleDetailView.vue`、`HomeView.vue`、`CategoryView.vue`
- `src/views/admin/AdminContentView.vue`、`AnswerPanel.vue`、`ReviewPanel.vue`、`PermissionPanel.vue`、`LearnerSubjectPanel.vue`
- `src/gateways/coursewareGateway.js`、`learningGateway.js`
- 新组件 5 个（见 9.3）
- `src/router/index.js`

**构建/脚本**
- `scripts/sync-articles-data.js`（改为种子生成，退出构建流程）
- `package.json` 脚本（移除 build 前的 sync 依赖，新增 `seed:manifest`）
- CI：新增 `check-courseware` 与 manifest 校验

---

*本文档为设计说明，尚未落地代码；实施前请确认第 17 节的待确认问题。*
</content>
