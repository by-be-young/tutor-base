<!-- src/views/admin/AdminCoursewareOverviewView.vue -->
<template>
  <div class="overview">
    <!-- 课件页合并了原来的「设置答案」与「共享」页：三个标签共用顶部概览 -->
    <div class="seg overview__tabs" role="tablist" aria-label="课件工作区">
      <button v-for="tab in tabs" :key="tab.id" type="button" role="tab" class="seg__btn"
        :class="{ 'is-active': activeTab === tab.id }" :aria-selected="activeTab === tab.id"
        :aria-controls="`courseware-tab-${tab.id}`" @click="switchTab(tab.id)">
        <i :class="tab.icon" aria-hidden="true"></i>
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <template v-if="activeTab === 'tree'">
      <section class="panel panel--tight overview__lead">
        <p class="eyebrow">课件托管</p>
        <p class="overview__lead-text">
          每个科目一个发布版本：草稿只有你自己能看到，点「发布」才生效；删除草稿不会影响已发布内容。
          点目录里的篇目会在新页面里用学员阅读器完整预览（草稿会标注「学员还看不到」），要改内容再点「去编辑」。
          所有正文都从数据库托管版本读取，页面不再直接读取 <code>public/articles</code> 下的静态文件。
          删除一篇课件会同时清掉它的草稿、答案、共享与学员权限，并从当前发布版本里移除。
        </p>
      </section>

      <div class="stat-grid">
        <div class="stat-card">
          <span class="stat-label">科目</span>
          <span class="stat-value">{{ subjects.length }}</span>
          <span class="stat-sub">已发布版本 + 草稿</span>
        </div>
        <div class="stat-card stat-card--amber">
          <span class="stat-label">待发布草稿</span>
          <span class="stat-value">{{ data.pendingDrafts }}</span>
          <span class="stat-sub">草稿需点发布才生效</span>
        </div>
        <div class="stat-card stat-card--cyan">
          <span class="stat-label">获取的课件</span>
          <span class="stat-value">{{ data.acquiredCount }}</span>
          <span class="stat-sub">来自其他老师</span>
        </div>
        <div class="stat-card" :class="{ 'stat-card--coral': data.pendingUpdates > 0 }">
          <span class="stat-label">有更新</span>
          <span class="stat-value">{{ data.pendingUpdates }}</span>
          <span class="stat-sub">来源发布了新版本</span>
        </div>
      </div>

      <section class="panel panel--pad">
        <header class="panel-head overview__head">
          <h2 class="panel-title">存储空间</h2>
          <span class="chip" :class="usageChipClass">{{ usageLabel }}</span>
        </header>
        <div class="panel-body overview__usage">
          <div class="progress" :class="usageProgressClass" role="img"
            :aria-label="`已用 ${formatBytes(usage.totalBytes)}，配额 ${quotaText}`">
            <span class="progress__bar" :style="{ width: usageWidth }"></span>
          </div>
          <p class="overview__usage-line">
            <strong>{{ formatBytes(usage.totalBytes) }}</strong>
            <span class="text-muted"> / {{ quotaText }}</span>
            <span v-if="usage.orphanCount" class="text-dim">
              · 其中 {{ usage.orphanCount }} 个孤儿文件（{{ formatBytes(usage.orphanBytes) }}）可回收
            </span>
          </p>
        </div>
      </section>

      <div v-if="loading" class="empty" role="status">
        <span class="spinner" aria-hidden="true"></span>
        <p class="empty__text">正在加载课件概览…</p>
      </div>

      <div v-else-if="!subjects.length" class="empty">
        <i class="fas fa-folder-open empty__icon" aria-hidden="true"></i>
        <p class="empty__title">还没有任何科目</p>
        <p class="empty__text">
          在目录的「＋」里新建课件会同时建好科目目录；正文会保存到服务端托管版本，供学员端和预览统一读取。
        </p>
      </div>

      <div v-else class="overview__workspace">
        <section class="panel panel--pad overview__tree">
          <header class="panel-head overview__tree-head">
            <h2 class="panel-title">课件目录</h2>
            <div class="overview__tree-actions">
              <span class="overview__tree-hint">＋ 新建课件 · 文件夹＋ 新建子目录 · 点篇目预览</span>
              <button type="button" class="btn btn--ghost btn--sm" @click="toggleAll">
                {{ expanded ? '全部收起' : '全部展开' }}
              </button>
            </div>
          </header>
          <div class="panel-body overview__subjects">
            <section v-for="(item, index) in subjects" :key="item.subject" class="overview__subject">
              <!-- 科目目录直接复用站内的文件夹树（与学员端知识地图同一套 FolderNode/FileNode），
                   层级按 logicalPath 还原，可以一层层展开到具体讲义。 -->
              <FolderNode
                :node="treeOf(item.subject)"
                :accent="accentFor(index)"
                :status-map="emptyStatus"
                :expand-token="expandToken"
                :expand-to="expanded"
                mode="admin"
                @file-click="openPreview(item.subject, $event)"
                @file-move="openMoveDialog(item.subject, $event)"
                @file-delete="openDeleteDialog(item.subject, $event)"
                @create-file="createArticleIn"
                @create-folder="createFolderIn"
              />
              <p class="overview__subject-meta">
                <span v-if="item.version" class="chip chip--lime mono">v{{ item.version }}</span>
                <span v-else class="chip chip--amber mono">未发布</span>
                <span class="overview__subject-stat">草稿 {{ item.draftCount }}</span>
                <span class="overview__subject-stat">被获取 {{ item.copiedCount }}</span>
                <span class="overview__subject-stat">分享 {{ item.shareCount }}</span>
                <span v-if="item.publishedAt" class="overview__subject-stat">
                  发布于 {{ formatDate(item.publishedAt) }}
                </span>
              </p>
            </section>
          </div>
        </section>
      </div>
    </template>

    <section v-else-if="activeTab === 'answers'" id="courseware-tab-answers" class="overview__panel" role="tabpanel">
      <div class="panel panel--tight answers-bar">
        <div class="answers-bar__switcher">
          <span class="eyebrow">CURRENT LEARNER</span>
          <StudentSwitcher @select="handleSelectStudent" />
        </div>
        <p class="answers-bar__hint">
          <i aria-hidden="true" class="fas fa-circle-info"></i>
          答案按学员范围维护：只列出你名下、且该学员已开通科目里已授权的课件。
        </p>
      </div>
      <AnswerPanel :blog-data="blogData" :all-subjects="allSubjects" :students="adminStore.students"
        :selected-student-id="adminStore.currentStudentId" />
    </section>

    <section v-else id="courseware-tab-sharing" class="overview__panel" role="tabpanel">
      <AdminSharingView />
    </section>

    <!-- 移动课件对话框 -->
    <div v-if="moveDialog.open" class="move-overlay" @click.self="closeMoveDialog">
      <div class="move-dialog" role="dialog" aria-modal="true" aria-labelledby="move-dialog-title">
        <h3 class="move-dialog__title" id="move-dialog-title">
          <i class="fas fa-folder-move" aria-hidden="true"></i>
          移动课件
        </h3>
        <p class="move-dialog__current">
          <span class="text-dim">当前路径：</span>
          <code class="mono">{{ moveDialog.currentPath }}</code>
        </p>
        <label class="move-dialog__label" for="new-path-input">新路径（含科目前缀和文件名）</label>
        <input id="new-path-input" v-model="moveDialog.newPath" class="move-dialog__input" type="text"
          placeholder="例如：高中英语/语法课讲义/虚拟语气.md" @keydown.enter="confirmMove" />
        <div v-if="moveDialog.suggestions.length" class="move-dialog__sugs">
          <span class="text-dim">已有目录：</span>
          <button v-for="dir in moveDialog.suggestions" :key="dir" type="button" class="move-dialog__sug"
            @click="applySuggestion(dir)">
            {{ dir }}
          </button>
        </div>
        <div class="move-dialog__actions">
          <button type="button" class="btn btn--ghost btn--sm" @click="closeMoveDialog">取消</button>
          <button type="button" class="btn btn--primary btn--sm" :disabled="moveDialog.busy || !moveDialog.newPath.trim()"
            @click="confirmMove">
            <i v-if="moveDialog.busy" class="fas fa-circle-notch fa-spin"></i>
            {{ moveDialog.busy ? '移动中…' : '确认移动' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 删除课件确认对话框：删除会连带清掉草稿、答案、共享与学员权限，必须二次确认 -->
    <div v-if="deleteDialog.open" class="move-overlay" @click.self="closeDeleteDialog">
      <div class="move-dialog delete-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-dialog-title">
        <h3 class="move-dialog__title delete-dialog__title" id="delete-dialog-title">
          <i class="fas fa-triangle-exclamation" aria-hidden="true"></i>
          删除课件
        </h3>
        <p class="delete-dialog__name">{{ deleteDialog.title }}</p>
        <p v-if="deleteDialog.path" class="move-dialog__current">
          <span class="text-dim">路径：</span>
          <code class="mono">{{ deleteDialog.path }}</code>
        </p>
        <ul class="delete-dialog__list">
          <li>从当前发布版本移除（不可撤销，其它老师手里的副本也会失效）</li>
          <li>删除它的草稿、答案设置、单篇共享</li>
          <li>删除学员权限里的这篇授权</li>
        </ul>
        <div class="move-dialog__actions">
          <button type="button" class="btn btn--ghost btn--sm" :disabled="deleteDialog.busy" @click="closeDeleteDialog">
            取消
          </button>
          <button type="button" class="btn btn--primary btn--sm delete-dialog__confirm"
            :disabled="deleteDialog.busy" @click="confirmDelete">
            <i v-if="deleteDialog.busy" class="fas fa-circle-notch fa-spin"></i>
            {{ deleteDialog.busy ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FolderNode from '@/components/articles/FolderNode.vue'
import AnswerPanel from '@/components/admin/AnswerPanel.vue'
import StudentSwitcher from '@/components/admin/StudentSwitcher.vue'
import AdminSharingView from '@/views/admin/AdminSharingView.vue'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { ensureArticleData } from '@/composables/useAdminWorkspace'
import { useAdminStore } from '@/stores/adminStore'
import { useArticleStore } from '@/stores/blogStore'
import { useAuthStore } from '@/stores/authStore'
import { useCoursewareStore } from '@/stores/coursewareStore'
import { loadCoursewareLibrary } from '@/utils/coursewareLibrary'
import { buildCoursewareTree } from '@/utils/coursewareTree'
import { showToast } from '@/utils/toast'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()
const blogStore = useArticleStore()
const authStore = useAuthStore()
const coursewareStore = useCoursewareStore()

/** 课件 / 设置答案 / 共享 三个标签；答案与共享原本是独立导航项，现已并入本页。 */
const tabs = [
  { id: 'tree', label: '课件目录', icon: 'fas fa-folder-tree' },
  { id: 'answers', label: '设置答案', icon: 'fas fa-key' },
  { id: 'sharing', label: '共享', icon: 'fas fa-share-nodes' }
]
const TAB_IDS = tabs.map(tab => tab.id)
const activeTab = ref(TAB_IDS.includes(route.query.tab) ? route.query.tab : 'tree')

function switchTab(tabId) {
  activeTab.value = tabId
  if (route.query.tab === tabId) return
  router.replace({ query: { ...route.query, tab: tabId === 'tree' ? undefined : tabId } })
}

// 旧链接 /admin/answers、/admin/sharing 会带 tab 进来；也支持浏览器前进后退
watch(() => route.query.tab, tab => {
  activeTab.value = TAB_IDS.includes(tab) ? tab : 'tree'
})

const blogData = computed(() => blogStore.blogData)
const allSubjects = computed(() => {
  const subjects = new Set()
  for (const blog of blogStore.blogData) {
    if (blog.series) subjects.add(blog.series)
  }
  return Array.from(subjects).sort((left, right) => left.localeCompare(right, 'zh-CN'))
})

const accountId = computed(() => authStore.currentUser?.accountId ?? null)

function handleSelectStudent(studentId) {
  adminStore.selectStudent(studentId)
}

const loading = ref(true)
const data = reactive({
  subjects: [],
  pendingDrafts: 0,
  acquiredCount: 0,
  pendingUpdates: 0,
  unreadNotifications: 0
})
const usage = reactive({
  totalBytes: 0,
  orphanCount: 0,
  orphanBytes: 0,
  quotaBytes: 0,
  usageRatio: 0,
  level: 'ok'
})

// 科目目录：数据来自「我的课件」全量清单，含已发布篇目与草稿（正文均由数据库托管）
const articles = ref([])
/** 已登记的空目录（`学科/子目录`）：课件目录树由路径推导，空目录靠这份登记留住。 */
const folders = ref([])
const emptyStatus = new Map()
const articlesBySubject = computed(() => {
  const groups = new Map()
  for (const article of articles.value) {
    const key = article.subject || '未归类'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(article)
  }
  return groups
})

function articlesOf(subjectValue) {
  return articlesBySubject.value.get(subjectValue) || []
}

function foldersOf(subjectValue) {
  return folders.value.filter(path => String(path || '').split('/')[0] === subjectValue)
}

const ACCENTS = ['cyan', 'violet', 'amber', 'coral', 'pink', 'lime']

function accentFor(index) {
  return ACCENTS[index % ACCENTS.length]
}

/** 目录展开状态：默认完全收起，全部展开/收起由 FolderNode 的 expand-token 统一驱动。 */
const expanded = ref(false)
const expandToken = ref(0)

function toggleAll() {
  expanded.value = !expanded.value
  expandToken.value += 1
}

// 预览：跳到完整的学员阅读页（/admin/preview/:id），不在本页内嵌近似效果
function openPreview(subjectValue, articleId) {
  const article = articles.value.find(item => Number(item.articleId) === Number(articleId))
  const query = { mode: 'preview', subject: subjectValue }
  if (article?.title) query.title = article.title
  if (article?.logicalPath) query.path = article.logicalPath
  router.push({ name: 'CoursewarePreview', params: { id: articleId }, query })
}

// ---- 在任意一级目录新建课件 ----
/** 按目录落点生成空白课件：名称即文件名，正文带骨架，发布后仍在这一级目录。 */
async function createArticleIn(folderPath) {
  const directory = String(folderPath || '').replace(/\/+$/, '')
  if (!directory) return

  const input = window.prompt(`在「${directory}」下新建课件，请输入文件名（.md 可省略）：`, '新课件')
  if (input == null) return
  const name = input.trim().replace(/\.md$/i, '').trim()
  if (!name) {
    showToast('文件名不能为空', 'warning')
    return
  }
  if (/[\\/:*?"<>|]/.test(name)) {
    showToast('文件名不能包含 \\ / : * ? " < > |', 'warning')
    return
  }

  const logicalPath = `${directory}/${name}.md`
  const markdown = `---\ntitle: ${name}\ndefaultLayout: A\n---\n\n<!-- @layout:A -->\n# ${name}\n\n在这里写正文\n`
  try {
    const saved = await coursewareGateway.saveDraft(null, markdown, null, { logicalPath, title: name })
    showToast(`已创建空白课件「${name}」`, 'success')
    router.push({
      path: '/admin/editor',
      query: { subject: directory.split('/')[0], articleId: saved.articleId }
    })
  } catch (error) {
    showToast(error?.message || '新建课件失败，请稍后重试', 'error')
  }
}

// ---- 在任意一级目录新建子目录 ----
/** 空目录没有课件可推导，必须登记到后端；建好后立即展开目录树，方便继续在里面放课件。 */
async function createFolderIn(folderPath) {
  const parent = String(folderPath || '').replace(/\/+$/, '')
  if (!parent) return

  const input = window.prompt(`在「${parent}」下新建子目录，请输入目录名：`, '新目录')
  if (input == null) return
  const name = input.trim().replace(/\/+$/, '')
  if (!name) {
    showToast('目录名不能为空', 'warning')
    return
  }
  if (/[\\/:*?"<>|]/.test(name)) {
    showToast('目录名不能包含 \\ / : * ? " < > |', 'warning')
    return
  }

  try {
    await coursewareGateway.createFolder(`${parent}/${name}`)
    showToast(`已创建子目录「${name}」`, 'success')
    await loadFolders()
    expanded.value = true
    expandToken.value += 1
  } catch (error) {
    showToast(error?.message || '新建子目录失败，请稍后重试', 'error')
  }
}

// ---- 移动课件 ----
const moveDialog = reactive({
  open: false,
  articleId: null,
  subject: '',
  currentPath: '',
  newPath: '',
  busy: false,
  suggestions: []
})

function openMoveDialog(subjectValue, articleId) {
  const article = articles.value.find(item => Number(item.articleId) === Number(articleId))
  if (!article) return
  moveDialog.articleId = Number(articleId)
  moveDialog.subject = subjectValue
  moveDialog.currentPath = article.logicalPath || ''
  moveDialog.newPath = article.logicalPath || ''
  moveDialog.busy = false
  // 收集同科目已有目录（取所有路径的父目录）
  const dirs = new Set()
  for (const a of articlesOf(subjectValue)) {
    const p = a.logicalPath || ''
    const idx = p.lastIndexOf('/')
    if (idx > 0) dirs.add(p.slice(0, idx + 1))
  }
  moveDialog.suggestions = Array.from(dirs).sort()
  moveDialog.open = true
}

function applySuggestion(dir) {
  // 保留原文件名，只换目录
  const fileName = moveDialog.currentPath.slice(moveDialog.currentPath.lastIndexOf('/') + 1)
  moveDialog.newPath = dir + fileName
}

function closeMoveDialog() {
  if (moveDialog.busy) return
  moveDialog.open = false
}

async function confirmMove() {
  if (!moveDialog.articleId || !moveDialog.newPath.trim() || moveDialog.busy) return
  moveDialog.busy = true
  try {
    await coursewareGateway.moveArticle(moveDialog.articleId, moveDialog.newPath.trim())
    showToast(`已移动到 ${moveDialog.newPath.trim()}`, 'success')
    moveDialog.open = false
    await Promise.all([loadArticles(), loadOverview()])
  } catch (error) {
    showToast(error?.message || '移动失败，请稍后重试', 'error')
  } finally {
    moveDialog.busy = false
  }
}

// ---- 删除课件 ----
/** 删除前弹窗确认：会连带清掉草稿、答案、共享与学员权限，属于不可撤销操作。 */
const deleteDialog = reactive({
  open: false,
  articleId: null,
  title: '',
  path: '',
  busy: false
})

function openDeleteDialog(subjectValue, articleId) {
  const article = articles.value.find(item => Number(item.articleId) === Number(articleId))
  deleteDialog.articleId = Number(articleId)
  deleteDialog.title = article?.title || `课件 ${articleId}`
  deleteDialog.path = article?.logicalPath || ''
  deleteDialog.busy = false
  deleteDialog.open = true
}

function closeDeleteDialog() {
  if (deleteDialog.busy) return
  deleteDialog.open = false
}

async function confirmDelete() {
  if (!deleteDialog.articleId || deleteDialog.busy) return
  deleteDialog.busy = true
  const removedTitle = deleteDialog.title
  try {
    await coursewareGateway.deleteArticle(deleteDialog.articleId)
    showToast(`已删除课件「${removedTitle}」`, 'success')
    deleteDialog.open = false
    await Promise.all([loadArticles(), loadOverview(), loadFolders()])
  } catch (error) {
    showToast(error?.message || '删除失败，请稍后重试', 'error')
  } finally {
    deleteDialog.busy = false
  }
}

/** 交给 FolderNode 的目录树：科目是根文件夹，logicalPath 的层级是多级子目录。 */
function treeOf(subjectValue) {
  return buildCoursewareTree(subjectValue, articlesOf(subjectValue), foldersOf(subjectValue))
}

/** 概览给的科目 + 只在「我的课件」清单或目录登记里出现的科目（例如只有草稿、还没发布） */
const subjects = computed(() => {
  const base = [...data.subjects]
  const known = new Set(base.map(item => item.subject))
  const extra = new Set()
  for (const subjectKey of articlesBySubject.value.keys()) {
    if (!known.has(subjectKey)) extra.add(subjectKey)
  }
  for (const path of folders.value) {
    const subjectKey = String(path || '').split('/')[0]
    if (subjectKey && !known.has(subjectKey)) extra.add(subjectKey)
  }
  for (const subjectKey of extra) {
    const items = articlesOf(subjectKey)
    base.push({
      subject: subjectKey,
      version: 0,
      publishedAt: null,
      articleCount: items.filter(item => item.published).length,
      draftCount: items.filter(item => item.hasDraft).length,
      copiedCount: 0,
      shareCount: 0
    })
  }
  return base
})

const percentText = computed(() => `${Math.round(usage.usageRatio * 100)}%`)
const quotaText = computed(() => (usage.quotaBytes > 0 ? formatBytes(usage.quotaBytes) : '未设限'))
const usageWidth = computed(() => `${Math.min(100, Math.round(usage.usageRatio * 100))}%`)

const usageChipClass = computed(() => ({
  'chip--lime': usage.level === 'ok',
  'chip--amber': usage.level === 'warn',
  'chip--coral': usage.level === 'critical'
}))

const usageLabel = computed(() => {
  if (usage.quotaBytes <= 0) return '未设限'
  if (usage.level === 'critical') return `容量告急 · ${percentText.value}`
  if (usage.level === 'warn') return `接近上限 · ${percentText.value}`
  return `余量充足 · ${percentText.value}`
})

const usageProgressClass = computed(() => ({
  'progress--warm': usage.level === 'warn',
  'progress--pink': usage.level === 'critical'
}))

function formatBytes(value) {
  const bytes = Number(value) || 0
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function formatDate(value) {
  if (!value) return '—'
  return String(value).slice(0, 10)
}

async function loadUsage() {
  try {
    Object.assign(usage, await coursewareGateway.getUsage())
  } catch (error) {
    // 用量只是辅助信息：失败不打断概览主流程
    if (typeof console !== 'undefined') console.warn('加载存储用量失败', error?.message)
  }
}

async function loadOverview() {
  try {
    const overview = await coursewareGateway.getOverview()
    data.subjects = overview.subjects
    data.pendingDrafts = overview.pendingDrafts
    data.acquiredCount = overview.acquiredCount
    data.pendingUpdates = overview.pendingUpdates
    data.unreadNotifications = overview.unreadNotifications
  } catch (error) {
    showToast(error?.message || '加载概览失败', 'error')
  }
}

async function loadArticles() {
  try {
    const { articles: list } = await loadCoursewareLibrary()
    articles.value = list
  } catch (error) {
    showToast(error?.message || '加载课件目录失败', 'error')
  }
}

/** 空目录登记：失败不阻塞页面，目录树退回按课件路径推导。 */
async function loadFolders() {
  try {
    folders.value = await coursewareGateway.listFolders()
  } catch {
    folders.value = []
  }
}

async function ensureStudents() {
  if (adminStore.students.length > 0) return
  try {
    await adminStore.loadStudents()
    adminStore.selectInitialStudent(accountId.value)
  } catch (error) {
    showToast(error?.message || '加载学员列表失败，请稍后重试', 'error')
  }
}

onMounted(async () => {
  // 课件页内置「设置答案」：与答案面板共用同一份已托管课件清单与学员列表
  const jobs = [loadOverview(), loadUsage(), loadArticles(), loadFolders(), ensureStudents()]
  jobs.push(ensureArticleData(blogStore).then(() => coursewareStore.loadWorkspace(blogStore.blogData)))
  await Promise.all(jobs.map(job => job.catch(() => {})))
  loading.value = false
})
</script>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 课件 / 设置答案 / 共享 三个标签 */
.overview__tabs {
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: thin;
}

.overview__panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.answers-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.answers-bar__switcher {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 0 1 360px;
  min-width: 240px;
}

.answers-bar__hint {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  font-size: 0.78rem;
  line-height: 1.5;
  color: var(--text-2);
}

.answers-bar__hint i {
  color: var(--text-3);
}

@media (max-width: 760px) {
  .answers-bar {
    align-items: flex-start;
  }

  .answers-bar__switcher {
    flex-basis: 100%;
  }
}

.overview__lead {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.overview__lead-text {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.6;
  color: var(--text-2);
}

.overview__head {
  align-items: center;
}

.overview__usage {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.overview__usage-line {
  margin: 0;
  font-size: 0.86rem;
  color: var(--text-2);
}

.overview__subjects {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.overview__workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.overview__tree {
  min-width: 0;
}

.overview__tree-head {
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.overview__tree-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.overview__tree-hint {
  font-size: 0.74rem;
  color: var(--text-3);
}

@media (max-width: 720px) {
  .overview__tree-hint {
    display: none;
  }
}

.overview__subject {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.overview__subject-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 0 0 0 12px;
  font-size: 0.76rem;
  color: var(--text-2);
}

.overview__subject-stat {
  font-variant-numeric: tabular-nums;
}

.delete-dialog {
  max-width: 480px;
}

.delete-dialog__title {
  color: var(--vermilion);
}

.delete-dialog__name {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 800;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.delete-dialog__list {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.8rem;
  line-height: 1.55;
  color: var(--text-2);
}

.delete-dialog__confirm {
  background: var(--vermilion);
  color: var(--paper-1);
}

/* 移动对话框 */
.move-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  padding: 20px;
}

.move-dialog {
  width: 100%;
  max-width: 520px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-md);
  background: var(--paper-1);
  box-shadow: 5px 5px 0 var(--ink);
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.move-dialog__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--ink);
}

.move-dialog__current {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.5;
}

.move-dialog__current code {
  font-size: 0.78rem;
  background: var(--paper-2);
  padding: 2px 6px;
  border-radius: 4px;
}

.move-dialog__label {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-2);
}

.move-dialog__input {
  width: 100%;
  padding: 9px 12px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-1);
  font-family: inherit;
  font-size: 0.86rem;
  color: var(--ink);
  outline: none;
}

.move-dialog__input:focus {
  box-shadow: 2px 2px 0 var(--ink);
}

.move-dialog__sugs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 0.76rem;
}

.move-dialog__sug {
  padding: 3px 9px;
  border: 1.5px solid var(--line-strong);
  border-radius: 999px;
  background: var(--paper-2);
  color: var(--text-2);
  font-size: 0.72rem;
  cursor: pointer;
  transition: border-color var(--dur-fast), background var(--dur-fast);
}

.move-dialog__sug:hover {
  border-color: var(--ink);
  background: var(--butter);
}

.move-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .overview__subject-meta {
    margin-left: 6px;
  }
}
</style>
