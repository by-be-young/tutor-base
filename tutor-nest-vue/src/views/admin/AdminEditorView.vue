<template>
  <div class="editor">
    <section class="panel panel--tight editor__lead">
      <div class="editor__lead-main">
        <p class="eyebrow">课件托管</p>
        <h1 class="editor__lead-title">在线编辑器</h1>
      </div>
      <button type="button" class="btn btn--soft btn--sm" @click="guideOpen = true">
        <i class="fas fa-circle-question" aria-hidden="true"></i> 发布教程
      </button>
    </section>

    <section class="panel panel--tight editor__identity">
      <div class="editor__identity-main">
        <p class="eyebrow">正在编辑</p>
        <h1 class="editor__identity-title">{{ currentTitle }}</h1>
        <p class="editor__identity-meta">
          <span class="editor__chip">{{ currentSubject }}</span>
          <span v-if="articleId" class="editor__chip">#{{ articleId }}</span>
          <span v-if="currentPath" class="editor__chip editor__chip--path">{{ currentPath }}</span>
          <span class="editor__chip" :class="`is-${currentStatusTone}`">
            {{ currentStatus }}
          </span>
        </p>
        <p v-if="currentArticle && currentArticle.hosted === false" class="editor__identity-hint">
          站内静态课件：正文在 <code>public/articles</code> 下。保存会生成在线草稿，发布后仍按原篇目生效。
        </p>
        <p v-if="currentArticle?.contentMissing || recoveredFromStatic" class="editor__identity-hint is-warning">
          这篇课件的已发布正文文件在服务器上丢失了（存储卷重建或历史环境），当前用站内讲义打开。
          保存草稿并点「发布」会把正文重新写回。
        </p>
      </div>
      <label class="editor__field editor__field--picker">
        <span>选择要编辑的课件</span>
        <select :value="articleId ?? ''" @change="onPickArticle">
          <option value="">— 从我的课件里选一篇 —</option>
          <optgroup v-for="group in articleGroups" :key="group.subject" :label="group.subject">
            <option v-for="item in group.items" :key="item.articleId" :value="item.articleId">
              {{ item.title || `课件 ${item.articleId}` }}{{ item.hosted === false ? ' · 静态课件' : item.published ? ' · 已发布' : ' · 草稿' }}
            </option>
          </optgroup>
        </select>
      </label>
    </section>

    <div class="panel panel--tight editor__toolbar">
      <label class="editor__field">
        <span>科目</span>
        <input v-model="subject" type="text" placeholder="例如 高中化学" />
      </label>
      <label class="editor__field">
        <span>模板</span>
        <select v-model="templateId">
          <option :value="null">不用模板（按布局新建）</option>
          <option v-for="template in templates" :key="template.id" :value="template.id">
            {{ template.name }}{{ template.subject ? ` · ${template.subject}` : '' }}
          </option>
        </select>
      </label>
      <label class="editor__field">
        <span>布局</span>
        <select v-model="layoutId">
          <option v-for="layout in layouts" :key="layout.layoutId" :value="layout.layoutId">
            {{ layout.layoutId }} · {{ layout.name }}
          </option>
        </select>
      </label>
      <button type="button" class="editor__ghost" :disabled="busy" @click="insertLayout">插入布局</button>
      <button type="button" class="editor__ghost" :disabled="busy" @click="insertQuestion">插入题目</button>
      <button type="button" class="editor__ghost" :disabled="busy" @click="newDraft">新建草稿</button>
      <button type="button" class="editor__ghost" :disabled="busy || !markdown" @click="saveAsTemplate">
        存为模板
      </button>
    </div>

    <div class="editor__body">
      <aside class="panel panel--tight editor__list">
        <h2 class="editor__list-title">大纲</h2>
        <p v-if="!outline.length" class="editor__empty">正文里还没有 `# ` 章节。</p>
        <ul v-else class="editor__outline">
          <li v-for="(section, index) in outline" :key="index">
            <span class="editor__outline-title">{{ section.title }}</span>
            <em v-if="section.layout">{{ section.layout }}</em>
            <span class="editor__outline-actions">
              <button type="button" :disabled="index === 0" @click="moveSection(index, -1)">↑</button>
              <button type="button" :disabled="index === outline.length - 1" @click="moveSection(index, 1)">↓</button>
            </span>
          </li>
        </ul>

        <h2 class="editor__list-title">我的草稿</h2>
        <p v-if="!drafts.length" class="editor__empty">还没有草稿。选择布局后点「新建草稿」。</p>
        <ul v-else class="editor__draft-list">
          <li v-for="draft in drafts" :key="draft.articleId" class="editor__draft-item">
            <button
              type="button"
              class="editor__list-item"
              :class="{ 'is-active': articleId === draft.articleId }"
              @click="openDraft(draft)"
            >
              <span>{{ draft.title || `草稿 ${draft.articleId}` }}</span>
              <em>{{ draft.subject || '未归类' }}{{ draft.published ? ' · 已发布' : '' }}</em>
            </button>
            <button
              type="button"
              class="editor__ghost editor__draft-remove"
              :disabled="busy"
              :title="draft.published ? '删除草稿（已发布版本保留）' : '删除这份未发布的草稿'"
              @click.stop="deleteDraftById(draft)"
            >
              删除
            </button>
          </li>
        </ul>

        <h2 class="editor__list-title">导入导出</h2>
        <button type="button" class="editor__ghost" :disabled="busy" @click="exportZip">导出 zip</button>
        <label class="editor__upload">
          <input type="file" accept=".zip" @change="onImportFile" />
          <span>导入 zip（落到草稿）</span>
        </label>
        <label class="editor__check">
          <input v-model="importOverwrite" type="checkbox" />
          <span>同路径覆盖</span>
        </label>
        <p v-if="importReport" class="editor__report">
          导入 {{ importReport.imported }} · 覆盖 {{ importReport.overwritten }} · 跳过 {{ importReport.skipped }}
        </p>
      </aside>

      <div class="editor__main">
        <div class="panel panel--tight editor__pane">
          <div class="editor__pane-head">
            <span>Markdown 源码 · {{ currentTitle }}</span>
            <span class="editor__pane-actions">
              <span v-if="dirty" class="editor__tag">未保存</span>
              <button type="button" class="editor__ghost editor__preview-btn" :disabled="busy || !markdown"
                @click="openPreview">
                <i class="fas fa-eye" aria-hidden="true"></i> 学员样式预览
              </button>
            </span>
          </div>
          <textarea v-model="markdown" class="editor__code" spellcheck="false" @input="dirty = true" />
          <ul v-if="precheck.errors.length" class="editor__errors">
            <li v-for="(error, index) in precheck.errors" :key="index">{{ error }}</li>
          </ul>
        </div>
      </div>
    </div>

    <footer class="panel panel--tight editor__footer">
      <button type="button" class="editor__ghost" :disabled="busy" @click="runPrecheck">校验</button>
      <button type="button" class="editor__ghost" :disabled="busy || !markdown" @click="openPreview">
        学员样式预览
      </button>
      <button type="button" class="editor__ghost" :disabled="busy || !markdown" @click="save">保存草稿</button>
      <button type="button" class="editor__ghost" :disabled="busy || !canDeleteDraft" @click="deleteCurrentDraft">
        删除草稿
      </button>
      <button type="button" class="editor__primary" :disabled="busy || !subject" @click="publish">发布</button>
    </footer>

    <CoursewarePublishGuide :open="guideOpen" @close="guideOpen = false" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CoursewarePublishGuide from '@/components/admin/CoursewarePublishGuide.vue'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { loadArticleMarkdownForEditing, loadCoursewareLibrary } from '@/utils/coursewareLibrary'
