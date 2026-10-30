<!--
  CategoryView.vue - 学科知识地图（Riso Campus 印刷学院风）
  ----------------------------------------------------------------------------
  功能说明：
    1. 根据用户登录状态展示不同内容（未登录引导 / 空目录提示 / 知识地图）
    2. 从路由 query 参数中获取当前科目（subject），结合用户权限过滤博客数据
    3. 将过滤后的博客数据构建为层级目录树（文件夹 + 文件）
    4. 加载并展示每个博客文件的提交状态（待提交题数）
    5. 支持点击文件节点导航到博客详情页

  视觉：奶油纸底 + 墨线卡片 + 高饱和印刷色块 + 错位硬阴影（杂志封面感）
  交互增强（不改变数据源与权限逻辑）：
    - 本地标题搜索：仅在内存中过滤已构建的目录树
    - 全部展开 / 全部收起：通过展开信号下发给递归文件夹节点
    - 搜索时自动展开命中的文件夹

  依赖关系：
    - 全局状态：authStore（认证信息）、blogStore（博客数据）
    - 子组件：FolderNode（目录文件夹节点）、FileNode（目录文件节点）
    - 路由参数：query.subject（当前选中的科目）

  数据流：
    route.query.subject  →  currentSubject  →  filteredArticles  →  treeData  →  渲染目录树
    authStore.permissionIds ──────────────────┘
    blogStore.blogData ──────────────────────────────────────────────────┘
