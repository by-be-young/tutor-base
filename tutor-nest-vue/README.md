```markdown
# Tutor Nest Vue - 学习资料仓库

一个基于 Vue 3 构建的在线学习资料管理系统，支持 Markdown 文章展示、数学公式渲染、图片嵌入、触控笔绘制、题目练习与批阅等功能。

## 🌟 功能特性

### 核心功能
- 📚 **文章浏览** - 支持 Markdown 格式的学习资料展示
- 🔐 **用户认证** - 基于 Supabase 的登录/注册系统
- 🎨 **双栏布局** - 桌面端自动切换双栏显示（正文 + 侧边栏）
- 📐 **数学公式** - 基于 KaTeX 的 LaTeX 数学公式渲染
- 🖼️ **图片嵌入** - 支持 `![[图片名]]` 语法的图片嵌入
- ✍️ **触控笔绘制** - 平板触控笔可在页面上绘制（渐隐效果）
- 📝 **题目系统** - 支持题目占位符、学生提交、教师批阅

### 管理模式
- 👥 **权限管理** - 管理员可控制学生对文章的访问权限
- ✏️ **批阅中心** - 教师批阅学生答案（正确/半对/错误）
- 🔑 **答案设置** - 设置标准答案并开启自动批阅
- 📊 **状态追踪** - 实时显示学生提交和批阅状态

### 用户体验
- 💾 **自动保存** - 页面关闭前自动保存答题内容
- 📱 **响应式设计** - 适配桌面、平板、手机等设备
- 🎯 **筛选功能** - 按科目、状态等条件筛选文章
- 🔔 **操作反馈** - Toast 弹窗提示操作结果

---

## 🏗️ 项目结构

```
tutor-nest-vue/
├── public/
│   └── data/
│       └── blogs.json              # 文章索引数据
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
├── blogs/                          # Markdown 文章目录
│   ├── 化学/
│   │   ├── 前导课讲义/
│   │   ├── 理论课讲义/
│   │   └── 课后练习/
│   └── 英语/
│       ├── 写作课讲义/
│       ├── 语法课讲义/
│       └── 课后练习/
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
| `/` | HomeView | 首页 |
| `/category?subject=xxx` | CategoryView | 分类页（需登录） |
| `/blog/:id` | BlogDetailView | 文章详情（需登录） |
| `/blog/:id?mode=review&studentId=xxx` | BlogDetailView | 批阅模式 |
| `/blog/:id?mode=answer` | BlogDetailView | 答案设置模式 |
| `/admin` | AdminView | 管理页面（需登录） |
| `/*` | NotFoundView | 404 页面 |

**主要逻辑：**
- 使用 `createWebHistory` 实现 SPA 路由
- 路由守卫检查登录状态

---

### 3. 首页 `src/views/HomeView.vue`

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

### 4. 分类页 `src/views/CategoryView.vue`

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
- `FolderNode.vue` - 文件夹节点，可展开/折叠
- `FileNode.vue` - 文件节点，显示警告图标

---

### 5. 文章详情页 `src/views/BlogDetailView.vue`

**功能：** 文章阅读、答题、批阅、答案设置（四种模式合一）

**三种模式：**

| 模式 | 触发方式 | 功能 |
|------|----------|------|
| `study` | 默认 | 学生阅读文章、答题 |
| `review` | `?mode=review&studentId=xxx` | 教师批阅学生答案 |
| `answer` | `?mode=answer` | 教师设置标准答案 |

**内容渲染流程：**
1. 加载 Markdown 文件
2. 处理图片嵌入语法（`![[图片]]`）
3. 注入题目占位符（`【@1】` → `<div class="question-slot">`）
4. 渲染 Markdown 为 HTML
5. 渲染 KaTeX 数学公式
6. 初始化题目卡片

**题目系统逻辑：**

**学习模式（study）：**
- 显示题目文本输入框
- 已批阅的题目锁定，可切换查看答案/作答
- 支持单个题目提交或全部提交

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

**数据库表：**
- `article_answer_keys` - 存储标准答案
- `article_question_submissions` - 存储学生提交

---

### 6. 管理页面 `src/views/AdminView.vue`

**功能：** 学生管理、权限控制、批阅、答案设置

**三个面板：**

**权限管理面板 (`PermissionPanel.vue`)**
- 选择学生 → 选择科目 → 勾选文章授权
- 筛选：未授权/已授权/全部
- 保存权限到 `student` 表

