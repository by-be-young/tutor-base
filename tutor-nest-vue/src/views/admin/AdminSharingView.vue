<template>
  <div class="page page--wide sharing">
    <header class="page-head">
      <p class="eyebrow">课件共享</p>
      <h1 class="page-title sharing__title">课件共享</h1>
      <p class="page-sub">
        分享自己的整科或单篇课件，或从资源目录获取其他老师的课件。获取后先进入草稿，发布时才随版本上线。
      </p>
      <div v-if="notifications.length" class="panel panel--tight sharing__notify">
        <span class="chip chip--amber">{{ unreadCount }} 条未读提醒</span>
        <ul class="sharing__notify-list">
          <li v-for="item in notifications.slice(0, 3)" :key="item.id">
            <strong>{{ item.title }}</strong>
            <span>{{ item.body }}</span>
          </li>
        </ul>
        <button type="button" class="btn btn--ghost btn--sm" :disabled="!unreadCount" @click="markAllRead">
          全部标记已读
        </button>
      </div>
    </header>

    <div class="seg sharing__tabs" role="tablist" aria-label="课件共享视图">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        role="tab"
        class="seg__btn"
        :class="{ 'is-active': activeTab === tab.id }"
        :aria-selected="activeTab === tab.id"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 我的分享 -->
    <div v-if="activeTab === 'shares'" class="sharing__panel">
      <form class="panel panel--pad sharing__card" @submit.prevent="createShare">
        <h2 class="sharing__card-title">新建分享</h2>
        <div class="sharing__row">
          <label class="sharing__field">
            <span>科目</span>
            <select v-model="shareForm.subject">
              <option v-for="subject in subjects" :key="subject" :value="subject">{{ subject }}</option>
            </select>
          </label>
          <label class="sharing__field">
            <span>范围</span>
            <select v-model="shareForm.scope">
              <option value="subject">整科</option>
              <option value="article">指定单篇</option>
            </select>
          </label>
          <label class="sharing__check">
            <input v-model="shareForm.allowPreview" type="checkbox" />
            <span>允许预览</span>
          </label>
          <label class="sharing__check">
            <input v-model="shareForm.allowCopy" type="checkbox" />
            <span>允许获取</span>
          </label>
        </div>

        <div v-if="shareForm.scope === 'article'" class="sharing__picker">
          <p v-if="!myArticles.length" class="sharing__empty">该科目暂无可分享的课件。</p>
          <label v-for="article in myArticles" :key="article.id" class="sharing__check">
            <input v-model="shareForm.articleIds" type="checkbox" :value="article.id" />
            <span>{{ article.title }}</span>
          </label>
        </div>

        <button class="sharing__primary" type="submit" :disabled="busy">
          {{ busy ? '提交中…' : '创建分享' }}
        </button>
      </form>

      <div class="panel panel--pad sharing__card">
        <h2 class="sharing__card-title">我发出的分享</h2>
        <p v-if="!shares.length" class="sharing__empty">还没有分享。选择科目创建第一条分享后，其他老师才能在资源目录看到。</p>
        <table v-else class="sharing__table">
          <thead>
            <tr><th>科目</th><th>范围</th><th>预览</th><th>获取</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="share in shares" :key="share.shareId">
              <td>{{ share.subject }}</td>
              <td>{{ share.scope === 'subject' ? '整科' : `第 ${share.articleId} 篇` }}</td>
              <td>{{ share.allowPreview ? '允许' : '禁止' }}</td>
              <td>{{ share.allowCopy ? '允许' : '禁止' }}</td>
              <td>
                <button type="button" class="sharing__link" :disabled="busy" @click="revoke(share.shareId)">
                  撤回
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 资源目录 -->
    <div v-else-if="activeTab === 'resources'" class="sharing__panel">
      <div class="panel panel--pad sharing__card">
        <h2 class="sharing__card-title">资源目录</h2>
        <p v-if="loadingResources" class="sharing__empty">加载中…</p>
        <p v-else-if="!resources.length" class="sharing__empty">暂时没有其他老师分享课件。</p>
        <table v-else class="sharing__table">
          <thead>
            <tr><th>老师</th><th>科目</th><th>篇数</th><th>可否获取</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="resource in resources" :key="`${resource.teacherId}-${resource.subject}`">
              <td>{{ resource.teacherName }}</td>
              <td>{{ resource.subject }}</td>
              <td>{{ resource.articleCount }}</td>
              <td>{{ resource.allowCopy ? '允许' : '仅预览' }}</td>
              <td>
                <button type="button" class="sharing__link" @click="toggleTree(resource)">
                  {{ expandedKey === keyOf(resource) ? '收起' : '展开' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="expandedKey" class="panel panel--pad sharing__card">
        <h2 class="sharing__card-title">{{ expandedTitle }} · 共享课件</h2>
        <p v-if="loadingTree" class="sharing__empty">加载中…</p>
        <template v-else>
          <p v-if="!tree.length" class="sharing__empty">该科目没有可获取的篇目。</p>
          <ul v-else class="sharing__tree">
            <li v-for="article in tree" :key="article.articleId">
              <label class="sharing__check">
                <input
                  v-model="selectedArticles"
                  type="checkbox"
                  :value="article.articleId"
                  :disabled="!article.allowCopy"
                />
                <span>{{ article.logicalPath }}</span>
                <em v-if="!article.allowCopy" class="sharing__tag">仅预览</em>
              </label>
              <button type="button" class="sharing__link" @click="openPreview(article)">预览</button>
            </li>
          </ul>

          <div class="sharing__row">
            <label class="sharing__field">
              <span>目标目录（可空，默认沿用来源路径）</span>
              <input v-model="targetRootPath" type="text" placeholder="例如 我获取的课件/高中化学" />
            </label>
          </div>
          <div class="sharing__actions">
            <button type="button" class="sharing__primary" :disabled="busy || !selectedArticles.length" @click="acquireSelected">
              获取选中（{{ selectedArticles.length }}）
            </button>
            <button type="button" class="sharing__ghost" :disabled="busy" @click="acquireWholeSubject">获取整科</button>
          </div>
        </template>
      </div>
    </div>

    <!-- 我的获取 -->
    <div v-else-if="activeTab === 'copies'" class="sharing__panel">
      <div class="panel panel--pad sharing__card">
        <h2 class="sharing__card-title">我的获取</h2>
        <p v-if="!copies.length" class="sharing__empty">还没有获取任何课件。</p>
        <table v-else class="sharing__table">
          <thead>
            <tr><th>标题</th><th>来源</th><th>来源路径</th><th>我的路径</th><th>状态</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="copy in copies" :key="copy.copyId">
              <td>
                <input v-if="renaming.copyId === copy.copyId" v-model="renaming.title" type="text" />
                <template v-else>{{ copy.title }}</template>
              </td>
              <td>{{ copy.sourceTeacherName }}</td>
              <td class="sharing__path">{{ copy.sourceLogicalPath }}</td>
              <td class="sharing__path">
                <input v-if="renaming.copyId === copy.copyId" v-model="renaming.logicalPath" type="text" />
                <template v-else>{{ copy.localLogicalPath }}</template>
              </td>
              <td>
                <span v-if="copy.hasUpdate" class="sharing__tag is-new">有更新</span>
                <span v-else>已同步</span>
              </td>
              <td>
                <template v-if="renaming.copyId === copy.copyId">
                  <button type="button" class="sharing__link" :disabled="busy" @click="saveRename">保存</button>
                  <button type="button" class="sharing__link" @click="cancelRename">取消</button>
                </template>
                <button v-else type="button" class="sharing__link" @click="startRename(copy)">改名</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 更新 -->
    <div v-else class="sharing__panel">
      <div class="panel panel--pad sharing__card">
        <h2 class="sharing__card-title">
          更新汇总
          <span class="sharing__tag">有更新 {{ digest.updatedCount }} · 新增 {{ digest.addedCount }} · 下线 {{ digest.removedCount }}</span>
        </h2>
        <p v-if="!hasDigestItems" class="sharing__empty">所有获取的课件都与来源一致。</p>
        <div v-else class="sharing__digest">
          <section v-if="digest.updated.length">
            <h3>有更新</h3>
            <ul>
              <li v-for="item in digest.updated" :key="`u-${item.copyId}`">
                <span>{{ item.title }}</span>
                <button type="button" class="sharing__link" @click="openUpdate(item.copyId)">查看差异</button>
              </li>
            </ul>
          </section>
          <section v-if="digest.added.length">
            <h3>来源新增（尚未获取）</h3>
            <ul>
              <li v-for="item in digest.added" :key="`a-${item.sourceArticleId}`">
                <span>{{ item.title }}</span>
                <em class="sharing__path">{{ item.logicalPath }}</em>
              </li>
            </ul>
          </section>
          <section v-if="digest.removed.length">
            <h3>来源已下线</h3>
            <ul>
              <li v-for="item in digest.removed" :key="`r-${item.copyId}`">
                <span>{{ item.title }}</span>
                <em class="sharing__path">来源不再提供该篇，本地副本仍可用</em>
              </li>
            </ul>
          </section>
        </div>
      </div>
    </div>

    <div v-if="previewDialog.open" class="backdrop sharing__dialog" role="dialog" aria-modal="true">
      <div class="modal sharing__dialog-body">
        <h2 class="sharing__card-title">{{ previewDialog.title }}</h2>
        <p v-if="previewDialog.loading" class="sharing__empty">加载中…</p>
        <pre v-else class="sharing__preview">{{ previewDialog.markdown }}</pre>
        <div class="sharing__actions">
          <button type="button" class="sharing__ghost" @click="previewDialog.open = false">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="updateDialog.open" class="backdrop sharing__dialog" role="dialog" aria-modal="true">
      <div class="modal sharing__dialog-body">
        <h2 class="sharing__card-title">{{ updateDialog.sourceTitle }}</h2>
        <p v-if="updateDialog.loading" class="sharing__empty">加载中…</p>
        <template v-else>
          <p class="sharing__hint">
            来源版本 v{{ updateDialog.detail.sourceVersion }} ·
            <span v-if="updateDialog.detail.localModified">本地已有改动，覆盖前会自动备份</span>
            <span v-else>本地未改动</span>
            <span v-if="updateDialog.detail.answerChanged"> · 答案键有变更</span>
          </p>
          <pre class="sharing__preview">{{ updateDialog.detail.diff || '（无差异）' }}</pre>
          <div class="sharing__actions">
            <button type="button" class="sharing__primary" :disabled="busy" @click="applyUpdate('overwrite')">
              以来源覆盖
            </button>
            <button type="button" class="sharing__ghost" :disabled="busy" @click="applyUpdate('keep')">忽略此版本</button>
            <button type="button" class="sharing__ghost" @click="updateDialog.open = false">稍后处理</button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useArticleStore } from '@/stores/blogStore'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { showToast } from '@/utils/toast'

const tabs = [
  { id: 'shares', label: '我的分享' },
  { id: 'resources', label: '资源目录' },
  { id: 'copies', label: '我的获取' },
  { id: 'updates', label: '更新' }
]

const blogStore = useArticleStore()
const activeTab = ref('shares')
const busy = ref(false)

const shares = ref([])
const resources = ref([])
const copies = ref([])
const loadingResources = ref(false)

const notifications = ref([])
const unreadCount = ref(0)

const digest = ref({ updatedCount: 0, addedCount: 0, removedCount: 0, updated: [], added: [], removed: [] })
const hasDigestItems = computed(() =>
  digest.value.updatedCount + digest.value.addedCount + digest.value.removedCount > 0)

const renaming = reactive({ copyId: null, title: '', logicalPath: '' })
const previewDialog = reactive({ open: false, title: '', markdown: '', loading: false })
const updateDialog = reactive({
  open: false,
  loading: false,
  copyId: null,
  sourceTitle: '',
  detail: { sourceVersion: 0, localModified: false, answerChanged: false, diff: '', sourceHash: '' }
})

const expandedKey = ref('')
const expandedTitle = ref('')
const tree = ref([])
const loadingTree = ref(false)
const selectedArticles = ref([])
const targetRootPath = ref('')

const shareForm = reactive({
  subject: '',
  scope: 'subject',
  articleIds: [],
  allowPreview: true,
  allowCopy: true
})

const subjects = computed(() => [...new Set(blogStore.blogData.map(item => item.series).filter(Boolean))])
const myArticles = computed(() => {
  if (!shareForm.subject) return []
  return blogStore.blogData.filter(item => item.series === shareForm.subject)
})

watch(subjects, (list) => {
  if (!shareForm.subject && list.length) shareForm.subject = list[0]
}, { immediate: true })

watch(() => shareForm.scope, () => {
  shareForm.articleIds = []
})

function keyOf(resource) {
  return `${resource.teacherId}:${resource.subject}`
}

function reportError(error) {
  showToast(error?.message || '操作失败，请稍后重试', 'error')
}

async function loadShares() {
  try {
    shares.value = await coursewareGateway.listShares(false)
  } catch (error) {
    reportError(error)
  }
}

async function loadResources() {
  loadingResources.value = true
  try {
    resources.value = await coursewareGateway.listResources()
  } catch (error) {
    reportError(error)
  } finally {
    loadingResources.value = false
  }
}

async function loadCopies() {
  try {
    copies.value = await coursewareGateway.listCopies()
  } catch (error) {
    reportError(error)
  }
}

async function loadNotifications() {
  try {
    const result = await coursewareGateway.listNotifications(false)
    notifications.value = result.items
    unreadCount.value = result.unreadCount
  } catch (error) {
    reportError(error)
  }
}

async function loadDigest() {
  try {
    digest.value = await coursewareGateway.getUpdates()
  } catch (error) {
    reportError(error)
  }
}

async function markAllRead() {
  try {
    const result = await coursewareGateway.markNotificationsRead([])
    unreadCount.value = result.unreadCount
    notifications.value = notifications.value.map(item => ({ ...item, read: true }))
  } catch (error) {
    reportError(error)
  }
}

async function openPreview(article) {
  previewDialog.open = true
  previewDialog.title = article.logicalPath
  previewDialog.markdown = ''
  previewDialog.loading = true
  const [teacherId] = expandedKey.value.split(':')
  try {
    previewDialog.markdown = await coursewareGateway.previewArticle(Number(teacherId), article.articleId)
  } catch (error) {
    previewDialog.open = false
    reportError(error)
  } finally {
    previewDialog.loading = false
  }
}

function startRename(copy) {
  renaming.copyId = copy.copyId
  renaming.title = copy.title
  renaming.logicalPath = copy.localLogicalPath
}

function cancelRename() {
  renaming.copyId = null
  renaming.title = ''
  renaming.logicalPath = ''
}

async function saveRename() {
  busy.value = true
  try {
    await coursewareGateway.renameCopy(renaming.copyId, {
      title: renaming.title,
      logicalPath: renaming.logicalPath
    })
    showToast('已更新名称与路径', 'success')
    cancelRename()
    await loadCopies()
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function openUpdate(copyId) {
  updateDialog.open = true
  updateDialog.loading = true
  updateDialog.copyId = copyId
  updateDialog.sourceTitle = copies.value.find(item => item.copyId === copyId)?.title || '更新详情'
  try {
    updateDialog.detail = await coursewareGateway.getUpdateDetail(copyId)
    updateDialog.sourceTitle = updateDialog.detail.sourceTitle || updateDialog.sourceTitle
  } catch (error) {
    updateDialog.open = false
    reportError(error)
  } finally {
    updateDialog.loading = false
  }
}

async function applyUpdate(mode) {
  busy.value = true
  try {
    const result = await coursewareGateway.applyUpdate(updateDialog.copyId, {
      mode,
      expectedSourceHash: updateDialog.detail.sourceHash
    })
    showToast(mode === 'overwrite'
      ? `已用来源覆盖${result.backupHash ? '（原草稿已备份）' : ''}`
      : '已忽略该来源版本', 'success')
    updateDialog.open = false
    await Promise.all([loadCopies(), loadDigest()])
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function createShare() {
  if (!shareForm.subject) {
    showToast('请先选择科目', 'warning')
    return
  }
  if (shareForm.scope === 'article' && !shareForm.articleIds.length) {
    showToast('请选择要分享的课件', 'warning')
    return
  }
  busy.value = true
  try {
    await coursewareGateway.createShare({
      subject: shareForm.subject,
      scope: shareForm.scope,
      articleIds: shareForm.scope === 'article' ? shareForm.articleIds : [],
      allowPreview: shareForm.allowPreview,
      allowCopy: shareForm.allowCopy
    })
    showToast('分享已生效', 'success')
    shareForm.articleIds = []
    await loadShares()
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function revoke(shareId) {
  busy.value = true
  try {
    await coursewareGateway.revokeShare(shareId)
    showToast('已撤回分享', 'success')
    await loadShares()
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function toggleTree(resource) {
  const key = keyOf(resource)
  if (expandedKey.value === key) {
    expandedKey.value = ''
    tree.value = []
    return
  }
  expandedKey.value = key
  expandedTitle.value = `${resource.teacherName} · ${resource.subject}`
  selectedArticles.value = []
  loadingTree.value = true
  try {
    tree.value = await coursewareGateway.listResourceTree(resource.teacherId, resource.subject)
  } catch (error) {
    tree.value = []
    reportError(error)
  } finally {
    loadingTree.value = false
  }
}

async function acquire(articleIds) {
  const [teacherId, subject] = expandedKey.value.split(':')
  if (!teacherId) return
  busy.value = true
  try {
    const results = await coursewareGateway.acquire({
      sourceTeacherId: Number(teacherId),
      subject,
      articleIds,
      targetRootPath: targetRootPath.value.trim() || undefined
    })
    const reused = results.filter(item => item.alreadyExists).length
    showToast(reused
      ? `已获取 ${results.length - reused} 篇，${reused} 篇此前已获取`
      : `已获取 ${results.length} 篇课件`, 'success')
    selectedArticles.value = []
    await Promise.all([loadCopies(), blogStore.loadArticleData()])
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

function acquireSelected() {
  return acquire([...selectedArticles.value])
}

function acquireWholeSubject() {
  return acquire([])
}

onMounted(async () => {
  if (!blogStore.blogData.length) await blogStore.loadArticleData()
  await Promise.all([loadShares(), loadResources(), loadCopies(), loadNotifications(), loadDigest()])
})
</script>

<style scoped>
.sharing {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.sharing__title {
  margin: 0;
}

.sharing__tabs {
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: thin;
}

.sharing__notify {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
}

.sharing__notify-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.82rem;
  color: var(--text-2);
}

.sharing__notify-list strong {
  margin-right: 6px;
  color: var(--ink);
}

.sharing__panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sharing__card-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 0.98rem;
  font-weight: 800;
  color: var(--ink);
}

.sharing__row {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: flex-end;
}

.sharing__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.78rem;
  color: var(--text-2);
  min-width: 200px;
}

.sharing__field select,
.sharing__field input,
.sharing__table input {
  padding: 7px 10px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-1);
  color: var(--ink);
  font-family: inherit;
  font-size: 0.82rem;
}

.sharing__check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.82rem;
  color: var(--text-2);
}

.sharing__check input[type='checkbox'] {
  accent-color: var(--cobalt);
  width: 15px;
  height: 15px;
}

.sharing__picker {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
  padding: 10px;
  border: 1.5px dashed var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-2);
}

.sharing__tree {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sharing__tree li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.sharing__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.82rem;
}

.sharing__table th,
.sharing__table td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1.5px solid var(--paper-2);
}

.sharing__table th {
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-3);
}

.sharing__path {
  color: var(--text-3);
  font-family: var(--font-mono);
  font-size: 0.76rem;
  word-break: break-all;
}

.sharing__empty {
  margin: 0;
  color: var(--text-3);
  font-size: 0.82rem;
}

.sharing__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.sharing__primary,
.sharing__ghost {
  padding: 8px 16px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  font-family: var(--font-display);
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.14s var(--ease-out), box-shadow 0.14s var(--ease-out);
}

.sharing__primary {
  background: var(--vermilion);
  color: var(--paper-1);
  box-shadow: 3px 3px 0 var(--ink);
}

.sharing__primary:hover:not(:disabled) {
  transform: translate(-1px, -1px);
  box-shadow: 4px 4px 0 var(--ink);
}

.sharing__primary:disabled {
  background: var(--paper-2);
  color: var(--text-3);
  box-shadow: none;
  cursor: not-allowed;
}

.sharing__ghost {
  background: var(--paper-1);
  color: var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
}

.sharing__ghost:hover:not(:disabled) {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 var(--ink);
}

.sharing__link {
  border: none;
  background: none;
  padding: 0;
  color: var(--cobalt);
  font-family: inherit;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.sharing__link:disabled {
  color: var(--text-3);
  cursor: not-allowed;
  text-decoration: none;
}

.sharing__tag {
  font-family: var(--font-mono);
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  color: var(--text-3);
  font-style: normal;
}

.sharing__tag.is-new {
  padding: 1px 7px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  background: var(--butter);
  color: var(--ink);
}

.sharing__digest {
  display: flex;
  flex-direction: column;
  gap: 14px;
  font-size: 0.82rem;
}

.sharing__digest h3 {
  margin: 0 0 6px;
  font-size: 0.82rem;
  font-weight: 800;
  color: var(--ink);
}

.sharing__digest ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sharing__digest li {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.sharing__dialog {
  z-index: 60;
}

.sharing__dialog-body {
  width: min(820px, 100%);
  max-height: 80vh;
  overflow: auto;
}

.sharing__preview {
  margin: 0;
  padding: 12px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-2);
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 0.74rem;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 46vh;
  overflow: auto;
}

@media (max-width: 768px) {
  .sharing__row {
    gap: 10px;
  }

  .sharing__field {
    min-width: 100%;
  }
}
</style>