-->
<template>
  <div class="page page--wide category-page">
    <!--
      状态一：用户未登录
      路由守卫通常已重定向到首页，这里保留登录引导兜底
    -->
    <template v-if="!authStore.isLoggedIn">
      <header class="page-head">
        <span class="eyebrow">NEST / 学科知识地图</span>
        <h1 class="page-title">请先登录后查看</h1>
        <p class="page-sub">登录后即可进入你被授权的学科知识地图。</p>
      </header>
      <EmptyState icon="fas fa-lock" title="目录需要登录后展开"
        text="请先登录后查看。登录后这里会展示你被授权的学科文档、文件夹结构与待提交任务。">
        <router-link to="/" class="btn btn--primary">
          <i class="fas fa-right-to-bracket"></i> 返回首页登录
        </router-link>
      </EmptyState>
    </template>

    <!-- 状态二：目录数据加载中 -->
    <template v-else-if="blogStore.isLoading && !blogStore.blogData.length">
      <div class="category-loading" aria-busy="true">
        <div class="loading-line"><span class="spinner" aria-hidden="true"></span>正在展开学科地图…</div>
        <div class="skeleton category-loading__hero"></div>
        <div class="grid grid--3">
          <div class="skeleton category-loading__stat"></div>
          <div class="skeleton category-loading__stat"></div>
          <div class="skeleton category-loading__stat"></div>
        </div>
        <div class="skeleton category-loading__list"></div>
      </div>
    </template>

    <!--
      状态三：用户已登录但无有效数据
      未选择科目（currentSubject 为空）或权限过滤后目录为空
    -->
    <template v-else-if="!currentSubject || filteredArticles.length === 0">
      <header class="page-head">
        <span class="eyebrow">NEST / 学科知识地图</span>
        <h1 class="page-title">请从首页选择科目</h1>
        <p class="page-sub">在学科大厅选择一门科目，知识地图会在这里展开。</p>
      </header>
      <EmptyState icon="fas fa-compass" title="还没有可展示的目录"
        text="请从首页选择科目。如果科目卡片为空，请联系管理员开放学习权限。">
        <router-link to="/" class="btn btn--primary">
          <i class="fas fa-house"></i> 返回首页
        </router-link>
      </EmptyState>
    </template>

    <!--
      状态四：知识地图
      学科杂志封面 → 统计条 → 搜索/展开工具条 → 纸面目录树
    -->
    <template v-else>
      <section class="map-hero" :class="subjectTheme.coverClass" :aria-label="`${currentSubject} 学科知识地图`">
        <!-- 左页：学科色块 + 大标题 -->
        <div class="map-hero__cover">
          <span class="map-hero__watermark" aria-hidden="true">{{ subjectTheme.watermark }}</span>
          <div class="map-hero__cover-top">
            <router-link to="/" class="map-hero__back">
              <i class="fas fa-arrow-left" aria-hidden="true"></i> 学科大厅
            </router-link>
            <span class="map-hero__code mono">{{ subjectTheme.code }}</span>
          </div>

          <p class="map-hero__kicker mono">SUBJECT KNOWLEDGE MAP</p>
          <h1 class="map-hero__title">
            {{ currentSubject }}<span class="map-hero__title-mark">知识地图</span>
          </h1>
          <p class="map-hero__blurb">{{ subjectTheme.blurb }}</p>

          <div class="map-hero__chips">
            <span class="chip">
              <i :class="`fas ${subjectTheme.icon}`" aria-hidden="true"></i>{{ currentSubject }}
            </span>
            <span class="chip">{{ folderCount }} 个文件夹</span>
            <span class="chip" :class="{ 'map-hero__chip--alert': pendingTotal > 0 }">
              <i :class="['fas', pendingTotal > 0 ? 'fa-circle-exclamation' : 'fa-circle-check']"
                aria-hidden="true"></i>
              {{ pendingTotal > 0 ? `${pendingTotal} 题待提交` : '暂无待提交' }}
            </span>
          </div>
        </div>

        <!-- 右页：白边照片 + 胶带贴纸 -->
        <div class="map-hero__art">
          <span class="map-hero__block" aria-hidden="true"></span>
          <figure class="map-hero__photo">
            <img v-if="!coverBroken" class="map-hero__img" :src="subjectTheme.cover"
              :alt="`${currentSubject}学科知识地图封面插画`" decoding="async" @error="coverBroken = true" />
            <div v-else class="map-hero__fallback" aria-hidden="true">
              <i :class="`fas ${subjectTheme.icon}`"></i>
            </div>
          </figure>
          <span class="map-hero__tape" aria-hidden="true"></span>
          <span class="map-hero__caption mono">{{ currentSubject }} · NEST ARCHIVE</span>
        </div>
      </section>

      <!-- 统计条：文章数 / 待提交题数 / 文件夹数 -->
      <RevealOnScroll :delay="0.05">
        <div class="stat-grid">
          <div class="stat-card stat-card--cyan">
            <div class="stat-value"><CountUp :value="filteredArticles.length" /></div>
            <div class="stat-label">篇文档已授权</div>
            <div class="stat-sub">{{ currentSubject }} 系列</div>
          </div>
          <div class="stat-card stat-card--coral">
            <div class="stat-value"><CountUp :value="pendingTotal" /></div>
            <div class="stat-label">题待提交</div>
            <div class="stat-sub">来自学习进度</div>
          </div>
          <div class="stat-card stat-card--amber">
            <div class="stat-value"><CountUp :value="folderCount" /></div>
            <div class="stat-label">个知识文件夹</div>
            <div class="stat-sub">逐级展开浏览</div>
          </div>
        </div>
      </RevealOnScroll>

      <!-- 搜索 + 全部展开/收起 -->
      <RevealOnScroll :delay="0.1">
        <div class="panel panel--tight map-toolbar">
          <div class="map-toolbar__search">
            <label class="sr-only" for="category-search">搜索文档标题</label>
            <div class="input-wrap">
              <i class="fas fa-magnifying-glass" aria-hidden="true"></i>
              <input id="category-search" v-model="searchQuery" class="input map-toolbar__input" type="search"
                placeholder="搜索文档标题…" autocomplete="off" spellcheck="false" />
              <button v-if="searchQuery" type="button" class="input-wrap__action" aria-label="清空搜索"
                @click="clearSearch">
                <i class="fas fa-xmark" aria-hidden="true"></i>
              </button>
            </div>
          </div>

          <div class="map-toolbar__actions">
            <span class="map-toolbar__count">
              显示 <strong class="mono">{{ visibleArticleCount }}</strong> / <span class="mono">{{ totalArticleCount }}</span> 篇
            </span>
            <button type="button" class="btn btn--sm btn--soft" @click="toggleAllExpanded">
              <i :class="['fas', allExpanded ? 'fa-compress' : 'fa-expand']" aria-hidden="true"></i>
              {{ allExpanded ? '全部收起' : '全部展开' }}
            </button>
          </div>
        </div>
      </RevealOnScroll>

      <!-- 目录树 -->
      <section class="map-tree-section" aria-label="文档目录">
        <p v-if="isSearching" class="map-tree-section__hint">
          <i class="fas fa-magnifying-glass" aria-hidden="true"></i>
          搜索「{{ searchQuery.trim() }}」· 命中 <strong class="mono">{{ visibleArticleCount }}</strong> 篇文档
        </p>

        <div v-if="isSearching && displayTree.length === 0" class="empty">
          <span class="empty__icon"><i class="fas fa-magnifying-glass"></i></span>
          <p class="empty__title">没有匹配的文档</p>
          <p class="empty__text">试试更短的关键词，比如「反应」「语法」或「练习」。</p>
          <div class="empty__actions">
            <button type="button" class="btn btn--soft btn--sm" @click="clearSearch">清空搜索</button>
          </div>
        </div>

        <div v-else class="map-tree">
          <template v-for="node in displayTree" :key="node.isFile ? `file-${node.blogId}` : `dir-${node.name}`">
            <FolderNode v-if="!node.isFile" :node="node" :depth="0" :status-map="submissionStatusMap"
              :accent="subjectTheme.accent" :expand-token="expandToken" :expand-to="expandTo"
              @file-click="navigateToDetail" />
            <FileNode v-else :node="node" :depth="0" :status-map="submissionStatusMap" :accent="subjectTheme.accent"
              @click="navigateToDetail(node.blogId)" />
          </template>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