**批阅中心面板 (`ReviewPanel.vue`)**
- 选择学生 → 选择科目 → 显示待批阅文章
- 筛选：待批阅/已批阅/无需批阅
- 点击文章进入批阅模式
- 显示待批阅数量徽章

**答案设置面板 (`AnswerPanel.vue`)**
- 选择科目 → 显示文章列表
- 筛选：待设置/已设置/无需设置
- 点击文章进入答案设置模式

**数据缓存：**
- `articleHasQuestions` - 检测文章是否包含题目
- `articleAnswerKeys` - 文章是否已设置答案
- `studentSubmissions` - 学生提交状态

---

### 7. 状态管理 `src/stores/`

#### authStore.js - 认证状态
```javascript
// 管理用户登录状态
state: { currentUser, permissionIds }
actions: { login, register, logout, initFromStorage }
```

**主要逻辑：**
- 支持 Supabase 数据库登录
- 超级用户（young）拥有所有权限
- 登录信息持久化到 localStorage

#### blogStore.js - 博客数据
```javascript
// 管理文章数据
state: { blogData, isLoading, error }
actions: { loadBlogData, getBlogsBySubject, getBlogById }
```

**主要逻辑：**
- 从 `/data/blogs.json` 加载文章索引
- 提供按学科、ID 查询方法
- 加载失败使用默认数据

#### adminStore.js - 管理员状态
```javascript
// 管理后台状态
state: { students, currentStudentId, permissionDirty }
actions: { loadStudents, addStudent, updatePermissions, savePermissions }
```

**主要逻辑：**
- 从 `student` 表加载学生列表
- 管理权限修改和保存
- 追踪权限是否已修改（未保存提示）

---

### 8. 组合式函数 `src/composables/`

#### useKatex.js - 数学公式渲染

**功能：** 动态加载 KaTeX 库，渲染数学公式

**主要逻辑：**
1. 预连接 CDN 加速
2. 动态加载 CSS 和 JS
3. 预处理文本节点（替换 `\cdotp` 为 `\cdot`）
4. 使用 `renderMathInElement` 渲染公式
5. 强制换行处理（防止超长公式溢出）
6. 5秒超时保护（超时移除公式标记）

**支持的公式语法：**
- 行内公式：`$...$`
- 块级公式：`$$...$$`
- LaTeX 语法：`\(...\)` 和 `\[...\]`

#### useImageEmbed.js - 图片嵌入

**功能：** 解析 `![[图片名]]` 语法，嵌入图片

**语法示例：**
```markdown
![[image.png]]                    # 基础图片
![[image.png|300x200]]            # 指定尺寸
![[image.png|center]]             # 居中
![[image.png|图片标题]]           # 带标题
![[image.png|300x200|center|标题]] # 完整语法
```

**主要逻辑：**
1. 解析选项（尺寸、对齐、标题、图注）
2. 生成带样式的 HTML img 标签
3. 支持懒加载
4. 观察 DOM 变化自动解析
5. 注入图片样式（对齐、浮动、响应式）

#### useDrawing.js - 触控笔绘制

**功能：** 平板触控笔在页面上绘制线条（渐隐消失）

**主要逻辑：**
1. 创建全屏 Canvas 画布
2. 仅响应触控笔（`pointerType === 'pen'`）
3. 阻止绘制时的页面滚动
4. 3秒无操作后渐隐（0.8秒淡出）
5. `useDrawingInDetail()` 自动在详情页激活

---

### 9. AppHeader.vue - 导航栏

**功能：** 全局导航栏，根据路由动态显示

**主要逻辑：**
- 首页：显示 Logo + 登录/退出按钮
- 分类页：显示返回按钮 + 学科标题 + 学科切换按钮
- 详情页：显示返回按钮 + 文章标题 + 模式标签
- 管理页：由 AdminView 自定义导航栏

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
| Vue Router | 路由管理 |
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

### 构建生产版本
```bash
npm run build
```

### 预览构建结果
```bash
npm run preview
```

---

## 📝 文章编写指南

### Markdown 格式
文章使用标准 Markdown 语法编写，存放在 `blogs/` 目录下。

### 题目占位符
```markdown
【@1】这是第一道题
【@2】这是第二道题
```
或使用 `[@1]` 语法。

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

---

## 📱 浏览器支持

- Chrome/Edge (推荐)
- Safari
- Firefox
- 平板浏览器（支持触控笔）

---

## 📄 许可证

MIT License
```

这个 README 涵盖了项目的所有核心功能和代码逻辑说明，可以作为项目的完整文档。