import { articleStatus } from '@/utils/coursewareTree'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const layouts = ref([])
const templates = ref([])
const templateId = ref(null)
const drafts = ref([])
// 「我的课件」清单：已发布篇目 + 尚未发布的草稿 + 站内静态课件，作为选择器的唯一数据源
const articles = ref([])
const articleId = ref(null)
const subject = ref('')
const layoutId = ref('A')
const markdown = ref('')
const dirty = ref(false)
const busy = ref(false)
const importOverwrite = ref(false)
const importReport = ref(null)
const guideOpen = ref(false)
const precheck = reactive({ checked: false, errors: [] })

// 当前这篇在服务端有没有草稿行：决定「删除草稿」是否可用
const currentHasDraft = ref(false)
// 已发布正文文件缺失、本次用站内讲义顶上：保存并发布后才会恢复
const recoveredFromStatic = ref(false)
const canDeleteDraft = computed(() => Boolean(articleId.value) && currentHasDraft.value)

/** 当前编辑对象：标题 / 科目 / ID / 发布态都从「我的课件」清单里取，避免只剩一个裸 ID。 */
const currentArticle = computed(
  () => articles.value.find(item => Number(item.articleId) === Number(articleId.value)) || null
)

const currentTitle = computed(
  () => currentArticle.value?.title || (articleId.value ? `课件 #${articleId.value}` : '新课件（未命名）')
)