/**
 * 组件逻辑层
 *
 * 核心职责：
 * 1. 管理当前科目状态（currentSubject）
 * 2. 计算属性生成过滤后博客列表（filteredArticles）
 * 3. 将博客列表转换为目录树结构（treeData）
 * 4. 加载博客提交状态（submissionStatusMap）
 * 5. 处理文件节点的导航跳转
 * 6. （展示层增强）本地搜索、全部展开/收起、学科印刷色主题
 */

// ==================== 外部依赖导入 ====================

import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { learningGateway } from '@/gateways/learningGateway'

// 子组件：目录文件夹节点 / 目录文件节点
import FolderNode from '@/components/articles/FolderNode.vue'
import FileNode from '@/components/articles/FileNode.vue'
// 公共 UI：空状态 / 滚动入场 / 数字滚动
import EmptyState from '@/components/ui/EmptyState.vue'
import RevealOnScroll from '@/components/ui/RevealOnScroll.vue'
import CountUp from '@/components/ui/CountUp.vue'

// ==================== 学科主题（纯展示映射） ====================

/**
 * 学科主题映射：封面统一使用站内 cards 图片（BASE_URL 兼容部署子路径），
 * 加载失败时回落到纯色块 + 图标
 * - accent      驱动文件夹卡片的印刷色标签（cyan/violet/amber → 天蓝/钴蓝/奶油黄）
 * - coverClass  驱动杂志封面的色块配色（高中化学=钴蓝+天蓝，高中英语=朱红+奶油黄，初中英语=黄绿+墨黑）
 */
const CARD_BASE = `${import.meta.env.BASE_URL}`

const SUBJECT_THEMES = {
  '高中化学': {
    accent: 'cyan',
    cover: `${CARD_BASE}subjects/chem-bg.png`,
    coverClass: 'cover--cobalt',
    icon: 'fa-flask',
    code: 'CHEM / 01',
    watermark: 'CHEMISTRY',
    blurb: '从物质的量到电化学，把反应、结构与能量串成一张可展开的知识地图。'
  },
  '高中英语': {
    accent: 'violet',
    cover: `${CARD_BASE}subjects/en-bg.png`,
    coverClass: 'cover--vermilion',
    icon: 'fa-language',
    code: 'ENG / 02',
    watermark: 'ENGLISH',
    blurb: '语法、词法、写作与精读，顺着语感的洋流把语言拆成可复用的结构。'
  },
  '初中英语': {
    accent: 'amber',
    cover: `${CARD_BASE}subjects/junior-en-bg.png`,
    coverClass: 'cover--lime',
    icon: 'fa-graduation-cap',
    code: 'J-ENG / 03',
    watermark: 'JUNIOR ENGLISH',
    blurb: '从语法底层逻辑起步，先把基础打牢，再进入阅读与写作。'
  }
}

