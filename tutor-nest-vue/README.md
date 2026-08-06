# Tutor Nest Vue - 学习资料仓库

一个基于 Vue 3 构建的在线学习资料管理系统，支持 Markdown 文章展示、数学公式渲染、图片嵌入、触控笔绘制、题目练习与批阅等功能。

## 🌟 功能特性

### 核心功能
- 📚 **文章浏览** — 支持 Markdown 格式的学习资料展示
- 🔐 **用户认证** — 基于 Supabase 的登录/注册系统
- 🎨 **双栏布局** — 桌面端自动切换双栏显示（正文 + 侧边栏）
- 📐 **数学公式** — 基于 KaTeX 的 LaTeX 数学公式渲染
- 🖼️ **图片嵌入** — 支持 `![[图片名]]` 语法的图片嵌入
- ✍️ **触控笔绘制** — 平板触控笔可在页面上绘制（渐隐效果）
- 📝 **题目系统** — 支持题目占位符、学生提交、教师批阅

### 管理模式
- 👥 **权限管理** — 管理员可控制学生对文章的访问权限
- ✏️ **批阅中心** — 教师批阅学生答案（正确/半对/错误）
- 🔑 **答案设置** — 设置标准答案并开启自动批阅
- 📊 **状态追踪** — 实时显示学生提交和批阅状态

### 用户体验
- 💾 **自动保存** — 页面关闭前自动保存答题内容
- 📱 **响应式设计** — 适配桌面、平板、手机等设备
- 🎯 **筛选功能** — 按科目、状态等条件筛选文章
- 🔔 **操作反馈** — Toast 弹窗提示操作结果
- 🏋️ **错题训练** — 独立页面重做错题本中的题目，做错次数自动累积
- 📑 **目录侧边栏** — 从左侧滑入的目录树，h2 按 h1 分组折叠，点击标题跳转
- 📋 **答题卡侧边栏** — 从左侧滑入的答题卡，显示各 h1 下的题目完成情况，点击题号跳转到对应 h1 顶部
- 🔄 **模式切换跟随** — 切换双栏/单栏显示模式时自动定位到此前屏幕中央所在的 h1 顶部

---

## 🏗️ 项目结构

```
tutor-nest-vue/
├── public/
│   ├── blogs/                      # Markdown 文章文件（.md 文件在此存放）
│   │   ├── 化学/
│   │   │   ├── 前导课讲义/
│   │   │   ├── 理论课讲义/
│   │   │   └── 课后练习/
│   │   └── 英语/
│   │       ├── 写作课讲义/
│   │       ├── 语法课讲义/
│   │       └── 课后练习/
│   └── data/
│       ├── blogs.json              # 文章索引（自动生成）
│       └── id_map.json             # 文章 ID 映射表（自动生成）
├── scripts/
│   └── sync-blogs-data.js          # 博客数据同步脚本
├── src/
│   ├── assets/                     # 静态资源
│   ├── components/                 # 可复用组件
│   │   ├── common/
│   │   │   └── AppHeader.vue       # 导航栏组件
│   │   ├── admin/
│   │   │   ├── PermissionPanel.vue     # 权限管理面板
│   │   │   ├── PermissionTreeNode.vue  # 权限树节点
│   │   │   ├── ReviewPanel.vue         # 批阅中心面板
│   │   │   ├── AnswerPanel.vue         # 答案设置面板
│   │   │   └── ClickableTreeNode.vue   # 可点击树节点
│   │   └── blogs/
│   │       ├── FolderNode.vue      # 文件夹节点
│   │       └── FileNode.vue        # 文件节点
│   ├── views/                      # 页面视图
│   │   ├── HomeView.vue            # 首页（登录/注册/学科卡片）
│   │   ├── CategoryView.vue        # 分类页（文章列表）
│   │   ├── BlogDetailView.vue      # 详情页（文章阅读/答题/批阅）
│   │   ├── AdminView.vue           # 管理页（权限/批阅/答案）
│   │   └── NotFoundView.vue        # 404 页面
│   ├── composables/                # 组合式函数
│   │   ├── useKatex.js             # KaTeX 数学公式渲染
│   │   ├── useImageEmbed.js        # 图片嵌入解析
│   │   └── useDrawing.js           # 触控笔绘制
│   ├── stores/                     # Pinia 状态管理
│   │   ├── authStore.js            # 用户认证状态
│   │   ├── blogStore.js            # 博客数据状态
│   │   └── adminStore.js           # 管理员状态
│   ├── utils/
│   │   └── supabase.js             # Supabase 客户端配置
│   ├── router/
│   │   └── index.js                # 路由配置
│   ├── App.vue                     # 根组件
│   └── main.js                     # 入口文件
├── index.html
├── vite.config.js
└── package.json
```

