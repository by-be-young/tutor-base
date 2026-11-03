<template>
  <div class="editor">
    <section class="panel panel--tight editor__lead">
      <p class="eyebrow">课件托管</p>
      <p class="editor__lead-text">
        草稿保存后学员端看不到，点「发布」才生效。布局指令写在 <code>&lt;!-- @layout:X --&gt;</code> 注释里，
        不影响题干与作答占位符解析。
      </p>
    </section>

    <section class="panel panel--tight editor__identity">
      <div class="editor__identity-main">
        <p class="eyebrow">正在编辑</p>
        <h1 class="editor__identity-title">{{ currentTitle }}</h1>
        <p class="editor__identity-meta">
          <span class="editor__chip">{{ currentSubject }}</span>
          <span v-if="articleId" class="editor__chip">#{{ articleId }}</span>
          <span v-if="currentPath" class="editor__chip editor__chip--path">{{ currentPath }}</span>
          <span class="editor__chip" :class="currentArticle?.published ? 'is-live' : 'is-draft'">
            {{ currentStatus }}
          </span>
        </p>
      </div>
      <label class="editor__field editor__field--picker">
        <span>选择要编辑的课件</span>
        <select :value="articleId ?? ''" @change="onPickArticle">
          <option value="">— 从我的课件里选一篇 —</option>
          <optgroup v-for="group in articleGroups" :key="group.subject" :label="group.subject">
            <option v-for="item in group.items" :key="item.articleId" :value="item.articleId">
              {{ item.title || `课件 ${item.articleId}` }}{{ item.published ? ' · 已发布' : ' · 草稿' }}
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
        <ul v-else>
          <li v-for="draft in drafts" :key="draft.articleId">
            <button
              type="button"
              class="editor__list-item"
              :class="{ 'is-active': articleId === draft.articleId }"
              @click="openDraft(draft)"
            >
              <span>{{ draft.title || `草稿 ${draft.articleId}` }}</span>
              <em>{{ draft.subject || '未归类' }}{{ draft.published ? ' · 已发布' : '' }}</em>
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

        <h2 class="editor__list-title">Git 工作区（可选）</h2>
        <p class="editor__report">
          习惯本地编辑时用：仓库里只有 Markdown，发布仍走服务端校验。
        </p>
        <ol class="editor__wizard">
          <li v-for="(step, index) in gitSteps" :key="index">
            <span class="editor__wizard-step">{{ index + 1 }}</span>
            <span class="editor__wizard-body">
              <strong>{{ step.title }}</strong>
              <code v-if="step.command" class="editor__wizard-cmd">{{ step.command }}</code>
              <em v-if="step.hint">{{ step.hint }}</em>
            </span>
            <button v-if="step.command" type="button" class="editor__ghost editor__copy" @click="copyCommand(step.command)">
              复制
            </button>
          </li>
        </ol>
      </aside>

      <div class="editor__main">
        <div class="panel panel--tight editor__pane">
          <div class="editor__pane-head">
            <span>Markdown 源码 · {{ currentTitle }}</span>
            <span v-if="dirty" class="editor__tag">未保存</span>
          </div>
          <textarea v-model="markdown" class="editor__code" spellcheck="false" @input="dirty = true" />
        </div>
        <div class="panel panel--tight editor__pane">
          <div class="editor__pane-head">
            <span>预览</span>
            <span v-if="precheck.errors.length" class="editor__tag is-error">
              {{ precheck.errors.length }} 个问题
            </span>
            <span v-else-if="precheck.checked" class="editor__tag is-ok">校验通过</span>
          </div>
          <div class="editor__preview" v-html="previewHtml" />
          <ul v-if="precheck.errors.length" class="editor__errors">
            <li v-for="(error, index) in precheck.errors" :key="index">{{ error }}</li>
          </ul>
        </div>
      </div>
    </div>

    <footer class="panel panel--tight editor__footer">
      <button type="button" class="editor__ghost" :disabled="busy" @click="runPrecheck">校验</button>
      <button type="button" class="editor__ghost" :disabled="busy || !markdown" @click="save">保存草稿</button>
      <button type="button" class="editor__primary" :disabled="busy || !subject" @click="publish">发布</button>
    </footer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { showToast } from '@/utils/toast'

const route = useRoute()

const layouts = ref([])
const templates = ref([])
const templateId = ref(null)
const drafts = ref([])
// 「我的课件」清单：已发布篇目 + 尚未发布的草稿，作为选择器的唯一数据源
const articles = ref([])
const articleId = ref(null)
const subject = ref('')
const layoutId = ref('A')
const markdown = ref('')
const dirty = ref(false)
const busy = ref(false)
const importOverwrite = ref(false)
const importReport = ref(null)
const precheck = reactive({ checked: false, errors: [] })

const previewHtml = computed(() => renderSections(markdown.value))

/** 当前编辑对象：标题 / 科目 / ID / 发布态都从「我的课件」清单里取，避免只剩一个裸 ID。 */
const currentArticle = computed(
  () => articles.value.find(item => Number(item.articleId) === Number(articleId.value)) || null
)

const currentTitle = computed(
  () => currentArticle.value?.title || (articleId.value ? `课件 #${articleId.value}` : '新课件（未命名）')
)

const currentSubject = computed(() => currentArticle.value?.subject || subject.value || '未归类')

const currentPath = computed(() => currentArticle.value?.logicalPath || '')