const DEFAULT_THEME = {
  accent: 'violet',
  cover: `${CARD_BASE}subjects/chem-bg.png`,
  coverClass: 'cover--cobalt',
  icon: 'fa-book-open',
  code: 'NEST / 00',
  watermark: 'NEST',
  blurb: '按文件夹逐级展开这节课的知识脉络，点击任意文档开始学习。'
}

// ==================== 路由与状态管理 ====================

/** Vue Router 路由实例，用于获取当前路由参数 */
const route = useRoute()
/** Vue Router 路由导航实例，用于编程式跳转 */
const router = useRouter()
/** 认证状态管理 store，提供登录状态和用户权限信息 */
const authStore = useAuthStore()
/** 博客数据管理 store，提供博客数据加载功能 */
const blogStore = useArticleStore()

// ==================== 响应式状态 ====================

/**
 * 当前选中的科目
 * 初始值从路由 query 参数 `subject` 获取
 * @type {import('vue').Ref<string>}
 */
const currentSubject = ref(route.query.subject || '')

/**
 * 博客提交状态映射表
 * 键：博客 ID（number）→ 值：待提交题目数
 * @type {import('vue').Ref<Map<number, number>>}
 */
const submissionStatusMap = ref(new Map())

/** 当前学科主题 */
const subjectTheme = computed(() => SUBJECT_THEMES[currentSubject.value] || DEFAULT_THEME)

/** 封面加载失败标记（失败时展示纯色块 + 图标兜底） */
const coverBroken = ref(false)

/** 本地搜索关键词 */
const searchQuery = ref('')

/** 文件夹统一展开信号：自增数值 + 目标状态，下发给递归节点 */
const expandToken = ref(0)
const expandTo = ref(false)
const allExpanded = ref(false)

// ==================== 计算属性 ====================

/** 标准化后的搜索关键词 */
const searchTerm = computed(() => searchQuery.value.trim().toLowerCase())

/** 是否处于搜索状态 */
const isSearching = computed(() => searchTerm.value.length > 0)

/**
 * 过滤后的博客列表
 *
 * 过滤逻辑：
 * 1. 当前科目为空时返回空数组
 * 2. 从 authStore 获取用户的权限 ID 列表，转换为有效数字
 * 3. 筛选条件：
 *    a. 博客所属系列（series）等于当前科目
 *    b. 博客 ID 在用户权限范围内
 *
 * @type {import('vue').ComputedRef<Array>}
 */
const filteredArticles = computed(() => {
  // 未选择科目时直接返回空
  if (!currentSubject.value) return []

  // 获取用户权限 ID 列表：转为 Number 并过滤非法数值（NaN、Infinity）
  const permissionIds = authStore.getPermissionIds()
    .map(Number)
    .filter(Number.isFinite)

  // 双条件过滤：科目匹配 + 权限校验
  return blogStore.blogData.filter(b =>
    b.series === currentSubject.value &&
    permissionIds.includes(Number(b.id))
  )
})

/**
 * 博客目录树结构
 *
 * 构建时机：filteredArticles 变化时自动重新计算
 * 构建结果：根级节点数组（文件夹和文件混排，文件夹在前）
 *
 * @type {import('vue').ComputedRef<Array>}
 */
const treeData = computed(() => {
  if (!filteredArticles.value.length) return []
  return buildTree(filteredArticles.value)
})

/** 搜索过滤后的目录树（不改变数据源） */
const displayTree = computed(() => {
  if (!isSearching.value) return treeData.value
  return filterTree(treeData.value, searchTerm.value)
})

/** 文件夹总数（递归统计整棵树） */
const folderCount = computed(() => countFolders(treeData.value))

/** 全树文档篇数 */
const totalArticleCount = computed(() => countFiles(treeData.value))

/** 当前可见（搜索命中）文档篇数 */
const visibleArticleCount = computed(() => countFiles(displayTree.value))

