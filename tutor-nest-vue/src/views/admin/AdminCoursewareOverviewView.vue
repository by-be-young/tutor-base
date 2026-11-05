<!-- src/views/admin/AdminCoursewareOverviewView.vue -->
<template>
  <div class="overview">
    <section class="panel panel--tight overview__lead">
      <p class="eyebrow">课件托管</p>
      <p class="overview__lead-text">
        每个科目一个发布版本：草稿只有你自己能看到，点「发布」才生效；删除草稿不会影响已发布内容。
        点目录里的篇目可以按学员看到的样子预览，需要改内容再点「去编辑」。
        获取他人的课件、分享自己的课件都在「共享」页。
      </p>
    </section>

    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-label">科目</span>
        <span class="stat-value">{{ subjects.length }}</span>
        <span class="stat-sub">已发布版本 + 站内静态课件</span>
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
      <p class="empty__title">还没有发布任何科目</p>
      <p class="empty__text">
        到「编辑」页新建草稿并发布；`public/articles` 下的静态课件也会出现在科目目录里，可直接打开编辑。
      </p>
      <div class="empty__actions">
        <router-link class="btn btn--primary btn--sm" to="/admin/editor">去新建课件</router-link>
        <router-link class="btn btn--ghost btn--sm" to="/admin/sharing">去共享页</router-link>
      </div>
    </div>

    <div v-else class="overview__workspace">
      <section class="panel panel--pad overview__tree">
        <header class="panel-head overview__tree-head">
          <h2 class="panel-title">课件目录</h2>
          <button type="button" class="btn btn--ghost btn--sm" @click="toggleAll">
            {{ expanded ? '全部收起' : '全部展开' }}
          </button>
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
              @file-click="selectArticle"
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
              <router-link class="btn btn--soft btn--sm" :to="{ path: '/admin/editor', query: { subject: item.subject } }">
                新建 / 继续编辑
              </router-link>
            </p>
          </section>
        </div>
      </section>

      <aside class="panel panel--pad overview__preview">
        <CoursewarePreviewPane
          :markdown="previewMarkdown"
          :title="previewTitle"
          :subtitle="previewPath"
          :status="previewStatus.label"
          :status-tone="previewStatus.tone"
          :loading="previewLoading"
          empty-text="从左侧目录点一篇课件，这里按学员看到的样子预览；需要改内容再点「去编辑」。"
        >
          <template #actions>
            <div v-if="previewArticle" class="overview__preview-actions">
              <router-link
                class="btn btn--soft btn--sm"
                :to="{ path: '/admin/editor', query: { subject: previewArticle.subject || undefined, articleId: previewArticle.articleId } }"
              >
                去编辑
              </router-link>
              <span v-if="previewStatus.tone === 'missing'" class="overview__preview-hint">
                已发布正文文件丢了：去编辑页保存草稿并发布即可恢复。
              </span>
              <span v-else-if="previewNote" class="overview__preview-hint">{{ previewNote }}</span>
            </div>
          </template>
        </CoursewarePreviewPane>
      </aside>
    </div>

    <section class="panel panel--tight overview__legacy">
      <h2 class="panel-title">旧「认领」页即将下线</h2>
      <p class="text-dim overview__legacy-text">
        课件归属已改为「老师命名空间 + 发布版本」，不再需要认领。
        旧页面仅用于查看历史认领记录，写入操作可被服务端配置下线。
      </p>
      <router-link class="btn btn--ghost btn--sm" to="/admin/content">查看旧认领页</router-link>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import FolderNode from '@/components/articles/FolderNode.vue'
import CoursewarePreviewPane from '@/components/admin/CoursewarePreviewPane.vue'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { loadArticleMarkdownForEditing, loadCoursewareLibrary } from '@/utils/coursewareLibrary'
import { articleStatus, buildCoursewareTree, markActiveArticle } from '@/utils/coursewareTree'
import { showToast } from '@/utils/toast'

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