---

## 📄 核心文件说明

### 1. 入口文件 `src/main.js`

```javascript
// 创建 Vue 应用实例
// 注册 Pinia 状态管理
// 注册 Vue Router 路由
// 挂载到 #app 元素
```

**主要逻辑：**
- 初始化 Vue 3 应用
- 集成 Pinia（状态管理）和 Vue Router（路由）
- 全局注入图片样式

---

### 2. 路由配置 `src/router/index.js`

**路由表：**

| 路径 | 组件 | 说明 |
|------|------|------|
| `/#/` | HomeView | 首页 |
| `/#/category?subject=xxx` | CategoryView | 分类页（需登录） |
| `/#/blog/:id` | BlogDetailView | 文章详情（需登录） |
| `/#/blog/:id?mode=review&studentId=xxx` | BlogDetailView | 批阅模式 |
| `/#/blog/:id?mode=answer` | BlogDetailView | 答案设置模式 |
| `/#/wrong-questions` | WrongQuestionsView | 错题本（需登录） |
| `/#/wrong-training` | WrongTrainingView | 错题训练（需登录） |
| `/#/admin` | AdminView | 管理页面（需登录） |
| `/#/*` | NotFoundView | 404 页面 |

**主要逻辑：**
- 使用 `createWebHashHistory`（hash 模式）兼容 GitHub Pages 部署
- 路由守卫检查登录状态

---

### 3. 文章数据同步机制

`public/data/blogs.json` 和 `public/data/id_map.json` **无需手动维护**，由脚本自动生成。

**触发时机：**
- `npm run dev` 启动前（`predev` hook）
- `npm run build` 构建前（`prebuild` hook）

**执行流程：**

```
public/blogs/ 下的 .md 文件
        ↓ 扫描
scripts/sync-blogs-data.js
        ├── 生成 id_map.json（路径 → ID 的映射关系）
        └── 生成 blogs.json（文章索引，按系列 + ID 排序）
```

**新增文章：** 只需在 `public/blogs/` 下新建 `.md` 文件，下次 `npm run dev` 或 `npm run build` 时自动分配 ID 并同步到两个 JSON 文件。

#### ID 分配规则与不重复保证

```javascript
const oldIdMap = readJson(ID_MAP_FILE)  // 读取现有映射
const maxId = Math.max(...现有所有 id)   // 当前最大 ID
let nextId = maxId + 1                  // 从 max+1 开始递增

for (每个文件) {
  if (已有) → 保留原 id                  // 已注册文件 ID 不变
  else      → idMap[path] = nextId++    // 新文件分配递增 ID
}
```

- **nextId 只增不减** — 从当前最大 ID 的下一编号起步，每次分配后 +1，永不回头
- **已有文件不重分** — 每个文件注册后永久绑定其 ID，不会被覆盖或重新分配
- **不受执行次数影响** — 无论运行多少次脚本、一次新增多少个文件，每个新文件都获得一个唯一的、从未使用过的 ID