/** 待提交题目总数（来自 submissionStatusMap，仅含当前科目） */
const pendingTotal = computed(() => {
  let sum = 0
  for (const value of submissionStatusMap.value.values()) {
    sum += Number(value) || 0
  }
  return sum
})

// ==================== 工具函数 ====================

/**
 * 构建博客目录树
 *
 * 算法说明：
 * 1. 遍历所有博客对象，解析每条博客的路径（path）
 * 2. 将路径按 "/" 分割，去掉第一个段（系列名，与 subject 重复），得到目录层级数组
 * 3. 逐层查找或创建文件夹节点
 * 4. 在最终层级创建文件节点，携带博客元数据（ID、日期、系列等）
 * 5. 递归排序：文件夹优先，同级按名称字母序排列
 *
 * 示例转换：
 *   输入：{ path: "高中化学/理论课讲义/0 物质的量", title: "0 物质的量", id: 3 }
 *   输出：{ name: "理论课讲义" → children: [{ name: "0 物质的量", isFile: true, blogId: 3 }] }
 *
 * @param {Array} articles - 待构建的博客数据数组
 * @returns {Array} 构建完成的根级节点数组
 */
function buildTree(articles) {
  // 虚拟根节点，用于统一处理逻辑
  const root = { children: [] }

  articles.forEach(blog => {
    // 分割路径并去掉系列名（路径形如 "高中化学/理论课讲义/文档.md"）
    const parts = blog.path.split('/')
    const dirParts = parts.slice(1)
    const title = blog.title

    // 当前指针，从根节点开始
    let current = root

    // 逐层处理目录部分
    for (let i = 0; i < dirParts.length; i++) {
      const part = dirParts[i]

      // 判断是否为最后一层（文件层）
      if (i === dirParts.length - 1) {
        // 在文件层：创建文件节点（去重处理）
        if (!current.children.find(child => child.name === title && child.isFile)) {
          current.children.push({
            name: title,
            isFile: true,
            blogId: blog.id,
            date: blog.date,
            series: blog.series
          })
        }
      } else {
        // 在文件夹层：查找或创建文件夹节点
        let dirNode = current.children.find(child => child.name === part && !child.isFile)
        if (!dirNode) {
          dirNode = { name: part, isFile: false, children: [] }
          current.children.push(dirNode)
        }
        // 进入下一层级
        current = dirNode
      }
    }
  })

  /**
   * 递归排序节点
   * 排序规则：文件夹在前（isFile: false），文件在后（isFile: true），同级按名称升序
   *
   * @param {Object} node - 待排序的节点对象
   */
  function sortNode(node) {
    if (!node.children) return
    node.children.sort((a, b) => {
      // 类型不同时：文件夹优先级更高（排在前面）
      if (a.isFile !== b.isFile) return a.isFile ? 1 : -1
      // 类型相同时：按名称字母序排列
      return a.name.localeCompare(b.name)
    })
    // 递归处理子节点的排序
    node.children.forEach(child => {
      if (!child.isFile) sortNode(child)
    })
  }
  sortNode(root)

  // 返回根节点的 children，即顶层节点数组
  return root.children
}

/**
 * 按标题过滤目录树：文件夹只保留存在命中子节点的分支
 *
 * @param {Array} nodes - 节点数组
 * @param {string} term - 小写搜索词
 * @returns {Array} 过滤后的节点数组（新对象，不修改原树）
 */
function filterTree(nodes, term) {
  const result = []
  for (const node of nodes || []) {
    if (node.isFile) {
      if (String(node.name || '').toLowerCase().includes(term)) result.push(node)
      continue
    }
    const children = filterTree(node.children, term)
    if (children.length) result.push({ ...node, children })
  }
  return result
}

/** 递归统计文件夹节点数 */
function countFolders(nodes) {
  let total = 0
  for (const node of nodes || []) {
    if (node.isFile) continue
    total += 1 + countFolders(node.children)
  }
  return total
}

/** 递归统计文件节点数 */
function countFiles(nodes) {
  let total = 0
  for (const node of nodes || []) {
    if (node.isFile) total += 1
    else total += countFiles(node.children)
  }
  return total
}