const currentStatus = computed(() => {
  if (!articleId.value) return '新草稿 · 尚未保存'
  const published = currentArticle.value?.published
  if (published) return dirty.value ? '已发布 · 有未保存改动' : '已发布'
  return dirty.value ? '草稿 · 有未保存改动' : '草稿 · 未发布'
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

/** 按 `# ` 切 section，把布局指令显示成小标签；题目占位符显示成卡片占位。 */
function renderSections(source) {
  const escape = (text) => String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const sections = []
  let current = { layout: null, lines: [] }
  for (const line of (source || '').split('\n')) {
    const directive = line.match(/<!--\s*@layout:\s*([A-Za-z0-9_-]+)\s*-->/)
    if (directive) {
      if (current.lines.length || current.layout) sections.push(current)
      current = { layout: directive[1], lines: [] }
      continue
    }
    current.lines.push(line)
  }
  if (current.lines.length || current.layout) sections.push(current)

  return sections
    .map(section => {
      const body = section.lines
        .join('\n')
        .replace(/【题干\s*(\d+)\s*】/g, '<mark>题干$1</mark>')
        .replace(/【@(\d*)】/g, '<span class="slot">答题卡</span>')
      const label = section.layout ? `<div class="layout">布局 ${section.layout}</div>` : ''
      return `<section>${label}<pre>${escape(body)}</pre></section>`
    })
    .join('')
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
    articles.value = await coursewareGateway.listArticles()
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
    const loaded = await coursewareGateway.getDraft(draft.articleId)
    articleId.value = loaded.articleId
    markdown.value = loaded.markdown
    if (draft.subject) subject.value = draft.subject
    dirty.value = false
    precheck.checked = false
    precheck.errors = []
    return true
  } catch (error) {
    reportError(error)
    return false
  } finally {
    busy.value = false
  }
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

async function save() {
  busy.value = true
  try {
    const saved = await coursewareGateway.saveDraft(articleId.value, markdown.value)
    articleId.value = saved.articleId
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
      dirty.value = false
    }
    const published = await coursewareGateway.publishDrafts(subject.value)
    showToast(`已发布 v${published.version}（${published.articleCount} 篇）`, 'success')
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

/** 零基础 Git 向导：三步走，命令可一键复制，不需要记参数。 */
const gitSteps = computed(() => {
  const target = subject.value ? ` --subject ${subject.value}` : ' --subject <科目>'
  return [
    {
      title: '安装 Node.js 18+ 与 Git',
      command: '',
      hint: 'https://nodejs.org/ 与 https://git-scm.com/ ，Windows 默认选项即可'
    },
    {
      title: '登录后取会话 cookie，填进环境变量',
      command: "$env:TUTOR_SESSION='<浏览器 cookie 值>'",
      hint: '开发者工具 → Application → Cookies → TUTOR_SESSION'
    },
    {
      title: '自检环境',
      command: 'node scripts/courseware-cli.mjs doctor',
      hint: '未通过时会直接告诉你缺什么'
    },
    {
      title: '拉取 → 本地编辑 → 推送',
      command: `node scripts/courseware-cli.mjs pull${target} --dir ./courseware`,
      hint: '改完用同样的 --subject 执行 push；冲突会提示先 pull，不会覆盖你的改动'
    }
  ]
})

async function copyCommand(command) {
  try {
    await navigator.clipboard.writeText(command)
    showToast('命令已复制', 'success')
  } catch {
    showToast('复制失败，请手动选择命令文本', 'warn')
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
  flex-direction: column;
  gap: 4px;
}

.editor__lead-text {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.6;
  color: var(--text-2);
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

.editor__list-item em {
  color: var(--text-3);
  font-family: var(--font-mono);
  font-size: 0.66rem;
  font-style: normal;
}

.editor__main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
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

.editor__preview {
  flex: 1;
  overflow: auto;
  font-size: 0.84rem;
  line-height: 1.75;
  color: var(--text-2);
}

.editor__preview :deep(pre) {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0 0 12px;
}

.editor__preview :deep(.layout) {
  display: inline-block;
  background: var(--sky);
  color: var(--ink);
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  padding: 1px 8px;
  font-family: var(--font-mono);
  font-size: 0.66rem;
  margin-bottom: 6px;
}

.editor__preview :deep(.slot) {
  background: var(--paper-2);
  border: 1.5px dashed var(--ink);
  border-radius: var(--r-sm);
  padding: 1px 6px;
  font-family: var(--font-mono);
  font-size: 0.66rem;
}

.editor__preview :deep(mark) {
  background: var(--lime);
  color: var(--ink);
  padding: 0 4px;
  border-radius: 4px;
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

.editor__wizard {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.editor__wizard li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  border: 1.5px solid var(--paper-2);
  background: var(--paper-2);
  border-radius: var(--r-sm);
}

.editor__wizard-step {
  flex: 0 0 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  background: var(--lime);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  font-weight: 700;
  color: var(--ink);
}

.editor__wizard-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.editor__wizard-body strong {
  font-size: 0.76rem;
  color: var(--ink);
}

.editor__wizard-body em {
  font-style: normal;
  font-size: 0.68rem;
  line-height: 1.5;
  color: var(--text-3);
}

.editor__wizard-cmd {
  display: block;
  overflow-x: auto;
  padding: 4px 6px;
  border: 1px dashed var(--ink);
  border-radius: 4px;
  background: var(--paper-1);
  font-family: var(--font-mono);
  font-size: 0.66rem;
  color: var(--ink);
  white-space: pre;
}

.editor__copy {
  flex: 0 0 auto;
  padding: 4px 10px;
  font-size: 0.68rem;
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
