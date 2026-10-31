<template>
  <section class="editor">
    <header class="editor__head">
      <h1 class="editor__title">课件编辑器</h1>
      <p class="editor__hint">
        草稿保存后学员端看不到，点「发布」才生效。布局指令写在 <code>&lt;!-- @layout:X --&gt;</code> 注释里，
        不影响题干与作答占位符解析。
      </p>
    </header>

    <div class="editor__toolbar">
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
      <aside class="editor__list">
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
      </aside>

      <div class="editor__main">
        <div class="editor__pane">
          <div class="editor__pane-head">
            <span>Markdown 源码{{ articleId ? ` · #${articleId}` : ' · 未保存' }}</span>
            <span v-if="dirty" class="editor__tag">未保存</span>
          </div>
          <textarea v-model="markdown" class="editor__code" spellcheck="false" @input="dirty = true" />
        </div>
        <div class="editor__pane">
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

    <footer class="editor__footer">
      <button type="button" class="editor__ghost" :disabled="busy" @click="runPrecheck">校验</button>
      <button type="button" class="editor__ghost" :disabled="busy || !markdown" @click="save">保存草稿</button>
      <button type="button" class="editor__primary" :disabled="busy || !subject" @click="publish">发布</button>
    </footer>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { showToast } from '@/utils/toast'

const layouts = ref([])
const templates = ref([])
const templateId = ref(null)
const drafts = ref([])
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

async function newDraft() {
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
  busy.value = true
  try {
    const loaded = await coursewareGateway.getDraft(draft.articleId)
    articleId.value = loaded.articleId
    markdown.value = loaded.markdown
    subject.value = draft.subject || subject.value
    dirty.value = false
    precheck.checked = false
    precheck.errors = []
  } catch (error) {
    reportError(error)
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
    await loadDrafts()
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
    await loadDrafts()
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
    await loadDrafts()
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
  await loadDrafts()
})
</script>

<style scoped>
.editor { display: flex; flex-direction: column; gap: 16px; padding: 4px 0 32px; }
.editor__head { display: flex; flex-direction: column; gap: 6px; }
.editor__title { margin: 0; font-size: 22px; font-weight: 700; }
.editor__hint { margin: 0; color: #6b7280; font-size: 13px; line-height: 1.6; }
.editor__toolbar,
.editor__footer { display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-end; }
.editor__field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #374151; min-width: 180px; }
.editor__field input,
.editor__field select {
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
}
.editor__body { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 16px; }
.editor__list { display: flex; flex-direction: column; gap: 8px; }
.editor__list-title { margin: 8px 0 0; font-size: 13px; font-weight: 600; color: #374151; }
.editor__list ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.editor__list-item {
  width: 100%;
  text-align: left;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}
.editor__list-item.is-active { border-color: #6366f1; background: #eef2ff; }
.editor__list-item em { color: #6b7280; font-size: 12px; font-style: normal; }
.editor__main { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }
.editor__pane {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px;
  background: #fff;
  min-height: 460px;
}
.editor__pane-head { display: flex; justify-content: space-between; font-size: 13px; color: #6b7280; }
.editor__code {
  flex: 1;
  min-height: 400px;
  border: none;
  outline: none;
  resize: vertical;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  line-height: 1.7;
}
.editor__preview { flex: 1; overflow: auto; font-size: 13px; line-height: 1.7; }
.editor__preview :deep(pre) { white-space: pre-wrap; word-break: break-word; margin: 0 0 12px; }
.editor__preview :deep(.layout) { display: inline-block; background: #eef2ff; color: #4338ca; border-radius: 6px; padding: 1px 8px; font-size: 12px; margin-bottom: 6px; }
.editor__preview :deep(.slot) { background: #f3f4f6; border: 1px dashed #9ca3af; border-radius: 6px; padding: 1px 6px; font-size: 12px; }
.editor__preview :deep(mark) { background: #fef3c7; padding: 0 4px; border-radius: 4px; }
.editor__errors { margin: 0; padding-left: 18px; color: #b91c1c; font-size: 12px; }
.editor__tag { font-size: 12px; color: #6b7280; }
.editor__tag.is-error { color: #b91c1c; }
.editor__tag.is-ok { color: #047857; }
.editor__empty { margin: 0; color: #6b7280; font-size: 13px; }
.editor__outline { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.editor__outline li {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #374151;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 4px 8px;
}
.editor__outline-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.editor__outline em { color: #4338ca; font-style: normal; background: #eef2ff; border-radius: 4px; padding: 0 4px; }
.editor__outline-actions { display: flex; gap: 2px; }
.editor__outline-actions button {
  border: none;
  background: #f3f4f6;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
  padding: 1px 5px;
}
.editor__outline-actions button:disabled { color: #d1d5db; cursor: not-allowed; }
.editor__report { margin: 0; font-size: 12px; color: #4b5563; }
.editor__upload { display: inline-flex; flex-direction: column; gap: 4px; font-size: 12px; color: #4b5563; }
.editor__check { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #374151; }
.editor__primary,
.editor__ghost { padding: 9px 16px; border-radius: 8px; font-size: 13px; cursor: pointer; }
.editor__primary { border: none; background: #6366f1; color: #fff; }
.editor__primary:disabled { background: #c7d2fe; cursor: not-allowed; }
.editor__ghost { border: 1px solid #d1d5db; background: #fff; color: #374151; }
.editor__ghost:disabled { color: #9ca3af; cursor: not-allowed; }
@media (max-width: 1100px) {
  .editor__body { grid-template-columns: 1fr; }
  .editor__main { grid-template-columns: 1fr; }
}
</style>