// ==================== 交互处理 ====================

/** 统一展开 / 收起所有文件夹 */
function setAllExpanded(expanded) {
  allExpanded.value = expanded
  expandTo.value = expanded
  expandToken.value += 1
}

function toggleAllExpanded() {
  setAllExpanded(!allExpanded.value)
}

function clearSearch() {
  searchQuery.value = ''
}

/**
 * 导航到博客详情页
 *
 * @param {number|string} blogId - 目标博客的唯一标识符
 */
function navigateToDetail(blogId) {
  router.push(`/blog/${blogId}`)
}

// 搜索时自动展开命中的文件夹
watch(searchTerm, (term) => {
  if (term) setAllExpanded(true)
})

// 切换科目时重置搜索与封面兜底状态
watch(currentSubject, () => {
  searchQuery.value = ''
  coverBroken.value = false
})

// ==================== 生命周期与监听 ====================

/**
 * 组件挂载后执行：
 * 1. 加载博客数据（从 blogStore 获取全量数据）
 * 2. 根据过滤后的博客 ID 列表，异步加载提交状态
 *
 * 前置条件：用户已登录
 */
onMounted(async () => {
  if (authStore.isLoggedIn) {
    // 触发博客数据加载（异步操作）
    await blogStore.loadArticleData()

    // 提取当前可见博客的 ID 列表
    const blogIds = filteredArticles.value.map(b => b.id)
    if (blogIds.length > 0) {
      // 批量查询提交状态
      submissionStatusMap.value = await loadSubmissionStatus(blogIds)
    }
  }
})

// 服务端会话异步恢复完成后同步加载数据
watch(() => authStore.isLoggedIn, async (loggedIn) => {
  if (loggedIn) {
    await blogStore.loadArticleData()
    const blogIds = filteredArticles.value.map(b => b.id)
    if (blogIds.length > 0) {
      submissionStatusMap.value = await loadSubmissionStatus(blogIds)
    }
  }
})

/**
 * 监听路由 query 参数 subject 的变化
 * 当用户从首页切换科目时，更新当前科目并触发数据重新计算
 *
 * @param {string} newSubject - 新的科目值
 */
watch(() => route.query.subject, async (newSubject) => {
  if (newSubject) {
    currentSubject.value = newSubject
    // 重新加载提交状态
    if (authStore.isLoggedIn) {
      await blogStore.loadArticleData()
      const blogIds = filteredArticles.value.map(b => b.id)
      if (blogIds.length > 0) {
        submissionStatusMap.value = await loadSubmissionStatus(blogIds)
      }
    }
  }
})

// ==================== 数据加载函数 ====================

/**
 * 批量加载博客提交状态
 *
 * 加载该学生有答案但未提交的题目数（待提交数量）
 *
 * @param {Array<number|string>} blogIds - 需要查询的博客 ID 列表
 * @returns {Promise<Map<number, number>>} 返回 blogId → 待提交题数 的映射
 */
async function loadSubmissionStatus(blogIds) {
  if (!blogIds.length) return new Map()
  let progress
  try {
    progress = await learningGateway.getLearnerProgress()
  } catch (error) {
    console.error('加载提交记录失败:', error)
    return new Map()
  }
  const result = new Map()
  const requested = new Set(blogIds.map(Number))
  progress.forEach(item => {
    const bid = Number(item.articleId)
    if (!requested.has(bid)) return
    const pending = item.totalQuestions - item.submittedQuestions
    if (pending > 0) {
      result.set(bid, pending)
    }
  })
  return result
}
</script>

<style scoped>
/* ===================================================================
   学科知识地图 —— Riso Campus 纸面布局
   =================================================================== */

.category-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

/* ---------- 学科杂志封面 ---------- */
.map-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(0, 0.94fr);
  border: 1.5px solid var(--ink);
  border-radius: var(--r-lg);
  background: var(--paper-1);
  box-shadow: var(--shadow-float);
  overflow: hidden;
}

/* 封面左页：学科大色块 */
.map-hero__cover {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 30px 30px 32px;
  background: var(--cover-bg, var(--cobalt));
  color: var(--cover-ink, var(--paper-1));
  overflow: hidden;
}

