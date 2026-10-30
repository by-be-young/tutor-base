<template>
  <section class="sharing">
    <header class="sharing__head">
      <h1 class="sharing__title">课件共享</h1>
      <p class="sharing__hint">
        分享自己的整科或单篇课件，或从资源目录获取其他老师的课件。获取后先进入草稿，发布时才随版本上线。
      </p>
      <div v-if="notifications.length" class="sharing__notify">
        <span class="sharing__tag is-new">{{ unreadCount }} 条未读提醒</span>
        <ul class="sharing__notify-list">
          <li v-for="item in notifications.slice(0, 3)" :key="item.id">
            <strong>{{ item.title }}</strong>
            <span>{{ item.body }}</span>
          </li>
        </ul>
        <button type="button" class="sharing__link" :disabled="!unreadCount" @click="markAllRead">
          全部标记已读
        </button>
      </div>
    </header>

    <div class="sharing__tabs" role="tablist" aria-label="课件共享视图">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        role="tab"
        class="sharing__tab"
        :class="{ 'is-active': activeTab === tab.id }"
        :aria-selected="activeTab === tab.id"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 我的分享 -->
    <div v-if="activeTab === 'shares'" class="sharing__panel">
      <form class="sharing__card" @submit.prevent="createShare">
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

      <div class="sharing__card">
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
      <div class="sharing__card">
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

      <div v-if="expandedKey" class="sharing__card">
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
      <div class="sharing__card">
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
      <div class="sharing__card">
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

    <div v-if="previewDialog.open" class="sharing__dialog" role="dialog" aria-modal="true">
      <div class="sharing__dialog-body">
        <h2 class="sharing__card-title">{{ previewDialog.title }}</h2>
        <p v-if="previewDialog.loading" class="sharing__empty">加载中…</p>
        <pre v-else class="sharing__preview">{{ previewDialog.markdown }}</pre>
        <div class="sharing__actions">
          <button type="button" class="sharing__ghost" @click="previewDialog.open = false">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="updateDialog.open" class="sharing__dialog" role="dialog" aria-modal="true">
      <div class="sharing__dialog-body">
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
  </section>
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
  padding: 4px 0 32px;
}

.sharing__head { display: flex; flex-direction: column; gap: 6px; }
.sharing__title { font-size: 22px; font-weight: 700; margin: 0; }
.sharing__hint { margin: 0; color: #6b7280; font-size: 13px; line-height: 1.6; }

.sharing__tabs { display: flex; gap: 8px; border-bottom: 1px solid #e5e7eb; }
.sharing__tab {
  border: none;
  background: none;
  padding: 10px 14px;
  font-size: 14px;
  color: #6b7280;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.sharing__tab.is-active { color: #1f2937; border-bottom-color: #6366f1; font-weight: 600; }

.sharing__panel { display: flex; flex-direction: column; gap: 16px; }
.sharing__card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}
.sharing__card-title { margin: 0; font-size: 15px; font-weight: 600; }
.sharing__row { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-end; }
.sharing__field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #374151; min-width: 200px; }
.sharing__field select,
.sharing__field input {
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
}
.sharing__check { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #374151; }
.sharing__picker { display: flex; flex-direction: column; gap: 6px; max-height: 220px; overflow-y: auto; padding: 8px; border: 1px dashed #d1d5db; border-radius: 8px; }
.sharing__tree { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.sharing__table { width: 100%; border-collapse: collapse; font-size: 13px; }
.sharing__table th,
.sharing__table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f3f4f6; }
.sharing__table th { color: #6b7280; font-weight: 600; }
.sharing__path { color: #4b5563; word-break: break-all; }
.sharing__empty { margin: 0; color: #6b7280; font-size: 13px; }
.sharing__actions { display: flex; gap: 10px; }
.sharing__primary,
.sharing__ghost {
  padding: 9px 16px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}
.sharing__primary { border: none; background: #6366f1; color: #fff; }
.sharing__primary:disabled { background: #c7d2fe; cursor: not-allowed; }
.sharing__ghost { border: 1px solid #d1d5db; background: #fff; color: #374151; }
.sharing__link { border: none; background: none; color: #6366f1; cursor: pointer; font-size: 13px; padding: 0; }
.sharing__link:disabled { color: #c7d2fe; cursor: not-allowed; }
.sharing__tag { font-size: 12px; color: #6b7280; font-style: normal; }
.sharing__tag.is-new { color: #b45309; background: #fef3c7; border-radius: 6px; padding: 1px 6px; }

.sharing__notify {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  border-radius: 10px;
}
.sharing__notify-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: #4b5563; }
.sharing__notify-list strong { margin-right: 6px; }
.sharing__digest { display: flex; flex-direction: column; gap: 14px; font-size: 13px; }
.sharing__digest h3 { margin: 0 0 6px; font-size: 13px; color: #374151; }
.sharing__digest ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.sharing__dialog {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 60;
}
.sharing__dialog-body {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  width: min(820px, 100%);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: auto;
}
.sharing__preview {
  margin: 0;
  padding: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 46vh;
  overflow: auto;
}
</style>