// 科目目录：数据来自「我的课件」全量清单，含已发布篇目、草稿与站内静态课件
const articles = ref([])
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

const ACCENTS = ['cyan', 'violet', 'amber', 'coral', 'pink', 'lime']

function accentFor(index) {
  return ACCENTS[index % ACCENTS.length]
}

/** 目录展开状态：全部展开/收起由 FolderNode 的 expand-token 统一驱动。 */
const expanded = ref(true)
const expandToken = ref(0)

function toggleAll() {
  expanded.value = !expanded.value
  expandToken.value += 1
}

// 预览：选中哪篇、正文、读取状态
const selectedId = ref(null)
const previewMarkdown = ref('')
const previewLoading = ref(false)
const previewNote = ref('')
const previewStatus = reactive({ label: '', tone: 'live' })

const previewArticle = computed(
  () => articles.value.find(item => Number(item.articleId) === Number(selectedId.value)) || null
)
const previewTitle = computed(() => previewArticle.value?.title || '')
const previewPath = computed(() => previewArticle.value?.logicalPath || '')

/** 交给 FolderNode 的目录树：科目是根文件夹，logicalPath 的层级是多级子目录。 */
function treeOf(subjectValue) {
  return markActiveArticle(
    buildCoursewareTree(subjectValue, articlesOf(subjectValue)),
    selectedId.value
  )
}

const PREVIEW_NOTES = {
  draft: '这是草稿预览：学员现在看到的还是上一版，点「发布」才生效。',
  static: '这篇还是站内静态讲义，正文在 public/articles 下；保存草稿并发布后由服务端托管。'
}

const PREVIEW_LABELS = {
  draft: { label: '草稿预览 · 学员看不到', tone: 'draft' },
  published: { label: '已发布 · 学员看到的就是这版', tone: 'live' },
  static: { label: '站内讲义 · 未托管', tone: 'static' },
  recovered: { label: '正文缺失 · 需重新发布', tone: 'missing' }
}

async function selectArticle(articleId) {
  const article = articles.value.find(item => Number(item.articleId) === Number(articleId))
  if (!article) return
  selectedId.value = article.articleId
  previewMarkdown.value = ''
  previewNote.value = ''
  previewLoading.value = true
  try {
    const loaded = await loadArticleMarkdownForEditing(article)
    if (Number(selectedId.value) !== Number(article.articleId)) return
    previewMarkdown.value = loaded.markdown
    const status = PREVIEW_LABELS[loaded.contentMissing ? 'recovered' : loaded.source]
      || { label: articleStatus(article).label, tone: articleStatus(article).tone }
    previewStatus.label = status.label
    previewStatus.tone = status.tone
    previewNote.value = loaded.contentMissing
      ? ''
      : PREVIEW_NOTES[loaded.source] || ''
  } catch (error) {
    previewStatus.label = '读取失败'
    previewStatus.tone = 'missing'
    previewNote.value = ''
    showToast(error?.message || '读取课件正文失败', 'error')
  } finally {
    previewLoading.value = false
  }
}

/** 概览给的科目 + 只在「我的课件」清单里出现的科目（例如只有草稿、还没发布） */
const subjects = computed(() => {
  const base = [...data.subjects]
  const known = new Set(base.map(item => item.subject))
  for (const [subjectKey, items] of articlesBySubject.value) {
    if (known.has(subjectKey)) continue
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

onMounted(async () => {
  await Promise.all([loadOverview(), loadUsage(), loadArticles()])
  loading.value = false
})
</script>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 18px;
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
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
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

.overview__preview {
  min-width: 0;
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.overview__preview :deep(.preview-pane) {
  flex: 1;
  min-height: 0;
}

.overview__preview-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.overview__preview-hint {
  font-size: 0.74rem;
  line-height: 1.5;
  color: var(--text-2);
}

@media (max-width: 1080px) {
  .overview__workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .overview__preview {
    position: static;
    max-height: none;
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

.overview__legacy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.overview__legacy-text {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .overview__subject-meta {
    margin-left: 6px;
  }
}
</style>