/* 超大号学科英文水印：贴右下角裁切，低透明度填充色块 */
.map-hero__watermark {
  position: absolute;
  right: -0.06em;
  bottom: -0.14em;
  z-index: 0;
  max-width: 130%;
  font-family: var(--font-display);
  font-size: clamp(3.4rem, 8.5vw, 7rem);
  font-weight: 800;
  line-height: 0.8;
  letter-spacing: -0.05em;
  white-space: nowrap;
  color: var(--cover-ink, var(--paper-1));
  opacity: 0.16;
  pointer-events: none;
  user-select: none;
}

.cover--lime .map-hero__watermark {
  color: var(--ink);
  opacity: 0.18;
}

/* 水印之上的封面内容 */
.map-hero__cover > :not(.map-hero__watermark) {
  position: relative;
  z-index: 1;
}

.map-hero__cover::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2;
  height: 10px;
  background: var(--cover-accent, var(--sky));
  border-top: 1.5px solid var(--ink);
}

.cover--cobalt {
  --cover-bg: var(--cobalt);
  --cover-ink: var(--paper-1);
  --cover-accent: var(--sky);
  --cover-mark: var(--sky);
  --cover-mark-ink: var(--ink);
}

.cover--vermilion {
  --cover-bg: var(--vermilion);
  --cover-ink: var(--paper-1);
  --cover-accent: var(--butter);
  --cover-mark: var(--butter);
  --cover-mark-ink: var(--ink);
}

.cover--lime {
  --cover-bg: var(--lime);
  --cover-ink: var(--ink);
  --cover-accent: var(--ink);
  --cover-mark: var(--ink);
  --cover-mark-ink: var(--paper-1);
}

.map-hero__cover-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.map-hero__back {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 12px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  background: var(--paper-1);
  color: var(--ink);
  font-size: 0.78rem;
  font-weight: 700;
  box-shadow: 2px 2px 0 var(--ink);
  transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);
}

.map-hero__back:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 var(--ink);
}

.map-hero__code {
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.22em;
  color: inherit;
  opacity: 0.82;
}

.map-hero__kicker {
  margin-top: 6px;
  font-size: 0.66rem;
  font-weight: 600;
  letter-spacing: 0.26em;
  color: inherit;
  opacity: 0.78;
}

.map-hero__title {
  font-size: clamp(2.2rem, 4.6vw, 3.6rem);
  line-height: 1.04;
  color: var(--cover-ink, var(--paper-1));
}

.map-hero__title-mark {
  display: inline-block;
  margin-left: 0.5rem;
  padding: 0.08em 0.42em;
  font-size: 0.3em;
  font-weight: 800;
  letter-spacing: 0.04em;
  vertical-align: middle;
  transform: rotate(-1.5deg);
  background: var(--cover-mark, var(--butter));
  color: var(--cover-mark-ink, var(--ink));
  border: 1.5px solid var(--ink);
  border-radius: 6px;
  box-shadow: 2px 2px 0 var(--ink);
}

.map-hero__blurb {
  max-width: 34ch;
  font-size: 0.9rem;
  color: inherit;
  opacity: 0.92;
}

.map-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
  padding-top: 8px;
}

.map-hero__chips .chip {
  box-shadow: 2px 2px 0 var(--ink);
}

.map-hero__chip--alert {
  background: var(--butter);
}

/* 封面右页：白边照片 + 胶带 */
.map-hero__art {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 300px;
  padding: 26px 22px;
  background-color: var(--paper-2);
  background-image: radial-gradient(var(--line-strong) 1px, transparent 1.2px);
  background-size: 16px 16px;
  border-left: 1.5px solid var(--ink);
  overflow: hidden;
}

.map-hero__block {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 58%;
  max-width: 250px;
  aspect-ratio: 4 / 5;
  background: var(--cover-accent, var(--sky));
  border: 1.5px solid var(--ink);
  transform: translate(calc(-50% + 14px), calc(-50% + 10px)) rotate(5deg);
  pointer-events: none;
}