✅ **单次新增多个文件**：依次分配 `maxId+1`、`maxId+2`、`maxId+3` ...
✅ **分多次新增文件**：首次新增得 `maxId+1`，登记进 `id_map.json`；下次再新增时以此为基数继续分配，已有文件纹丝不动。

---

### 4. 首页 `src/views/HomeView.vue`

**功能：** 用户登录/注册，展示学科卡片

**主要逻辑：**
1. **未登录状态**
   - 显示大标题"学习资料仓库"
   - 底部显示登录/注册表单
   - 支持用户名登录和注册

2. **已登录状态**
   - 从 `authStore` 获取用户权限
   - 从 `blogStore` 加载文章数据
   - 按学科分组，生成书本卡片
   - 点击卡片跳转到分类页

3. **管理员快捷键**
   - 连续按三次 `+` 键进入管理页面

4. **水平滚动**
   - 桌面端支持鼠标滚轮水平滚动卡片

---

### 5. 分类页 `src/views/CategoryView.vue`

**功能：** 展示指定学科下的文章树状列表

**主要逻辑：**
1. 从 URL 参数获取学科名称
2. 根据用户权限筛选文章
3. 构建文件夹树结构：
   - 解析文章路径，生成目录层级
   - 文件夹在前，文件在后，按名称排序
4. 加载提交状态：
   - 查询 `article_answer_keys` 获取题目数量
   - 查询 `article_question_submissions` 获取学生提交状态
   - 有未提交的文章显示红色警告图标
5. 点击文件夹展开/折叠，点击文件跳转详情页

**树节点组件：**
- `FolderNode.vue` — 文件夹节点，可展开/折叠
- `FileNode.vue` — 文件节点，显示警告图标

---

### 6. 文章详情页 `src/views/BlogDetailView.vue`

**功能：** 文章阅读、答题、批阅、答案设置（四种模式合一）

**三种模式：**

| 模式 | 触发方式 | 功能 |
|------|----------|------|
| `study` | 默认 | 学生阅读文章、答题 |
| `review` | `?mode=review&studentId=xxx` | 教师批阅学生答案 |
| `answer` | `?mode=answer` | 教师设置标准答案 |

**内容渲染流程：**
1. 加载 Markdown 文件（路径使用 `import.meta.env.BASE_URL` 适配子目录部署）
2. 处理图片嵌入语法（`![[图片]]`）
3. 注入题目占位符（`【@1】` → `<div class="question-slot">`）
4. 渲染 Markdown 为 HTML
5. 渲染 KaTeX 数学公式
6. 初始化题目卡片

**题目系统逻辑：**

**学习模式（study）：**
- 显示题目文本输入框，卡片左上角为题号角标、右上角为提交状态角标
- 已批阅的题目锁定，可切换查看答案/作答
- 支持单个题目提交或全部提交
- 可点击「加入错题本」把当前题手动加入错题本（已加入则提示）

**批阅模式（review）：**
- 显示学生答案和参考答案
- 三个按钮：正确、半对、错误
- 批阅结果实时保存

**答案设置模式（answer）：**
- 设置标准答案
- 开启/关闭自动批阅
- 自动批阅：学生答案与标准答案完全匹配即判对

**双栏布局：**
- Markdown 中使用 `---` 分隔主内容和侧边栏
- 桌面端（≥1024px）自动切换双栏
- 移动端单栏显示

**目录侧边栏（TOC）：**
- 从左侧以固定定位滑入，高度与屏幕一致，遮盖正文
- 自动提取文章中的 h1、h2 标题
- h2 按所属 h1 折叠，点击 h1 切换展开，同一时间只展开一个
- 点击标题平滑滚动到对应位置
- 与答题卡互斥（打开目录时自动关闭答题卡）

**答题卡侧边栏：**
- 显示各 h1 标题，标题下以 32×32 小方框排列题目序号
- 方框底色按状态着色：灰白=未提交、琥珀=待批阅、绿=正确、橙=半对、红=错误
- 点击题号跳转到对应 h1 顶部

