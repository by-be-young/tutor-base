<template>
  <div class="editor">
    <section v-if="article" class="panel editor__head">
      <div class="editor__head-main">
        <button type="button" class="editor__back" aria-label="返回课件页" @click="goCourseware">
          <i class="fas fa-arrow-left" aria-hidden="true"></i>
        </button>
        <div class="editor__head-text">
          <p class="editor__crumb">{{ currentPath }}</p>
          <h1 class="editor__title">{{ currentTitle }}</h1>
          <div class="editor__chips">
            <span class="chip">{{ currentSubject }}</span>
            <span class="chip mono">#{{ articleId }}</span>
            <span class="chip" :class="dirty ? 'chip--amber' : 'chip--lime'">
              {{ dirty ? '有未保存改动' : '已发布' }}
            </span>
          </div>
        </div>
      </div>
      <div class="editor__head-actions">
        <button type="button" class="btn btn--ghost btn--sm" @click="guideOpen = true">
          <i class="fas fa-book-open-reader" aria-hidden="true"></i>
          发布教程
        </button>
        <button type="button" class="btn btn--primary btn--sm" :disabled="busy || !dirty" @click="save">
          <i v-if="busy" class="fas fa-circle-notch fa-spin" aria-hidden="true"></i>
          保存并更新
        </button>
      </div>
    </section>

    <template v-if="article">
      <section class="panel editor__bar">
        <div class="editor__insert">
          <label class="editor__layout">
            <span>布局</span>
            <select v-model="layoutId">
              <option v-for="layout in layouts" :key="layout.layoutId" :value="layout.layoutId">
                {{ layout.layoutId }} · {{ layout.name }}
              </option>
            </select>
          </label>
          <button type="button" class="btn btn--soft btn--sm" :disabled="busy" @click="insertLayout">
            插入布局
          </button>
          <button type="button" class="btn btn--soft btn--sm" :disabled="busy" @click="insertQuestion">
            插入题目
          </button>
          <button type="button" class="btn btn--soft btn--sm" :disabled="busy" @click="openImageModal">
            插入图片
          </button>
        </div>
        <div class="editor__view-actions">
          <button type="button" class="btn btn--ghost btn--sm" :disabled="!markdown"
            @click="openPreview()">
            <i class="fas fa-eye" aria-hidden="true"></i>
            预览
          </button>
          <button type="button" class="btn btn--ghost btn--sm" :disabled="!markdown"
            @click="openPreview(true)">
            <i class="fas fa-file-pdf" aria-hidden="true"></i>
            PDF
          </button>
          <button type="button" class="btn btn--ghost btn--sm" :disabled="busy" @click="openAnswerSetting">
            <i class="fas fa-key" aria-hidden="true"></i>
            设置答案
          </button>
        </div>
      </section>

      <div class="editor__workspace">
        <aside class="panel editor__outline">
          <header class="editor__outline-head">
            <h2>大纲</h2>
            <span class="editor__outline-count">{{ outline.length }}</span>
          </header>
          <p v-if="!outline.length" class="editor__outline-empty">正文里还没有 `# ` 章节。</p>
          <ul v-else class="editor__outline-list">
            <li v-for="(section, index) in outline" :key="index">
              <div class="editor__outline-item">
                <span class="editor__outline-no">{{ String(index + 1).padStart(2, '0') }}</span>
                <div class="editor__outline-text">
                  <strong>{{ section.title }}</strong>
                  <em v-if="section.layout">布局 {{ section.layout }}</em>
                </div>
                <span class="editor__outline-move">
                  <button type="button" aria-label="上移章节" :disabled="index === 0"
                    @click="moveSection(index, -1)">↑</button>
                  <button type="button" aria-label="下移章节" :disabled="index === outline.length - 1"
                    @click="moveSection(index, 1)">↓</button>
                </span>
              </div>
            </li>
          </ul>
        </aside>

        <section class="panel editor__pane">
          <header class="editor__pane-head">
            <span class="editor__pane-title">Markdown</span>
            <span v-if="dirty" class="editor__pane-dirty">未保存</span>
          </header>
          <textarea ref="editorTextarea" v-model="markdown" class="editor__code" spellcheck="false"
            @input="dirty = true" />
        </section>
      </div>
    </template>

    <section v-else class="panel editor__empty">
      <div class="editor__empty-icon">
        <i :class="loading ? 'fas fa-circle-notch fa-spin' : 'fas fa-file-lines'" aria-hidden="true"></i>
      </div>
      <h2>{{ loading ? '正在载入课件…' : '请从课件页选择一篇课件' }}</h2>
      <p v-if="loadError">{{ loadError }}</p>
      <div v-if="!loading" class="editor__empty-actions">
        <button type="button" class="btn btn--primary btn--sm" @click="goCourseware">返回课件页</button>
        <button v-if="loadError" type="button" class="btn btn--ghost btn--sm" @click="retryLoad">重试</button>
      </div>
    </section>

    <CoursewarePublishGuide :open="guideOpen" @close="guideOpen = false" />

    <div v-if="imageOpen" class="editor__modal" role="dialog" aria-modal="true" aria-label="插入图片"
      @click.self="closeImageModal">
      <div class="editor__modal-card">
        <header class="editor__modal-head">
          <h2>插入图片</h2>
          <button type="button" class="editor__modal-close" :disabled="imageUploading" @click="closeImageModal">
            <i class="fas fa-times" aria-hidden="true"></i>
          </button>
        </header>

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
          第 {{ imageSection }} 节 · 本节第 {{ imageFigure }} 张
        </p>

        <p v-if="imageChecking" class="editor__modal-hint">正在检查是否重名…</p>
        <p v-else-if="imageConflict" class="editor__modal-alert">
          已有同名文件，建议使用
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
          <button type="button" class="btn btn--ghost btn--sm" :disabled="imageUploading" @click="closeImageModal">
            取消
          </button>
          <button type="button" class="btn btn--primary btn--sm"
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CoursewarePublishGuide from '@/components/admin/CoursewarePublishGuide.vue'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { sectionAtCursor, suggestedImageName } from '@/utils/coursewareMediaName'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const layouts = ref([])
const article = ref(null)
const articleId = ref(null)
const layoutId = ref('A')
const markdown = ref('')
const dirty = ref(false)
const busy = ref(false)
const loading = ref(false)
const loadError = ref('')
const guideOpen = ref(false)