.map-hero__photo {
  position: relative;
  width: min(60%, 232px);
  padding: 9px;
  background: var(--paper-1);
  border: 1.5px solid var(--ink);
  box-shadow: var(--shadow-card);
  transform: rotate(-3deg);
  transition: transform var(--dur-slow) var(--ease-out), box-shadow var(--dur-slow) var(--ease-out);
}

.map-hero__photo:hover {
  transform: rotate(-1deg) translateY(-4px);
  box-shadow: var(--shadow-float);
}

.map-hero__img {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  border: 1px solid var(--line-strong);
}

.map-hero__fallback {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 3 / 4;
  border: 1px solid var(--line-strong);
  background: var(--cover-accent, var(--sky));
  color: var(--ink);
  font-size: 2.6rem;
}

.map-hero__tape {
  position: absolute;
  top: 26px;
  left: calc(50% - 96px);
  width: 104px;
  height: 26px;
  background: var(--butter);
  border: 1.5px solid var(--ink);
  transform: rotate(-7deg);
  box-shadow: 2px 2px 0 var(--ink);
  pointer-events: none;
}

.map-hero__caption {
  position: absolute;
  left: 18px;
  bottom: 14px;
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--text-3);
}

/* ---------- 统计条 ---------- */
.stat-card .stat-value {
  min-height: 1em;
  display: block;
}

.stat-card .stat-label {
  color: var(--ink-soft);
}

.stat-card .stat-sub {
  color: var(--ink-soft);
  opacity: 0.72;
}

/* ---------- 工具条 ---------- */
.map-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.map-toolbar__search {
  flex: 1 1 280px;
  min-width: 200px;
}

.input-wrap .map-toolbar__input {
  height: 46px;
  padding-right: 46px;
  border-radius: var(--r-sm);
}

.map-toolbar__input::-webkit-search-cancel-button {
  display: none;
}

.map-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-left: auto;
}

.map-toolbar__count {
  color: var(--text-3);
  font-size: 0.8rem;
}

.map-toolbar__count strong {
  color: var(--ink);
  font-weight: 800;
}

/* ---------- 目录树 ---------- */
.map-tree-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.map-tree-section__hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-3);
  font-size: 0.8rem;
}

.map-tree-section__hint strong {
  color: var(--vermilion);
  font-weight: 800;
}

.map-tree {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* ---------- 加载骨架 ---------- */
.category-loading {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.category-loading__hero {
  height: 220px;
  border-radius: var(--r-lg);
}

.category-loading__stat {
  height: 104px;
  border-radius: var(--r-md);
}

.category-loading__list {
  height: 180px;
  border-radius: var(--r-lg);
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .map-hero {
    grid-template-columns: minmax(0, 1fr);
  }

  .map-hero__cover {
    padding: 22px 20px 26px;
  }

  .map-hero__watermark {
    font-size: clamp(3rem, 15vw, 4.6rem);
    opacity: 0.15;
  }

  .map-hero__art {
    min-height: 250px;
    border-left: 0;
    border-top: 1.5px solid var(--ink);
  }

  .map-hero__photo {
    width: min(48%, 200px);
  }

  .map-hero__block {
    width: 44%;
    max-width: 190px;
  }

  .map-hero__tape {
    left: calc(50% - 82px);
  }
}

@media (max-width: 760px) {
  .category-page {
    gap: 18px;
  }

  .map-hero__cover {
    padding: 18px 16px 24px;
  }

  .map-hero__title {
    font-size: 2.1rem;
  }

  .map-hero__title-mark {
    display: inline-block;
    margin-left: 0.35rem;
  }

  .map-hero__art {
    min-height: 210px;
    padding: 22px 16px;
  }

  .map-hero__photo {
    width: min(56%, 176px);
    padding: 7px;
  }

  .map-hero__block {
    width: 48%;
    max-width: 160px;
  }

  .map-hero__tape {
    top: 18px;
    left: calc(50% - 70px);
    width: 86px;
    height: 22px;
  }

  .map-toolbar {
    gap: 10px;
  }

  .map-toolbar__search {
    flex: 1 1 100%;
  }

  .map-toolbar__actions {
    width: 100%;
    margin-left: 0;
    justify-content: space-between;
  }

  .map-tree {
    gap: 5px;
  }
}
</style>
