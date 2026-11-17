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
        <p v-if="currentArticle?.contentMissing" class="editor__identity-hint is-warning">
          这篇课件的已发布正文文件在服务器上缺失。页面不会回退到任何静态文件，
          请重新保存并发布一份正文来恢复。
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
      <button type="button" class="editor__ghost" :disabled="busy" @click="openImageModal">插入图片</button>
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
                @click="openPreview()">
                <i class="fas fa-eye" aria-hidden="true"></i> 学员样式预览
              </button>
              <button type="button" class="editor__ghost editor__preview-btn" :disabled="busy || !markdown"
                @click="openPreview(true)">
                <i class="fas fa-file-pdf" aria-hidden="true"></i> 导出 PDF
              </button>
            </span>
          </div>
          <textarea ref="editorTextarea" v-model="markdown" class="editor__code" spellcheck="false" @input="dirty = true" />
          <ul v-if="precheck.errors.length" class="editor__errors">
            <li v-for="(error, index) in precheck.errors" :key="index">{{ error }}</li>
          </ul>
        </div>
      </div>
    </div>

    <footer class="panel panel--tight editor__footer">
      <button type="button" class="editor__ghost" :disabled="busy" @click="runPrecheck">校验</button>
      <button type="button" class="editor__ghost" :disabled="busy || !markdown" @click="openPreview()">
        学员样式预览
      </button>
      <button type="button" class="editor__ghost" :disabled="busy || !markdown" @click="openPreview(true)">
        导出 PDF
      </button>
      <button type="button" class="editor__ghost" :disabled="busy || articleId == null" @click="openAnswerSetting">
        设置答案
      </button>
      <button type="button" class="editor__ghost" :disabled="busy || !markdown" @click="save">保存草稿</button>
      <button type="button" class="editor__ghost" :disabled="busy || !canDeleteDraft" @click="deleteCurrentDraft">
        删除草稿
      </button>
      <button type="button" class="editor__primary" :disabled="busy || !subject" @click="publish">发布</button>
    </footer>

    <CoursewarePublishGuide :open="guideOpen" @close="guideOpen = false" />

    <div v-if="imageOpen" class="editor__modal" role="dialog" aria-modal="true" aria-label="插入图片"
      @click.self="closeImageModal">
      <div class="editor__modal-card">
        <header class="editor__modal-head">
          <div>
            <p class="eyebrow">插入图片</p>
            <h2>上传到课件图片桶</h2>
          </div>
          <button type="button" class="editor__ghost" :disabled="imageUploading" @click="closeImageModal">
            关闭
          </button>
        </header>

        <p class="editor__modal-hint">
          文件名按“科目 + 插入位置”自动生成，可手动修改。文件名只能用小写字母、数字、连字符和下划线；
          桶里已有同名文件时会提示并阻止上传。
        </p>

        <label class="editor__upload editor__upload--image">
          <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" @change="onImageFile" />
          <span>{{ imageFile ? '重新选择图片' : '选择图片（PNG / JPG / WebP / GIF）' }}</span>
        </label>

        <div v-if="imagePreviewUrl" class="editor__image-preview">
          <img :src="imagePreviewUrl" alt="待插入图片预览" />
          <span>{{ imageFile?.name }} · {{ formatBytes(imageFile?.size) }}</span>
        </div>

        <label class="editor__field">
          <span>文件名</span>
          <input v-model="imageName" type="text" spellcheck="false" @input="onImageNameInput" />
        </label>

        <p class="editor__modal-meta">
          插入位置：第 {{ imageSection }} 节 · 本节第 {{ imageFigure }} 张
        </p>

        <p v-if="imageChecking" class="editor__modal-hint">正在检查是否重名…</p>
        <p v-else-if="imageConflict" class="editor__modal-alert">
          已有同名文件。建议使用
          <button type="button" class="editor__inline-link" @click="applySuggestedName">
            {{ imageSuggested }}
          </button>
        </p>
        <p v-else-if="imageName && !imageNameValid" class="editor__modal-alert">
          文件名只能用小写字母、数字、点、下划线、连字符，并以 png/jpg/jpeg/webp/gif 结尾。
        </p>
        <p v-else-if="imageName && imageNameValid && imageChecked" class="editor__modal-ok">
          文件名可用
        </p>
        <p v-if="imageError" class="editor__modal-alert">{{ imageError }}</p>

        <footer class="editor__modal-actions">
          <button type="button" class="editor__ghost" :disabled="imageUploading" @click="closeImageModal">
            取消
          </button>
          <button type="button" class="editor__primary"
            :disabled="imageUploading || imageChecking || !imageFile || !imageNameValid || imageConflict || !imageChecked"
            @click="confirmImage">
            {{ imageUploading ? '上传中…' : '上传并插入' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CoursewarePublishGuide from '@/components/admin/CoursewarePublishGuide.vue'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { loadArticleMarkdownForEditing, loadCoursewareLibrary } from '@/utils/coursewareLibrary'
import { articleStatus } from '@/utils/coursewareTree'
import { sectionAtCursor, suggestedImageName } from '@/utils/coursewareMediaName'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const layouts = ref([])
const templates = ref([])
const templateId = ref(null)
const drafts = ref([])
// 「我的课件」清单：已发布篇目 + 尚未发布的草稿（正文全部由数据库托管），作为选择器的唯一数据源
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

// 插入图片：上传到 Supabase Storage，文件名按当前科目与光标所在章节生成
const editorTextarea = ref(null)
const imageOpen = ref(false)
const imageFile = ref(null)
const imageName = ref('')
const imagePreviewUrl = ref('')
const imageChecking = ref(false)
const imageUploading = ref(false)
const imageChecked = ref(false)
const imageConflict = ref(false)
const imageSuggested = ref('')
const imageError = ref('')
const imageSection = ref(1)
const imageFigure = ref(1)
let imageCursor = 0
let imageTimer = null

const IMAGE_NAME_PATTERN = /^[a-z0-9][a-z0-9._-]{0,120}\.(png|jpg|jpeg|webp|gif)$/

const imageNameValid = computed(() => IMAGE_NAME_PATTERN.test(String(imageName.value || '').trim()))

// 当前这篇在服务端有没有草稿行：决定「删除草稿」是否可用
const currentHasDraft = ref(false)
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

// 正文里的状态提示（草稿 / 已发布 / 正文缺失 / 有未保存改动）
const currentStatus = computed(() => {
  if (!articleId.value) return '新草稿 · 尚未保存'
  const current = currentArticle.value
  const missing = current?.contentMissing
  if (missing) return dirty.value ? '正文缺失 · 有未保存改动' : '正文缺失 · 需服务端恢复'
  const status = articleStatus(current)?.label || '草稿 · 未发布'
  return dirty.value ? `${status} · 有未保存改动` : status
})

const currentStatusTone = computed(() => {
  if (!articleId.value) return 'draft'
  if (currentArticle.value?.contentMissing) return 'missing'
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

function formatBytes(value) {
  const bytes = Number(value) || 0
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

function openImageModal() {
  if (busy.value) return
  const cursor = editorTextarea.value?.selectionStart ?? markdown.value.length
  imageCursor = cursor
  const position = sectionAtCursor(markdown.value, cursor)
  imageSection.value = position.section
  imageFigure.value = position.figure
  imageFile.value = null
  imageName.value = ''
  imageChecked.value = false
  imageConflict.value = false
  imageSuggested.value = ''
  imageError.value = ''
  if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
  imagePreviewUrl.value = ''
  imageOpen.value = true
}

function closeImageModal(force = false) {
  if (imageUploading.value && !force) return
  if (imageTimer) {
    clearTimeout(imageTimer)
    imageTimer = null
  }
  if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
  imagePreviewUrl.value = ''
  imageOpen.value = false
}

function onImageFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(file.type)) {
    imageError.value = '只支持 PNG、JPG、WebP、GIF 图片'
    imageFile.value = null
    if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
    imagePreviewUrl.value = ''
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    imageError.value = '图片不能超过 10 MB'
    imageFile.value = null
    if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
    imagePreviewUrl.value = ''
    return
  }
  const extension = (file.name.split('.').pop() || 'png').toLowerCase()
  const normalized = extension === 'jpeg' ? 'jpg' : extension
  imageError.value = ''
  imageFile.value = file
  imageName.value = suggestedImageName(markdown.value, imageCursor, currentSubject.value, normalized)
  if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
  imagePreviewUrl.value = URL.createObjectURL(file)
  checkImageName()
}

function onImageNameInput() {
  imageChecked.value = false
  imageConflict.value = false
  imageSuggested.value = ''
  imageError.value = ''
  checkImageName()
}

function checkImageName() {
  if (imageTimer) clearTimeout(imageTimer)
  const name = String(imageName.value || '').trim()
  if (!IMAGE_NAME_PATTERN.test(name)) {
    imageChecking.value = false
    return
  }
  imageChecking.value = true
  imageTimer = setTimeout(async () => {
    try {
      const result = await coursewareGateway.checkMedia(name)
      imageName.value = result.fileName
      imageConflict.value = result.exists
      imageSuggested.value = result.suggested
      imageChecked.value = true
    } catch (error) {
      imageError.value = error?.message || '检查文件名失败'
      imageChecked.value = false
    } finally {
      imageChecking.value = false
    }
  }, 350)
}

function applySuggestedName() {
  imageName.value = imageSuggested.value
  onImageNameInput()
}

async function confirmImage() {
  if (!imageFile.value || !imageNameValid.value || imageConflict.value) return
  imageUploading.value = true
  imageError.value = ''
  try {
    const uploaded = await coursewareGateway.uploadMedia(imageFile.value, imageName.value.trim())
    const name = uploaded?.fileName || imageName.value.trim()
    const cursor = Math.min(imageCursor, markdown.value.length)
    const before = markdown.value.slice(0, cursor)
    const after = markdown.value.slice(cursor)
    const prefix = !before || before.endsWith('\n\n') ? '' : before.endsWith('\n') ? '\n' : '\n\n'
    const suffix = '\n\n'
    const inserted = `${prefix}![[${name}]]${suffix}`
    markdown.value = before + inserted + after.replace(/^\n+/, '')
    dirty.value = true
    closeImageModal(true)
    showToast(`已插入图片 ${name}`, 'success')
    await nextTick()
    const position = cursor + inserted.length
    editorTextarea.value?.focus()
    editorTextarea.value?.setSelectionRange(position, position)
  } catch (error) {
    if (error?.code === 'courseware_media_exists' || error?.status === 409) {
      imageConflict.value = true
      checkImageName()
      imageError.value = '这个文件名刚被别人用了，请换一个'
    } else {
      imageError.value = error?.message || '上传失败，请稍后重试'
    }
  } finally {
    imageUploading.value = false
  }
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

/**
 * 打开学员视角预览：先落一次草稿，再在新标签页打开完整的学员阅读器。
 *
 * @param {boolean} print 直接以打印视图打开（浏览器里选「另存为 PDF」）
 */
async function openPreview(print = false) {
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
      dirty.value = false
      await Promise.all([loadDrafts(), loadArticles()])
    }
    const query = { mode: 'preview' }
    const course = subject.value || currentArticle.value?.subject
    if (course) query.subject = course
    if (currentTitle.value) query.title = currentTitle.value
    if (currentPath.value) query.path = currentPath.value
    if (print) query.print = '1'
    const target = router.resolve({ name: 'CoursewarePreview', params: { id }, query })
    window.open(target.href, '_blank', 'noopener')
    showToast(print ? '已保存草稿，正在打开打印视图（可另存为 PDF）' : '已保存草稿，并在新标签页打开学员视角预览',
      'success')
  } catch (error) {
    reportError(error)
  } finally {
    busy.value = false
  }
}

/** 打开这篇文章的标准答案设置页：有未保存改动先落草稿，保证答案对应最新题号。 */
async function openAnswerSetting() {
  if (articleId.value == null) {
    showToast('先保存一份草稿，再设置答案', 'warning')
    return
  }
  busy.value = true
  try {
    let id = articleId.value
    if (dirty.value) {
      const saved = await coursewareGateway.saveDraft(id, markdown.value)
      id = saved.articleId
      articleId.value = id
      currentHasDraft.value = true
      dirty.value = false
      await Promise.all([loadDrafts(), loadArticles()])
    }
    const target = router.resolve({ name: 'ArticleDetail', params: { id }, query: { mode: 'answer' } })
    window.open(target.href, '_blank', 'noopener')
    showToast('已在新标签页打开标准答案设置页', 'success')
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

/* 插入图片弹窗 */
.editor__modal {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(20, 18, 14, 0.52);
}

.editor__modal-card {
  width: min(560px, 100%);
  max-height: calc(100vh - 48px);
  overflow: auto;
  padding: 24px;
  background: var(--paper-1);
  border: 2px solid var(--ink);
  border-radius: var(--r-lg);
  box-shadow: 8px 8px 0 var(--ink);
}

.editor__modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.editor__modal-head h2 {
  margin: 4px 0 0;
  font-family: var(--font-display);
  font-size: 1.05rem;
}

.editor__modal-hint,
.editor__modal-meta {
  margin: 0 0 14px;
  font-size: 0.78rem;
  color: var(--text-2);
  line-height: 1.6;
}

.editor__upload--image {
  display: flex;
  margin-bottom: 14px;
}

.editor__image-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  padding: 10px;
  background: var(--paper-2);
  border: 1.5px dashed var(--ink);
  border-radius: var(--r-md);
}

.editor__image-preview img {
  width: 92px;
  height: 72px;
  object-fit: contain;
  background: #fff;
  border: 1px solid var(--line);
}

.editor__image-preview span {
  font-size: 0.76rem;
  color: var(--text-2);
  word-break: break-all;
}

.editor__modal-alert {
  margin: 0 0 12px;
  padding: 9px 12px;
  font-size: 0.78rem;
  color: var(--vermilion);
  background: rgba(196, 62, 44, 0.08);
  border-left: 3px solid var(--vermilion);
}

.editor__modal-ok {
  margin: 0 0 12px;
  font-size: 0.78rem;
  color: #2f7a4f;
}

.editor__inline-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--cobalt);
  font-family: var(--font-mono);
  font-size: inherit;
  text-decoration: underline;
  cursor: pointer;
}

.editor__modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 18px;
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