const currentSubject = computed(() => currentArticle.value?.subject || subject.value || '未归类')

const currentPath = computed(() => currentArticle.value?.logicalPath || '')

// 正文里的状态提示（草稿 / 已发布 / 静态讲义 / 正文缺失 / 有未保存改动）
const currentStatus = computed(() => {
  if (!articleId.value) return '新草稿 · 尚未保存'
  const current = currentArticle.value
  const missing = current?.contentMissing || recoveredFromStatic.value
  if (missing) return dirty.value ? '正文缺失 · 有未保存改动' : '正文缺失 · 需重新发布'
  const status = articleStatus(current)?.label || '草稿 · 未发布'
  return dirty.value ? `${status} · 有未保存改动` : status
})

const currentStatusTone = computed(() => {
  if (!articleId.value) return 'draft'
  if (currentArticle.value?.contentMissing || recoveredFromStatic.value) return 'missing'
  return articleStatus(currentArticle.value)?.tone || 'draft'
})

/** 选择器按科目分组，跨科目也能直接跳着编辑。 */
const articleGroups = computed(() => {
  const groups = new Map()
  for (const item of [...articles.value].sort((left, right) => {
    const subjectOrder = String(left.subject || '').localeCompare(String(right.subject || ''), 'zh-Hans-CN')
    if (subjectOrder !== 0) return subjectOrder
    return String(left.logicalPath || left.title || '').localeCompare(
      String(right.logicalPath || right.title || ''),
      'zh-Hans-CN'
    )
  })) {
    const key = item.subject || '未归类'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(item)
  }
  return [...groups.entries()].map(([subjectKey, items]) => ({ subject: subjectKey, items }))
})

function onPickArticle(event) {
  const value = event.target.value
  const target = articles.value.find(item => String(item.articleId) === String(value))
  if (!target) {
    event.target.value = articleId.value ?? ''
    return
  }
  if (Number(target.articleId) === Number(articleId.value)) return
  openDraft(target).then((opened) => {
    if (!opened) event.target.value = articleId.value ?? ''
  })
}

function confirmDiscard() {
  if (!dirty.value) return true
  return window.confirm('当前课件有未保存的改动，切换后会丢失，继续切换吗？')
}

/** 大纲：按 `# ` 切 section，带布局标签与索引，供上移/下移。 */
const outline = computed(() => splitSections(markdown.value))