**悬浮按钮组（右下角）：**
- 提交/保存按钮（主按钮）
- 布局切换按钮（圆形，仅在有侧栏时显示）
- 答题卡按钮（圆形，仅 study 模式且有题目时显示）
- 目录按钮（圆形，文章有 h1 标题时显示）

**数据库表：**
- `article_answer_keys` — 存储标准答案
- `article_question_submissions` — 存储学生提交

---

### 7. 管理页面 `src/views/AdminView.vue`

**功能：** 学生管理、权限控制、批阅、答案设置

**三个面板：**

**权限管理面板 (`PermissionPanel.vue`)**
- 选择学生 → 选择科目 → 展开带复选框的树状文件列表
- 筛选：**未授权**（默认，文件夹自动全展开）/ 已授权 / 全部
- 文章行显示复选框，勾选即授权、取消即禁止
- 保存权限到 `student` 表

**批阅中心面板 (`ReviewPanel.vue`)**
- 选择学生 → 选择科目 → 筛选显示文章
- 筛选：**待批阅**（文件夹自动全展开）/ 已批阅 / **未提交**（有题目且设了答案但学生未提交的文章，文件夹自动全展开）/ 无需批阅（没有题目的文章）
- 点击文章进入批阅模式，显示该学生的答案和参考答案

**答案设置面板 (`AnswerPanel.vue`)**
- 选择科目 → 筛选显示文章
- 筛选：**待设置**（文件夹自动全展开）/ 已设置 / 无需设置（没有题目的文章）
- 点击文章进入答案设置模式，可填写标准答案并开关自动批阅

---

### 8. 状态管理 `src/stores/`

#### authStore.js — 认证状态
```javascript
// 管理用户登录状态
// state: currentUser, permissionIds
// actions: login, register, logout, initFromStorage
```
- 支持 Supabase 数据库登录
- 超级用户（young）拥有所有权限
- 登录信息持久化到 localStorage

#### blogStore.js — 博客数据
```javascript
// 管理文章数据
// state: blogData, isLoading, error
// actions: loadBlogData, getBlogsBySubject, getBlogById
```
- 从 `blogs.json` 加载文章索引（路径自动适配 BASE_URL）
- 提供按学科、ID 查询方法

#### adminStore.js — 管理员状态
```javascript
// 管理后台状态
// state: students, currentStudentId, permissionDirty
// actions: loadStudents, addStudent, updatePermissions, savePermissions
```
- 从 `student` 表加载学生列表
- 管理权限修改和保存

---

### 9. 组合式函数 `src/composables/`

#### useKatex.js — 数学公式渲染
- 动态加载 KaTeX 库
- 支持行内 `$...$` 和块级 `$$...$$`

#### useImageEmbed.js — 图片嵌入
- 解析 `![[图片名|选项]]` 语法
- 支持尺寸、对齐、标题等选项

#### useDrawing.js — 触控笔绘制
- 全屏 Canvas，仅响应触控笔
- 3 秒无操作后渐隐

---

### 10. AppHeader.vue — 导航栏

根据路由动态显示：首页显示 Logo + 登录/退出、分类页显示返回 + 学科标题、详情页显示返回 + 文章标题 + 模式标签。

---

## 🗄️ 数据库结构

### student 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | int | 主键 |
| username | text | 用户名 |
| permissions | int[] | 文章权限列表 |

### article_answer_keys 表
| 字段 | 类型 | 说明 |
|------|------|------|
| blog_id | int | 文章ID |
| question_id | int | 题目ID |
| answer_text | text | 标准答案 |
| auto_grade | boolean | 是否自动批阅 |

### article_question_submissions 表
| 字段 | 类型 | 说明 |
|------|------|------|
| blog_id | int | 文章ID |
| student_id | int | 学生ID |
| question_id | int | 题目ID |
| answer_text | text | 学生答案 |
| review_status | text | 批阅状态 (pending/reviewed) |
| review_result | text | 批阅结果 (correct/partial/wrong) |