const currentTitle = computed(
  () => article.value?.title || (articleId.value ? `课件 #${articleId.value}` : '')
)
const currentSubject = computed(() => article.value?.subject || '')
const currentPath = computed(() => article.value?.logicalPath || '')

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

function goCourseware() {
  router.push('/admin/courseware')
}

function requestedArticleId() {
  const value = Number(route.query.articleId)
  return Number.isFinite(value) && value > 0 ? value : null
}

async function loadArticle(id) {
  loading.value = true
  loadError.value = ''
  try {
    const content = await coursewareGateway.getArticle(id)
    article.value = content
    articleId.value = Number(content?.articleId) || id
    markdown.value = String(content?.markdown || '')
    dirty.value = false
    const directive = markdown.value.match(/<!--\s*@layout:\s*([A-Za-z0-9_-]+)\s*-->/)
    if (directive && layouts.value.some(layout => layout.layoutId === directive[1])) {
      layoutId.value = directive[1]
    }
  } catch (error) {
    article.value = null
    articleId.value = id
    markdown.value = ''
    loadError.value = error?.message || '课件载入失败'
  } finally {
    loading.value = false
  }
}

function retryLoad() {
  const id = requestedArticleId()
  if (id) loadArticle(id)
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

/** 保存即更新线上版本：同一科目版本号 +1，学员立即看到。 */
async function save() {
  if (busy.value || !articleId.value) return false
  if (!markdown.value.trim()) {
    showToast('正文不能为空', 'warning')
    return false
  }
  busy.value = true
  try {
    const result = await coursewareGateway.saveArticle(articleId.value, markdown.value)
    dirty.value = false
    const heading = markdown.value.match(/^#\s+(.+)$/m)
    article.value = {
      ...article.value,
      markdown: markdown.value,
      title: heading ? heading[1].trim() : article.value?.title
    }
    showToast(`已更新到 v${result?.version ?? ''}`, 'success')
    return true
  } catch (error) {
    reportError(error)
    return false
  } finally {
    busy.value = false
  }
}

/**
 * 学员视角预览：有未保存改动时把当前正文放到 localStorage，预览页直接读它，
 * 不落库、不发布；已保存时则读线上版本。
 *
 * @param {boolean} print 直接以打印视图打开（浏览器里选「另存为 PDF」）
 */
function openPreview(print = false) {
  if (!markdown.value.trim()) {
    showToast('正文还是空的，先写点内容再预览', 'warning')
    return
  }
  const id = articleId.value
  if (!id) return
  const query = { mode: 'preview' }
  if (dirty.value) {
    window.localStorage.setItem(`courseware-preview:${id}`, markdown.value)
    query.localPreview = '1'
  }
  if (currentSubject.value) query.subject = currentSubject.value
  if (currentTitle.value) query.title = currentTitle.value
  if (currentPath.value) query.path = currentPath.value
  if (print) query.print = '1'
  const target = router.resolve({ name: 'CoursewarePreview', params: { id }, query })
  window.open(target.href, '_blank', 'noopener')
  showToast(print ? '正在打开打印视图' : '正在打开学员视角预览', 'success')
}

/** 设置答案：有未保存改动先保存并更新，保证答案对应最新题号。 */
async function openAnswerSetting() {
  if (!articleId.value) return
  if (dirty.value && !(await save())) return
  const target = router.resolve({
    name: 'ArticleDetail',
    params: { id: articleId.value },
    query: { mode: 'answer' }
  })
  window.open(target.href, '_blank', 'noopener')
  showToast('正在打开标准答案设置页', 'success')
}

onMounted(async () => {
  try {
    layouts.value = await coursewareGateway.listLayouts()
    if (layouts.value.length) layoutId.value = layouts.value[0].layoutId
  } catch (error) {
    reportError(error)
  }
  const id = requestedArticleId()
  if (id) {
    await loadArticle(id)
  } else {
    loadError.value = ''
  }
})

onBeforeUnmount(() => {
  if (imageTimer) clearTimeout(imageTimer)
  if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
})

watch(() => route.query.articleId, () => {
  const id = requestedArticleId()
  if (!id) {
    article.value = null
    articleId.value = null
    markdown.value = ''
    dirty.value = false
    loadError.value = ''
    return
  }
  if (id !== Number(article.value?.articleId)) loadArticle(id)
})
</script>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 20px;
}

.editor__head-main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.editor__back {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--line, #d9d5cb);
  border-radius: 12px;
  background: var(--paper-1, #fffdf8);
  color: var(--text-2, #5b574e);
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}

.editor__back:hover {
  border-color: var(--ink, #22201c);
  color: var(--ink, #22201c);
  transform: translateX(-2px);
}

.editor__head-text {
  min-width: 0;
}

.editor__crumb {
  margin: 0 0 4px;
  font-family: var(--font-mono);
  font-size: 0.74rem;
  color: var(--text-dim, #8c877c);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.editor__title {
  margin: 0;
  font-size: 1.45rem;
  line-height: 1.25;
  letter-spacing: -0.01em;
  color: var(--ink, #22201c);
}

.editor__chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.editor__head-actions {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 14px;
}

.editor__insert,
.editor__view-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.editor__layout {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding-left: 4px;
}

.editor__layout span {
  font-size: 0.76rem;
  color: var(--text-dim, #8c877c);
}

.editor__layout select {
  height: 34px;
  padding: 0 28px 0 10px;
  border: 1px solid var(--line, #d9d5cb);
  border-radius: 10px;
  background: var(--paper-1, #fffdf8);
  color: var(--ink, #22201c);
  font-size: 0.82rem;
  cursor: pointer;
}

.editor__workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 264px;
  gap: 14px;
  align-items: start;
}

.editor__outline {
  order: 2;
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 170px);
  overflow: auto;
  padding: 14px;
}

.editor__outline-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.editor__outline-head h2 {
  margin: 0;
  font-size: 0.86rem;
  color: var(--ink, #22201c);
}

.editor__outline-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--paper-2, #f4f0e6);
  font-family: var(--font-mono);
  font-size: 0.7rem;
  color: var(--text-2, #5b574e);
}

.editor__outline-empty {
  margin: 16px 0;
  font-size: 0.78rem;
  line-height: 1.6;
  color: var(--text-dim, #8c877c);
}

.editor__outline-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.editor__outline-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  border-radius: 10px;
  transition: background 0.15s ease;
}

.editor__outline-item:hover {
  background: var(--paper-2, #f4f0e6);
}

.editor__outline-no {
  flex: none;
  font-family: var(--font-mono);
  font-size: 0.66rem;
  line-height: 1.7;
  color: var(--text-dim, #8c877c);
}

.editor__outline-text {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.editor__outline-text strong {
  font-size: 0.78rem;
  font-weight: 500;
  line-height: 1.5;
  color: var(--ink, #22201c);
  overflow-wrap: anywhere;
}

.editor__outline-text em {
  font-style: normal;
  font-family: var(--font-mono);
  font-size: 0.64rem;
  color: var(--text-dim, #8c877c);
}

.editor__outline-move {
  flex: none;
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.editor__outline-item:hover .editor__outline-move,
.editor__outline-move:focus-within {
  opacity: 1;
}

.editor__outline-move button {
  width: 22px;
  height: 22px;
  border: 1px solid var(--line, #d9d5cb);
  border-radius: 6px;
  background: var(--paper-1, #fffdf8);
  color: var(--text-2, #5b574e);
  font-size: 0.66rem;
  cursor: pointer;
}

.editor__outline-move button:disabled {
  opacity: 0.35;
  cursor: default;
}

.editor__pane {
  order: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor__pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line, #d9d5cb);
}

.editor__pane-title {
  font-family: var(--font-mono);
  font-size: 0.76rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--text-dim, #8c877c);
}

.editor__pane-dirty {
  font-size: 0.7rem;
  color: #a5691a;
}

.editor__code {
  width: 100%;
  min-height: calc(100vh - 320px);
  padding: 18px 20px 24px;
  border: 0;
  background: transparent;
  color: var(--ink, #22201c);
  font-family: var(--font-mono);
  font-size: 0.88rem;
  line-height: 1.75;
  resize: vertical;
  outline: none;
  tab-size: 2;
}

.editor__code::selection {
  background: rgba(255, 214, 102, 0.45);
}

.editor__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 72px 24px;
  text-align: center;
}

.editor__empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border: 1px solid var(--line, #d9d5cb);
  border-radius: 16px;
  background: var(--paper-2, #f4f0e6);
  color: var(--text-dim, #8c877c);
  font-size: 1.1rem;
}

.editor__empty h2 {
  margin: 4px 0 0;
  font-size: 1rem;
  color: var(--ink, #22201c);
}

.editor__empty p {
  max-width: 520px;
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.6;
  color: var(--text-2, #5b574e);
}

.editor__empty-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.editor__modal {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(24, 22, 18, 0.42);
  backdrop-filter: blur(3px);
}

.editor__modal-card {
  width: min(440px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  padding: 18px;
  border: 1px solid var(--line, #d9d5cb);
  border-radius: 18px;
  background: var(--paper-1, #fffdf8);
  box-shadow: 0 18px 50px rgba(24, 22, 18, 0.18);
}

.editor__modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.editor__modal-head h2 {
  margin: 0;
  font-size: 1rem;
  color: var(--ink, #22201c);
}

.editor__modal-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--text-dim, #8c877c);
  cursor: pointer;
}

.editor__modal-close:hover {
  background: var(--paper-2, #f4f0e6);
  color: var(--ink, #22201c);
}

.editor__upload {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px dashed var(--line, #d9d5cb);
  border-radius: 12px;
  background: var(--paper-2, #f4f0e6);
  color: var(--text-2, #5b574e);
  font-size: 0.8rem;
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.editor__upload:hover {
  border-color: var(--ink, #22201c);
}

.editor__upload input {
  display: none;
}

.editor__upload--image {
  margin-bottom: 12px;
}

.editor__image-preview {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.editor__image-preview img {
  width: 100%;
  max-height: 220px;
  object-fit: contain;
  border-radius: 12px;
  background: var(--paper-2, #f4f0e6);
}

.editor__image-preview span {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  color: var(--text-dim, #8c877c);
}

.editor__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.editor__field span {
  font-size: 0.76rem;
  color: var(--text-dim, #8c877c);
}

.editor__field input {
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--line, #d9d5cb);
  border-radius: 10px;
  background: var(--paper-1, #fffdf8);
  color: var(--ink, #22201c);
  font-family: var(--font-mono);
  font-size: 0.8rem;
  outline: none;
}

.editor__field input:focus {
  border-color: var(--ink, #22201c);
}

.editor__modal-meta {
  margin: 10px 0 0;
  font-size: 0.72rem;
  color: var(--text-dim, #8c877c);
}

.editor__modal-hint,
.editor__modal-alert,
.editor__modal-ok {
  margin: 8px 0 0;
  font-size: 0.76rem;
  line-height: 1.55;
}

.editor__modal-hint {
  color: var(--text-dim, #8c877c);
}

.editor__modal-alert {
  color: #b42318;
}

.editor__modal-ok {
  color: #2f7d4f;
}

.editor__inline-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--ink, #22201c);
  font-family: var(--font-mono);
  text-decoration: underline;
  cursor: pointer;
}

.editor__modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .editor__workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .editor__outline {
    order: 1;
    position: static;
    max-height: none;
  }

  .editor__pane {
    order: 2;
  }
}

@media (max-width: 680px) {
  .editor__head {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .editor__head-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .editor__title {
    font-size: 1.2rem;
  }

  .editor__insert,
  .editor__view-actions {
    width: 100%;
  }

  .editor__code {
    min-height: 58vh;
    padding: 14px 16px 20px;
  }
}
</style>