function splitSections(source) {
  const sections = []
  let current = null
  let pendingLayout = null
  for (const line of (source || '').split('\n')) {
    const directive = line.match(/<!--\s*@layout:\s*([A-Za-z0-9_-]+)\s*-->/)
    if (directive) {
      pendingLayout = directive[1]
      continue
    }
    const heading = line.match(/^#\s+(.*)$/)
    if (heading) {
      current = { title: heading[1].trim(), layout: pendingLayout, lines: [line] }
      pendingLayout = null
      sections.push(current)
      continue
    }
    if (current) current.lines.push(line)
  }
  return sections.filter(section => section.title)
}

/** 上移/下移一个 section（连同它前面的布局指令一起移动）。 */
function moveSection(index, delta) {
  const lines = markdown.value.split('\n')
  const blocks = []
  let buffer = []
  let layoutLine = null
  for (const line of lines) {
    if (/<!--\s*@layout:/.test(line)) {
      layoutLine = line
      continue
    }
    if (/^#\s+/.test(line) && (buffer.length || layoutLine)) {
      blocks.push({ layout: layoutLine, lines: buffer })
      buffer = [line]
      layoutLine = null
      continue
    }
    buffer.push(line)
  }
  blocks.push({ layout: layoutLine, lines: buffer })

  const headingIndexes = blocks
    .map((block, position) => (block.lines.some(line => /^#\s+/.test(line)) ? position : -1))
    .filter(position => position >= 0)
  const from = headingIndexes[index]
  const to = headingIndexes[index + delta]
  if (from == null || to == null) return
  const [moved] = blocks.splice(from, 1)
  blocks.splice(to, 0, moved)

  const rebuilt = []
  for (const block of blocks) {
    if (block.layout) rebuilt.push(block.layout)
    rebuilt.push(...block.lines)
  }
  markdown.value = rebuilt.join('\n')
  dirty.value = true
}

function reportError(error) {
  showToast(error?.message || '操作失败，请稍后重试', 'error')
}

async function loadDrafts() {
  try {
    drafts.value = await coursewareGateway.listDrafts()
  } catch (error) {
    reportError(error)
  }
}

async function loadArticles() {
  try {
    const { articles: list } = await loadCoursewareLibrary()
    articles.value = list
  } catch (error) {
    reportError(error)
  }
}

async function newDraft() {
  if (!confirmDiscard()) return
  busy.value = true
  try {
    markdown.value = templateId.value
      ? await coursewareGateway.getTemplate(templateId.value)
      : await coursewareGateway.getLayoutSkeleton(layoutId.value, subject.value || '新课件')
    articleId.value = null
    currentHasDraft.value = false
    recoveredFromStatic.value = false
    dirty.value = true
    precheck.checked = false
    precheck.errors = []
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function saveAsTemplate() {
  const name = window.prompt('模板名（同科目同类型同名会覆盖）', '新模板')
  if (!name) return
  busy.value = true
  try {
    await coursewareGateway.saveTemplate({
      subject: subject.value || undefined,
      kind: 'new_lesson',
      name,
      markdown: markdown.value
    })
    showToast('已存为模板', 'success')
    templates.value = await coursewareGateway.listTemplates(subject.value || undefined)
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function openDraft(draft) {
  if (!confirmDiscard()) return false
  busy.value = true
  try {
    const loaded = await loadArticleMarkdownForEditing(draft)
    articleId.value = loaded.articleId
    markdown.value = loaded.markdown
    currentHasDraft.value = loaded.hasDraft
    recoveredFromStatic.value = loaded.contentMissing
    if (draft.subject) subject.value = draft.subject
    dirty.value = false
    precheck.checked = false
    precheck.errors = []
    if (loaded.contentMissing) {
      showToast('这篇课件的已发布正文文件缺失，已用站内讲义打开。保存草稿并发布后会重新写回正文。', 'warning')
    }
    return true
  } catch (error) {
    reportError(error)
    return false
  } finally {
    busy.value = false
  }
}

/** 删除一份草稿：只丢未上线的改动，已发布版本不受影响。 */
async function deleteDraftById(draft) {
  const label = draft.title || `草稿 ${draft.articleId}`
  const hint = draft.published
    ? `删除「${label}」的草稿？已经发布的版本会保留，学员看到的仍是上一版。`
    : `删除「${label}」？这份草稿还没有发布过，删除后无法恢复。`
  if (!window.confirm(hint)) return false
  busy.value = true
  try {
    await coursewareGateway.deleteDraft(draft.articleId)
    showToast('草稿已删除', 'success')
    if (Number(articleId.value) === Number(draft.articleId)) resetEditor()
    await Promise.all([loadDrafts(), loadArticles()])
    return true
  } catch (error) {
    reportError(error)
    return false
  } finally {
    busy.value = false
  }
}

function deleteCurrentDraft() {
  if (!articleId.value) return
  return deleteDraftById({
    articleId: articleId.value,
    title: currentTitle.value,
    published: Boolean(currentArticle.value?.published)
  })
}

function resetEditor() {
  articleId.value = null
  markdown.value = ''
  currentHasDraft.value = false
  recoveredFromStatic.value = false
  dirty.value = false
  precheck.checked = false
  precheck.errors = []
}

function insertLayout() {
  markdown.value = `${markdown.value.replace(/\s+$/, '')}\n\n<!-- @layout:${layoutId.value} -->\n# 新章节\n\n`
  dirty.value = true
}

function insertQuestion() {
  const numbers = [...markdown.value.matchAll(/【题干\s*(\d+)/g)].map(match => Number(match[1]))
  const next = numbers.length ? Math.max(...numbers) + 1 : 1
  markdown.value = `${markdown.value.replace(/\s+$/, '')}\n\n【题干${next}】题目内容\n\nA. 选项\n\n【@${next}】\n`
  dirty.value = true
}

async function runPrecheck() {
  try {
    const result = await coursewareGateway.precheckDraft(markdown.value)
    precheck.checked = true
    precheck.errors = result.errors
    showToast(result.ok ? '校验通过' : `发现 ${result.errors.length} 个问题`, result.ok ? 'success' : 'warning')
  } catch (error) {
    reportError(error)
  }
}

/** 打开学员视角预览：先落一次草稿，再在新标签页打开完整的学员阅读器。 */
async function openPreview() {
  if (!markdown.value.trim()) {
    showToast('正文还是空的，先写点内容再预览', 'warning')
    return
  }
  busy.value = true
  try {
    let id = articleId.value
    if (dirty.value || id == null) {
      const saved = await coursewareGateway.saveDraft(id, markdown.value)
      id = saved.articleId
      articleId.value = id
      currentHasDraft.value = true
      recoveredFromStatic.value = false
      dirty.value = false
      await Promise.all([loadDrafts(), loadArticles()])
    }
    const query = { mode: 'preview' }
    const course = subject.value || currentArticle.value?.subject
    if (course) query.subject = course
    if (currentTitle.value) query.title = currentTitle.value
    if (currentPath.value) query.path = currentPath.value
    const target = router.resolve({ name: 'CoursewarePreview', params: { id }, query })
    window.open(target.href, '_blank', 'noopener')
    showToast('已保存草稿，并在新标签页打开学员视角预览', 'success')
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function save() {
  busy.value = true
  try {
    const saved = await coursewareGateway.saveDraft(articleId.value, markdown.value)
    articleId.value = saved.articleId
    currentHasDraft.value = true
    recoveredFromStatic.value = false
    dirty.value = false
    showToast('草稿已保存', 'success')
    await Promise.all([loadDrafts(), loadArticles()])
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function publish() {
  busy.value = true
  try {
    if (dirty.value || articleId.value == null) {
      const saved = await coursewareGateway.saveDraft(articleId.value, markdown.value)
      articleId.value = saved.articleId
      currentHasDraft.value = true
      dirty.value = false
    }
    const published = await coursewareGateway.publishDrafts(subject.value)
    recoveredFromStatic.value = false
    // 发布成功后服务端会清掉本次并入版本的草稿，编辑器随之回到"已发布正文"状态
    currentHasDraft.value = false
    showToast(`已发布 v${published.version}（${published.articleCount} 篇），草稿已自动清空`, 'success')
    await Promise.all([loadDrafts(), loadArticles()])
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function exportZip() {
  busy.value = true
  try {
    const blob = await coursewareGateway.exportZip({ subject: subject.value || undefined })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `courseware-${subject.value || 'all'}.zip`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

async function onImportFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  busy.value = true
  try {
    importReport.value = await coursewareGateway.importZip(file, importOverwrite.value)
    showToast(`导入完成：新增 ${importReport.value.imported} 篇`, 'success')
    await Promise.all([loadDrafts(), loadArticles()])
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
    event.target.value = ''
  }
}

onMounted(async () => {
  try {
    layouts.value = await coursewareGateway.listLayouts()
    if (layouts.value.length) layoutId.value = layouts.value[0].layoutId
    templates.value = await coursewareGateway.listTemplates()
  } catch (error) {
    reportError(error)
  }
  await Promise.all([loadDrafts(), loadArticles()])
  // 支持从「课件」页/链接直达某篇课件：?subject=高中化学&articleId=12
  const querySubject = typeof route.query.subject === 'string' ? route.query.subject.trim() : ''
  if (querySubject) subject.value = querySubject
  const queryArticleId = Number(route.query.articleId)
  if (Number.isFinite(queryArticleId) && queryArticleId > 0) {
    const target = articles.value.find(item => Number(item.articleId) === queryArticleId)
    if (target) {
      await openDraft(target)
    } else {
      showToast(`没有找到课件 #${queryArticleId}，可从上方选择器切换`, 'warning')
    }
  }
})
</script>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.editor__lead {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.editor__lead-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.editor__lead-title {
  margin: 0;
  font-size: 1.05rem;
  color: var(--ink);
}

.editor__identity {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-end;
  justify-content: space-between;
}

.editor__identity-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.editor__identity-title {
  margin: 0;
  font-size: 1.05rem;
  line-height: 1.35;
  color: var(--ink);
}

.editor__identity-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
}

.editor__identity-hint {
  margin: 2px 0 0;
  font-size: 0.72rem;
  color: var(--text-2);
}

.editor__identity-hint code {
  font-family: var(--font-mono);
  font-size: 0.68rem;
}

.editor__chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  color: var(--text-2);
  background: var(--paper-2);
}

.editor__chip--path {
  border-style: dashed;
  word-break: break-all;
}

.editor__chip.is-live {
  border-color: #2f7a4d;
  color: #2f7a4d;
}

.editor__chip.is-draft {
  border-color: #b07d1f;
  color: #b07d1f;
}

.editor__chip.is-static {
  border-color: var(--line);
  color: var(--text-2);
}

.editor__chip.is-missing {
  border-color: var(--vermilion);
  color: var(--vermilion);
}

.editor__identity-hint.is-warning {
  color: var(--vermilion);
}

.editor__field--picker {
  min-width: 260px;
  flex: 0 1 320px;
}

.editor__toolbar,
.editor__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: flex-end;
}

.editor__footer {
  justify-content: flex-end;
}

.editor__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.74rem;
  color: var(--text-2);
  min-width: 180px;
}

.editor__field input,
.editor__field select {
  padding: 7px 10px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-1);
  color: var(--ink);
  font-family: inherit;
  font-size: 0.82rem;
}

.editor__body {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.editor__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.editor__list-title {
  margin: 6px 0 0;
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-3);
}

.editor__list ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.editor__list-item {
  width: 100%;
  text-align: left;
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  border-radius: var(--r-sm);
  padding: 7px 9px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 0.8rem;
  color: var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
  transition: transform 0.14s var(--ease-out), box-shadow 0.14s var(--ease-out);
}

.editor__list-item:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 var(--ink);
}

.editor__list-item.is-active {
  background: var(--lime);
}

.editor__draft-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.editor__draft-item {
  display: flex;
  align-items: stretch;
  gap: 6px;
}

.editor__draft-item .editor__list-item {
  flex: 1;
  min-width: 0;
}

.editor__draft-remove {
  flex: none;
  align-self: center;
  padding: 4px 8px;
  font-size: 0.72rem;
  color: var(--vermilion);
}

.editor__list-item em {
  color: var(--text-3);
  font-family: var(--font-mono);
  font-size: 0.66rem;
  font-style: normal;
}

.editor__main {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
}

.editor__pane {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  min-height: 460px;
}

.editor__pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-3);
}

.editor__code {
  flex: 1;
  min-height: 400px;
  border: 1.5px dashed var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-2);
  color: var(--ink);
  padding: 10px;
  outline: none;
  resize: vertical;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  line-height: 1.75;
}

.editor__pane-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor__preview-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  font-size: 0.7rem;
  letter-spacing: 0;
  text-transform: none;
  font-family: var(--font-body);
  color: var(--ink);
  cursor: pointer;
}

.editor__errors {
  margin: 0;
  padding-left: 18px;
  color: var(--vermilion);
  font-size: 0.78rem;
}

.editor__tag {
  font-family: var(--font-mono);
  font-size: 0.64rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-3);
}

.editor__tag.is-error {
  color: var(--vermilion);
}

.editor__tag.is-ok {
  color: var(--teal);
}

.editor__empty {
  margin: 0;
  color: var(--text-3);
  font-size: 0.78rem;
}

.editor__outline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.editor__outline li {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.76rem;
  color: var(--text-2);
  border: 1.5px solid var(--paper-2);
  border-radius: var(--r-sm);
  padding: 4px 8px;
  background: var(--paper-2);
}

.editor__outline-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink);
}

.editor__outline em {
  color: var(--ink);
  font-family: var(--font-mono);
  font-style: normal;
  font-size: 0.62rem;
  background: var(--butter);
  border-radius: var(--r-full);
  padding: 0 6px;
}

.editor__outline-actions {
  display: flex;
  gap: 2px;
}

.editor__outline-actions button {
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.68rem;
  padding: 1px 5px;
  color: var(--ink);
}

.editor__outline-actions button:disabled {
  color: var(--text-3);
  border-color: var(--paper-2);
  background: var(--paper-2);
  cursor: not-allowed;
}

.editor__report {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 0.72rem;
  color: var(--text-2);
}

.editor__upload {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.74rem;
  color: var(--text-2);
}

.editor__check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: var(--text-2);
}

.editor__check input[type='checkbox'] {
  accent-color: var(--cobalt);
  width: 15px;
  height: 15px;
}

.editor__primary,
.editor__ghost {
  padding: 8px 16px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  font-family: var(--font-display);
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.14s var(--ease-out), box-shadow 0.14s var(--ease-out);
}

.editor__primary {
  background: var(--vermilion);
  color: var(--paper-1);
  box-shadow: 3px 3px 0 var(--ink);
}

.editor__primary:hover:not(:disabled) {
  transform: translate(-1px, -1px);
  box-shadow: 4px 4px 0 var(--ink);
}

.editor__primary:disabled {
  background: var(--paper-2);
  color: var(--text-3);
  box-shadow: none;
  cursor: not-allowed;
}

.editor__ghost {
  background: var(--paper-1);
  color: var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
}

.editor__ghost:hover:not(:disabled) {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 var(--ink);
}

.editor__ghost:disabled {
  color: var(--text-3);
  border-color: var(--text-3);
  box-shadow: none;
  cursor: not-allowed;
}

@media (max-width: 1100px) {
  .editor__body {
    grid-template-columns: 1fr;
  }

  .editor__main {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .editor__field {
    min-width: 100%;
  }
}
</style>