---

## 📦 技术栈

| 技术 | 用途 |
|------|------|
| Vue 3 | 前端框架 |
| Vite | 构建工具 |
| Pinia | 状态管理 |
| Vue Router | 路由管理（hash 模式） |
| Supabase | 后端数据库 |
| Marked | Markdown 解析 |
| KaTeX | 数学公式渲染 |
| Font Awesome | 图标库 |

---

## 🚀 开发指南

### 安装依赖
```bash
npm install
```

### 启动开发服务器
```bash
npm run dev
```
（自动执行 `sync-blogs-data.js` 同步文章数据后再启动）

### 构建生产版本
```bash
npm run build
```
（自动执行 `sync-blogs-data.js` 同步文章数据后再构建）

### 预览构建结果
```bash
npm run preview
```

---

## 📝 文章编写指南

### 文章存放位置

所有 `.md` 文件存放在 `public/blogs/` 下，按学科分目录存放。

**新建文章步骤：**
1. 在 `public/blogs/` 下创建 `.md` 文件
2. 运行 `npm run dev`（或 `npm run build`）
3. 脚本自动分配 ID、更新 `blogs.json` 和 `id_map.json`

### Markdown 格式
文章使用标准 Markdown 语法编写。

### 题目占位符

文章通过两种占位符声明题目：

- **答题占位符** `【@】`（自动编号）或 `【@N】`（显式编号，推荐）—— 在该位置插入答题卡片，单独占一行
- **题干占位符** `【题干N】` —— 标记第 N 题的题干内容，渲染时隐藏；同一题可多处标记（如阅读材料 + 小题要求），错题本按出现顺序拼接
- **公共题干占位符** `【题干N-M】` —— 大题公共题干（如完形填空材料），区间内每题的错题本内容都会包含它

```markdown
### 例题 1
【题干1】这是第一道题的题干

A. 选项一
B. 选项二

【@1】

### 例题 2
【题干2】这是第二道题的题干

【@2】
```

**为兼容学生已有的提交记录，建议固定题目 ID 后不要随意改动。** 新增题目使用一个未用过的数字即可。

📐 **完整的例题书写规范（选项格式、双栏布局、错题本适配等）请参阅 [`docs/例题格式规范.md`](docs/例题格式规范.md)。**

### 图片嵌入
```markdown
![[化学/反应机理.png]]
![[英语/语法图表.png|300x200|center|图表标题]]
```

### 双栏布局
使用 `---` 分隔主内容和侧边栏：
```markdown
# 标题

主要内容...

---

侧边栏内容...

---

继续主要内容...
```

### 数学公式
```markdown
行内公式：$E = mc^2$
块级公式：$$\sum_{i=1}^n i = \frac{n(n+1)}{2}$$
```

---

## 🔧 环境变量

创建 `.env` 文件：
```bash
VITE_SUPABASE_URL=你的_Supabase_URL
VITE_SUPABASE_ANON_KEY=你的_Supabase_Anon_Key
```

部署到 GitHub Pages 时，需在仓库 Settings → Secrets and variables → Actions 中设置同名 Secrets。

---

## 🚢 部署

GitHub Actions 自动部署（`.github/workflows/deploy.yml`）：

1. 推送到 `main` 分支自动触发
2. 注入 Supabase 环境变量（从 Secrets 读取）
3. 以 `--base=/tutor-base/` 构建
4. 部署到 GitHub Pages

### 手动触发
GitHub 仓库 → Actions → Deploy to GitHub Pages → Run workflow

---

## 📱 浏览器支持

- Chrome/Edge（推荐）
- Safari
- Firefox
- 平板浏览器（支持触控笔）

---

## 📄 许可证

MIT License

这个 README 涵盖了项目的所有核心功能和代码逻辑说明，可以作为项目的完整文档。