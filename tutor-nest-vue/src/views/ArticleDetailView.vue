<!-- src/views/ArticleDetailView.vue -->
<template>
    <div class="reader" :class="['reader--fs-' + fontScale, { 'reader--focus': focusMode }]">
        <!-- 顶部阅读进度 -->
        <div class="reader-progress" aria-hidden="true">
            <span class="reader-progress__bar" :style="{ width: progressWidth }"></span>
        </div>

        <div class="reader-shell">
            <!-- 文章信息 / 阅读进度 -->
            <header class="reader-hero panel panel--glow">
                <div class="reader-hero__halftone" aria-hidden="true"></div>
                <div class="reader-hero__top">
                    <span class="eyebrow">NEST / 学巢阅读</span>
                    <span class="chip reader-mode" :class="modeChipClass">
                        <i class="fas" :class="modeIcon"></i>{{ modeLabel }}
                    </span>
                </div>
                <h1 class="reader-title">{{ blogTitle }}</h1>
                <div class="reader-meta">
                    <span v-if="subjectMeta.label" class="chip" :class="'chip--' + subjectMeta.tone">
                        <i class="fas" :class="subjectMeta.icon"></i>{{ subjectMeta.label }}
                    </span>
                    <span v-if="dateLabel" class="chip">
                        <i class="far fa-calendar"></i>{{ dateLabel }}
                    </span>
                    <span v-if="questionCount" class="chip chip--violet">
                        <i class="fas fa-list-ol"></i>{{ questionCount }} 道题
                    </span>
                    <span v-if="currentMode === 'study' && questionCount" class="chip chip--lime">
                        <i class="fas fa-book-medical"></i>错题可归档
                    </span>
                </div>
                <div class="reader-meter">
                    <div class="progress">
                        <span class="progress__bar" :style="{ width: progressWidth }"></span>
                    </div>
                    <span class="reader-meter__label mono">已读 {{ progressPercent }}%</span>
                </div>
            </header>

            <!-- 阅读工具条：字号 / 专注 / 目录 / 答题卡 / 布局 -->
            <div class="reader-tools" role="toolbar" aria-label="阅读工具">
                <div class="seg reader-fontsize" role="group" aria-label="正文字号">
                    <button v-for="option in FONT_SCALE_OPTIONS" :key="option.value" type="button"
                        class="seg__btn reader-fontsize__btn" :class="{ 'is-active': fontScale === option.value }"
                        :aria-pressed="fontScale === option.value" :title="option.title"
                        @click="setFontScale(option.value)">
                        {{ option.label }}
                    </button>
                </div>

                <span class="reader-tools__divider" aria-hidden="true"></span>

                <button type="button" class="reader-tool" :class="{ 'is-active': focusMode }" :aria-pressed="focusMode"
                    title="专注模式：收起装饰与侧栏，专注正文" @click="toggleFocusMode">
                    <i class="fas" :class="focusMode ? 'fa-compress' : 'fa-expand'"></i>
                    <span>专注</span>
                </button>
                <button v-if="showTocFab" type="button" class="reader-tool" :class="{ 'is-active': tocActive }"
                    :aria-expanded="tocActive" title="目录" @click="toggleToc">
                    <i class="fas fa-list"></i>
                    <span>目录</span>
                </button>
                <button v-if="showAnswerSheetFab" type="button" class="reader-tool"
                    :class="{ 'is-active': answerSheetActive }" :aria-expanded="answerSheetActive" title="答题卡"
                    @click="toggleAnswerSheet">
                    <i class="fas fa-th"></i>
                    <span>答题卡</span>
                </button>
                <button v-if="hasSidebar" type="button" class="reader-tool" :title="layoutToggleTitle"
                    @click="cycleLayoutMode">
                    <i class="fas" :class="layoutToggleIcon"></i>
                    <span>布局</span>
                </button>

                <span v-if="submittedCount > 0" class="reader-tools__hint mono">{{ submittedCount }} 题已提交</span>

                <button type="button" class="reader-tool reader-tool--end" title="回到顶部" aria-label="回到顶部"
                    @click="scrollToTop">
                    <i class="fas fa-arrow-up"></i>
                </button>
            </div>

            <!-- 正文：纸白卡片 + 墨线硬阴影 -->
            <main class="reader-sheet">
                <div v-if="!renderedContent" class="reader-loading">
                    <span class="spinner"></span>
                    <span>正在展开讲义…</span>
                </div>
                <div class="detail-layout-wrapper" :class="['layout-' + layoutMode, { 'has-sidebar-content': hasSidebar }]">
                    <div class="detail-body" ref="detailBodyRef" v-html="renderedContent"></div>
                </div>
            </main>
        </div>

        <!-- 悬浮提交按钮 -->
        <div v-if="showFab" class="fab-container">
            <button class="fab-btn" :class="fabStatusClass" :disabled="isSubmitting"
                :aria-label="currentMode === 'answer' ? '保存标准答案' : '提交答案'" @click="handleFabClick"
                v-html="fabButtonHtml"></button>
        </div>

        <!-- 桌面端右侧悬浮快捷组（移动端收纳进工具条） -->
        <div class="fab-right-group">
            <button v-if="hasSidebar" type="button" class="layout-toggle-fab" :title="layoutToggleTitle"
                :aria-label="layoutToggleTitle" @click="cycleLayoutMode">
                <i class="fas" :class="layoutToggleIcon"></i>
            </button>
            <button v-if="showAnswerSheetFab" type="button" class="answer-sheet-fab" title="答题卡" aria-label="答题卡"
                @click="toggleAnswerSheet">
                <i class="fas fa-th"></i>
            </button>
            <button v-if="showTocFab" type="button" class="toc-fab" title="目录" aria-label="目录" @click="toggleToc">
                <i class="fas fa-list"></i>
            </button>
        </div>

        <!-- 答题卡侧栏 -->
        <div v-if="answerSheetVisible" class="answer-sheet-overlay"
            :class="{ 'overlay-active': answerSheetActive }" @click.self="closeAnswerSheet">
            <aside class="answer-sheet-sidebar" :class="{ 'sidebar-open': answerSheetActive }" @click.stop role="dialog"
                aria-modal="true" aria-label="答题卡">
                <div class="answer-sheet-header">
                    <div class="answer-sheet-heading">
                        <span class="answer-sheet-title">答题卡</span>
                        <span class="answer-sheet-sub mono">{{ questionCount }} 题 · 已提交 {{ submittedCount }}</span>
                    </div>
                    <button type="button" class="answer-sheet-close-btn" aria-label="关闭答题卡"
                        @click="closeAnswerSheet">
                        <i class="fas fa-times"></i>
                    </button>
                </div>

                <div class="answer-sheet-legend">
                    <span class="legend-item"><i class="legend-dot is-unsubmitted"></i>未提交</span>
                    <span class="legend-item"><i class="legend-dot is-pending"></i>待批阅</span>
                    <span class="legend-item"><i class="legend-dot is-correct"></i>正确</span>
                    <span class="legend-item"><i class="legend-dot is-wrong"></i>错误</span>
                </div>

                <div class="answer-sheet-body">
                    <div v-for="(section, sIdx) in answerSheetSections" :key="sIdx" class="answer-sheet-section">
                        <div class="answer-sheet-section-title">{{ section.h1Title }}</div>
                        <div class="answer-sheet-grid">
                            <button v-for="q in section.questions" :key="q.questionId" type="button"
                                class="answer-sheet-cell" :class="'cell-' + q.status"
                                :title="`第${q.localIndex}题: ${q.statusLabel}`"
                                :aria-label="`第${q.localIndex}题，${q.statusLabel}，跳转到该章节`"
                                @click="scrollToHeading(section.headingId)">
                                {{ q.localIndex }}
                            </button>
                        </div>
                    </div>
                    <div v-if="answerSheetSections.length === 0" class="answer-sheet-empty">
                        暂无题目
                    </div>
                </div>

                <div class="answer-sheet-foot">
                    <button type="button" class="btn btn--primary btn--block" @click="closeAnswerSheet">
                        <i class="fas fa-pen"></i>继续作答
                    </button>
                </div>
            </aside>
        </div>

        <!-- 目录侧栏 -->
        <div v-if="tocVisible" class="answer-sheet-overlay" :class="{ 'overlay-active': tocActive }"
            @click.self="closeToc">
            <aside class="answer-sheet-sidebar toc-sidebar" :class="{ 'sidebar-open': tocActive }" @click.stop
                role="dialog" aria-modal="true" aria-label="目录">
                <div class="answer-sheet-header">
                    <div class="answer-sheet-heading">
                        <span class="answer-sheet-title">目录</span>
                        <span class="answer-sheet-sub mono">{{ tocData.length }} 个章节</span>
                    </div>
                    <button type="button" class="answer-sheet-close-btn" aria-label="关闭目录" @click="closeToc">
                        <i class="fas fa-times"></i>
                    </button>
                </div>

                <div class="toc-body">
                    <div v-for="(h1, idx) in tocData" :key="h1.id" class="toc-h1-item">
                        <div class="toc-h1-title" :class="{ 'is-open': expandedTocH1 === idx }"
                            @click="toggleTocH1(idx)">
                            <button type="button" class="toc-toggle" :aria-expanded="expandedTocH1 === idx"
                                :aria-label="`展开或收起 ${h1.text}`" @click.stop="toggleTocH1(idx)">
                                <i class="fas toc-arrow"
                                    :class="expandedTocH1 === idx ? 'fa-chevron-down' : 'fa-chevron-right'"></i>
                            </button>
                            <span class="toc-text" @click.stop="scrollToHeading(h1.id)">{{ h1.text }}</span>
                        </div>
                        <div v-if="expandedTocH1 === idx && h1.children.length" class="toc-h2-list">
                            <button v-for="h2 in h1.children" :key="h2.id" type="button" class="toc-h2-item"
                                @click="scrollToHeading(h2.id)">
                                {{ h2.text }}
                            </button>
                        </div>
                    </div>
                    <div v-if="tocData.length === 0" class="answer-sheet-empty">
                        暂无目录
                    </div>
                </div>
            </aside>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { useWrongQuestionsStore } from '@/stores/wrongQuestionsStore'
import { useKatex } from '@/composables/useKatex'
import { useImageEmbed } from '@/composables/useImageEmbed'
import { useDrawingInDetail } from '@/composables/useDrawing'
import { learningGateway } from '@/gateways/learningGateway'
import { showToast } from '@/utils/toast'
import { ANSWER_TOKEN, STEM_TOKEN, STEM_TOKEN_ONE, buildQuestionTextMap, normalizeMarkerIndentation } from '@/utils/questionText'
import { marked } from 'marked'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const blogStore = useArticleStore()
const wrongQuestionsStore = useWrongQuestionsStore()
const { renderMath } = useKatex()
const { processMarkdown, observe, disconnect } = useImageEmbed()
useDrawingInDetail()

// 配置图片基础路径
const { setBasePath } = useImageEmbed()
setBasePath('articles/图片/')

// ========== 状态管理 ==========
const detailBodyRef = ref(null)
const blogTitle = ref('加载中...')
const renderedContent = ref('')
const currentMode = ref('study') // study, review, answer
const isSubmitting = ref(false)
const fabStatusClass = ref('')
const contentVersion = ref(0)
const layoutMode = ref('both') // both, left, right
const hasSidebar = ref(false)

// 题目相关状态
const questionCount = ref(0)
const questionIdList = ref([])
const answerKeyMap = ref(new Map())
const submissionMap = ref(new Map())
const slotNodes = ref(new Map())
const statusNodes = ref(new Map())
let loadedStudyState = null

// 答题卡状态
const answerSheetVisible = ref(false)
const answerSheetActive = ref(false)
const h1QuestionSections = ref([])

// 目录状态
const tocVisible = ref(false)
const tocActive = ref(false)
const tocData = ref([])
const expandedTocH1 = ref(null)

// ========== 阅读体验（纯展示状态，不影响业务数据） ==========
const FONT_SCALE_KEY = 'nest-reader-font-scale'
const FONT_SCALE_OPTIONS = [
    { value: 'sm', label: '小', title: '小号正文' },
    { value: 'md', label: '中', title: '中号正文' },
    { value: 'lg', label: '大', title: '大号正文' }
]
const fontScale = ref(readStoredFontScale())
const focusMode = ref(false)
const readingProgress = ref(0)
let progressFrame = 0

function readStoredFontScale() {
    try {
        const stored = window.localStorage.getItem(FONT_SCALE_KEY)
        return FONT_SCALE_OPTIONS.some(option => option.value === stored) ? stored : 'md'
    } catch (error) {
        return 'md'
    }
}

function setFontScale(value) {
    if (!FONT_SCALE_OPTIONS.some(option => option.value === value)) return
    fontScale.value = value
    try {
        window.localStorage.setItem(FONT_SCALE_KEY, value)
    } catch (error) {
        /* localStorage 不可用时仅保留本次会话 */
    }
}

function toggleFocusMode() {
    focusMode.value = !focusMode.value
}

function scrollToTop() {
    window.scrollTo({ top: 0, behavior: 'smooth' })
}

function updateReadingProgress() {
    const max = document.documentElement.scrollHeight - window.innerHeight
    const ratio = max > 0 ? window.scrollY / max : 0
    readingProgress.value = Math.min(100, Math.max(0, ratio * 100))
}

function handleReaderScroll() {
    if (progressFrame) return
    progressFrame = requestAnimationFrame(() => {
        progressFrame = 0
        updateReadingProgress()
    })
}

function handleReaderKeydown(event) {
    if (event.key !== 'Escape') return
    if (answerSheetVisible.value) closeAnswerSheet()
    if (tocVisible.value) closeToc()
}

// ========== 计算属性 ==========
const blogId = computed(() => {
    const id = route.params.id
    return id ? parseInt(id, 10) : null
})

const currentArticle = computed(() => {
    return blogStore.blogData.find(b => b.id === blogId.value)
})

const showFab = computed(() => {
    return currentMode.value === 'study' || currentMode.value === 'answer'
})

const fabButtonHtml = computed(() => {
    if (currentMode.value === 'study') {
        return '<i class="fas fa-paper-plane"></i> 提交'
    } else if (currentMode.value === 'answer') {
        return '<i class="fas fa-save"></i> 保存'
    }
    return ''
})

const studentId = computed(() => {
    if (currentMode.value === 'study') {
        const user = authStore.currentUser
        if (user) {
            const numericId = Number(user.id)
            return Number.isFinite(numericId) ? numericId : null
        }
    } else if (currentMode.value === 'review') {
        const param = route.query.studentId
        if (param) {
            const num = Number(param)
            return Number.isFinite(num) ? num : null
        }
    }
    return null
})

const showAnswerSheetFab = computed(() => {
    return currentMode.value === 'study' && h1QuestionSections.value.length > 0
})

const answerSheetSections = computed(() => {
    return h1QuestionSections.value.map(section => ({
        ...section,
        questions: section.questions.map(q => {
            const submission = submissionMap.value.get(q.questionId)
            let status, statusLabel
            if (!submission) {
                status = 'unsubmitted'
                statusLabel = '未提交'
            } else if (submission.review_status !== 'reviewed') {
                status = 'pending'
                statusLabel = '待批阅'
            } else {
                status = submission.review_result || 'unsubmitted'
                const labels = { correct: '正确', partial: '半对', wrong: '错误' }
                statusLabel = labels[status] || '已批阅'
            }
            return { ...q, status, statusLabel }
        })
    }))
})

const showTocFab = computed(() => {
    return tocData.value.length > 0
})

const layoutToggleIcon = computed(() => {
    if (layoutMode.value === 'both') return 'fa-columns'
    if (layoutMode.value === 'left') return 'fa-file-lines'
    return 'fa-pencil'
})

const layoutToggleTitle = computed(() => {
    if (layoutMode.value === 'both') return '点击切换为仅显示正文'
    if (layoutMode.value === 'left') return '点击切换为仅显示答题区'
    return '点击切换为双栏显示'
})

// ========== 阅读页展示信息 ==========
const modeLabel = computed(() => {
    if (currentMode.value === 'review') return '批阅模式'
    if (currentMode.value === 'answer') return '答案设置'
    return '学习模式'
})

const modeIcon = computed(() => {
    if (currentMode.value === 'review') return 'fa-clipboard-check'
    if (currentMode.value === 'answer') return 'fa-key'
    return 'fa-book-open'
})

const modeChipClass = computed(() => {
    if (currentMode.value === 'review') return 'chip--amber'
    if (currentMode.value === 'answer') return 'chip--violet'
    return 'chip--cyan'
})

const subjectMeta = computed(() => {
    const series = currentArticle.value?.series || ''
    if (series.includes('化学')) return { label: series, tone: 'cyan', icon: 'fa-flask' }
    if (series.includes('初中英语')) return { label: series, tone: 'amber', icon: 'fa-language' }
    if (series.includes('英语')) return { label: series, tone: 'violet', icon: 'fa-language' }
    return { label: series, tone: 'violet', icon: 'fa-book' }
})

const dateLabel = computed(() => {
    const date = currentArticle.value?.date || ''
    return date ? date.replace(/-/g, '.') : ''
})

const progressPercent = computed(() => Math.round(readingProgress.value))

const progressWidth = computed(() => `${readingProgress.value.toFixed(2)}%`)

const submittedCount = computed(() => submissionMap.value.size)

// ========== 工具函数 ==========
function escapeHtml(text) {
    return String(text ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
}

function escapeUnderscoresInMath(markdown) {
    return markdown.replace(/(\$\$[^$]*\$\$|\$[^$]*\$)/g, (match) => {
        return match.replace(/_/g, '\\_')
    })
}

function renderMarkdown(markdown) {
    const safe = escapeUnderscoresInMath(markdown)
    return marked.parse(safe)
}

// ========== 题目占位符注入 ==========
// 题目文本映射：questionId → 题目内容（供错题本自动收集使用）
const questionTextMap = ref(new Map())

function injectQuestionSlots(markdown) {
    questionTextMap.value = new Map()

    // 缩进 4 空格或制表符的独行占位符会被 Markdown 当成代码块（卡片变成一行纯文本），先归一化
    markdown = normalizeMarkerIndentation(markdown)

    // 第一遍：大题分组框。
    // 区间标记【题干N-M】与最后一个小题占位符之间没有 `---`（同一栏）时，
    // 用一个大框包住公共题干及全部小题（纯样式作用，不影响解析）
    const lines = markdown.split('\n')
    const openLines = new Map()   // 行号 → 打开次数
    const closeLines = new Map()  // 行号 → 关闭次数
    for (let i = 0; i < lines.length; i++) {
        const stemMatch = lines[i].match(STEM_TOKEN_ONE)
        if (!stemMatch) continue
        const n = Number(stemMatch[1])
        const m = stemMatch[2] ? Number(stemMatch[2]) : n
        if (m <= n) continue // 单题标记或非法区间不分组

        // 找区间终点：
        //   优先用区间内最后一个小题的题干标记行，再向后扫描到其内容结束
        //   （遇到 `---`、下一个题干标记、`#` 标题或文档末尾为止；答题占位符行不结束，便于同栏混排）
        let lastStemLine = -1    // 区间内最后一个小题（单题）题干标记行
        let lastAnswerLine = -1  // 区间内最后一个答题占位符行
        for (let j = i + 1; j < lines.length; j++) {
            const sm = lines[j].match(STEM_TOKEN_ONE)
            if (sm && sm[2] === undefined) {
                const id = Number(sm[1])
                if (id >= n && id <= m) lastStemLine = j
            }
            for (const am of lines[j].matchAll(ANSWER_TOKEN)) {
                const id = am[1] === '' ? null : Number(am[1])
                if (id !== null && id >= n && id <= m) lastAnswerLine = j
            }
        }

        let closeLine
        if (lastStemLine !== -1) {
            // 从最后小题题干标记之后扫描内容结束位置
            closeLine = lastStemLine
            for (let j = lastStemLine + 1; j < lines.length; j++) {
                const t = lines[j].trim()
                if (t === '---' || STEM_TOKEN_ONE.test(lines[j]) || /^#\s/.test(t)) {
                    closeLine = j - 1
                    break
                }
                closeLine = j
            }
        } else if (lastAnswerLine !== -1) {
            closeLine = lastAnswerLine
        } else {
            continue
        }

        // 区间起点到终点之间出现 `---` 说明跨栏（如布局 B），不分组
        let hasSep = false
        for (let j = i; j <= closeLine; j++) {
            if (lines[j].trim() === '---') {
                hasSep = true
                break
            }
        }
        if (hasSep) continue

        openLines.set(i, (openLines.get(i) || 0) + 1)
        closeLines.set(closeLine, (closeLines.get(closeLine) || 0) + 1)
    }

    // 按行重建：组开始行前插入 `<div class="question-group">` + 空行，组结束行后插入 空行 + `</div>`
    const groupedLines = []
    for (let i = 0; i < lines.length; i++) {
        for (let k = 0; k < (openLines.get(i) || 0); k++) {
            groupedLines.push('<div class="question-group">', '')
        }
        groupedLines.push(lines[i])
        for (let k = 0; k < (closeLines.get(i) || 0); k++) {
            groupedLines.push('', '</div>')
        }
    }
    const groupedMarkdown = groupedLines.join('\n')

    // 第二遍：渲染 markdown
    //   答题标记 → 答题卡片（构建 questionId → 显示顺序号 映射）
    //   题干标记 → 样式化题号标签（显示顺序号，如「第 1 题」「第 1-3 题」，与实际 id 无关）
    let autoCounter = 1
    const usedIndices = new Set()
    const questionIds = []
    const idOrderMap = new Map()      // questionId → 显示顺序号（从 1 按出现顺序递增）
    let slotCount = 0
    let orderCounter = 0

    const processed = groupedMarkdown
        .replace(ANSWER_TOKEN, (_, numericId) => {
            let questionId
            if (numericId !== '') {
                questionId = String(numericId)
                usedIndices.add(Number(numericId))
            } else {
                while (usedIndices.has(autoCounter)) autoCounter++
                questionId = String(autoCounter)
                usedIndices.add(autoCounter)
                autoCounter++
            }
            slotCount++
            orderCounter++
            questionIds.push(questionId)
            idOrderMap.set(questionId, orderCounter)
            return `<div class="question-slot" data-question-id="${questionId}"></div>`
        })
        .replace(STEM_TOKEN, (_, n, m2) => {
            const on = idOrderMap.get(String(n))
            const om = m2 ? idOrderMap.get(String(m2)) : undefined
            const start = on ?? Number(n)
            const end = m2 ? (om ?? Number(m2)) : null
            return `<span class="stem-label">第 ${start}${end !== null ? '-' + end : ''} 题</span>`
        })

    // 题干映射与错题本 / 错题训练共用同一套解析规则（含旧课件的紧邻题目回退）
    questionTextMap.value = buildQuestionTextMap(markdown)

    return { markdown: processed, questionCount: slotCount, questionIdList: questionIds }
}

// ========== 双栏布局解析 ==========
function parseMarkdownWithSidebar(markdown) {
    const lines = markdown.split('\n')
    const sections = []
    let currentSection = null
    let i = 0

    while (i < lines.length) {
        const trimmed = lines[i].trim()
        if (trimmed.startsWith('# ')) {
            if (currentSection) sections.push(currentSection)
            currentSection = {
                h1: trimmed,
                mainContent: [],
                sidebarContent: [],
                isCollectingMain: true,
                hasSeenFirstSep: false,
                hasSeenSecondSep: false
            }
            i++
            continue
        }

        if (!currentSection) { i++; continue }

        if (trimmed === '---') {
            if (!currentSection.hasSeenFirstSep) {
                currentSection.hasSeenFirstSep = true
                currentSection.isCollectingMain = false
                i++
                continue
            } else if (!currentSection.hasSeenSecondSep) {
                currentSection.hasSeenSecondSep = true
                currentSection.isCollectingMain = true
                i++
                continue
            }
        }

        if (currentSection.isCollectingMain) {
            currentSection.mainContent.push(lines[i])
        } else {
            currentSection.sidebarContent.push(lines[i])
        }
        i++
    }

    if (currentSection) sections.push(currentSection)
    return sections
}

// 以每个h1 section为单位的双栏渲染（保持左右列一一对应）
function renderMarkdownWithSidebar(markdown) {
    const sections = parseMarkdownWithSidebar(markdown)
    if (sections.length === 0) return renderMarkdown(markdown)

    let html = ''
    sections.forEach(section => {
        const mainMd = section.mainContent.join('\n').trim()
        const sidebarMd = section.sidebarContent.join('\n').trim()
        const hasSidebar = sidebarMd && sidebarMd.length > 0

        if (!hasSidebar) {
            html += renderMarkdown(section.h1 + '\n' + mainMd)
        } else {
            // h1 放在双栏容器外部，确保在"仅答题区"模式下仍可见
            const h1Html = renderMarkdown(section.h1)
            const mainHtml = renderMarkdown(mainMd)
            const sidebarHtml = renderMarkdown(sidebarMd)
            html += `
        ${h1Html}
        <div class="detail-section-two-column">
          <div class="detail-main-column">${mainHtml}</div>
          <div class="detail-sidebar-column">${sidebarHtml}</div>
        </div>
      `
        }
    })

    return html
}

// ========== 数据库操作 ==========
async function loadQuestionAnswerKeys(blogId) {
    if (!blogId) return new Map()
    try {
        if (currentMode.value === 'answer') {
            const keys = await learningGateway.getAnswerKeys(blogId)
            return new Map(keys.map(item => [String(item.questionId), {
                blog_id: blogId, question_id: String(item.questionId),
                answer_text: item.answerText, auto_grade: item.autoGrade, updated_at: item.updatedAt
            }]))
        }
        loadedStudyState = currentMode.value === 'review'
            ? await learningGateway.getAdministratorState(blogId, studentId.value)
            : await learningGateway.getLearnerState(blogId)
        return new Map((loadedStudyState.questions || []).map(item => [String(item.questionId), {
            blog_id: blogId, question_id: String(item.questionId),
            answer_text: item.answerText || '', auto_grade: Boolean(item.autoGrade)
        }]))
    } catch (error) {
        console.error('加载答案设置失败:', error)
        return new Map()
    }
}

async function loadQuestionSubmissions(blogId, studentId) {
    if (!blogId || !studentId) return new Map()

    try {
        if (!loadedStudyState) {
            loadedStudyState = currentMode.value === 'review'
                ? await learningGateway.getAdministratorState(blogId, studentId)
                : await learningGateway.getLearnerState(blogId)
        }
        const data = (loadedStudyState.questions || []).map(item => item.submission).filter(Boolean)
        const map = new Map()
        data.forEach(item => map.set(String(item.questionId), {
            id: item.id, blog_id: item.articleId, student_id: item.learnerId,
            question_id: String(item.questionId), answer_text: item.answerText,
            review_status: item.reviewStatus, review_result: item.reviewResult,
            submitted_at: item.submittedAt, reviewed_at: item.reviewedAt
        }))
        return map
    } catch (error) {
        console.error('加载学生提交失败:', error)
        return new Map()
    }
}

// ========== 题目卡片渲染 ==========
function buildStatusPill(submission) {
    if (!submission) return { text: '未提交', cls: 'is-waiting' }
    if (submission.review_status !== 'reviewed') return { text: '待批阅', cls: 'is-pending' }
    const map = { correct: '正确', partial: '半对', wrong: '错误' }
    return { text: map[submission.review_result] || '已批阅', cls: `is-${submission.review_result || 'reviewed'}` }
}

function createPill(text, className = '') {
    const span = document.createElement('span')
    span.className = `question-pill${className ? ` ${className}` : ''}`
    span.textContent = text
    return span
}

function renderStudySlot(questionId, index) {
    const wrapper = document.createElement('div')
    wrapper.className = 'question-card question-card-study'
    wrapper.dataset.questionId = questionId

    const textarea = document.createElement('textarea')
    textarea.className = 'question-textarea question-textarea-study'
    textarea.rows = 2
    textarea.placeholder = '在这里填写答案'

    const submission = submissionMap.value.get(questionId)
    const answerKey = answerKeyMap.value.get(questionId)
    const isReviewed = submission?.review_status === 'reviewed'
    const originalAnswer = submission?.answer_text || ''

    if (submission) {
        textarea.value = originalAnswer
        if (isReviewed) {
            textarea.readOnly = true
            textarea.classList.add('is-locked')
        }
    }

    // 题号角标（左上角，显示顺序号，与实际 id 无关）
    const numberBadge = createPill(`第 ${index} 题`, 'is-number')
    wrapper.appendChild(numberBadge)

    // 状态角标（左上角绝对定位）
    const { text, cls } = buildStatusPill(submission)
    const badge = createPill(text, cls)
    wrapper.appendChild(badge)
    statusNodes.value.set(questionId, badge)

    wrapper.appendChild(textarea)

    // 卡片操作栏：加入错题本（总是显示）+ 查看答案（仅已批阅）
    const footer = document.createElement('div')
    footer.className = 'question-card-footer'

    // 加入错题本按钮
    const wrongBtn = document.createElement('button')
    wrongBtn.type = 'button'
    wrongBtn.className = 'question-action-btn'
    wrongBtn.innerHTML = '<i class="fas fa-book-medical"></i><span>加入错题本</span>'
    wrongBtn.addEventListener('click', function (e) {
        e.preventDefault()
        handleAddToWrongBook(questionId, this)
    })
    footer.appendChild(wrongBtn)

    if (isReviewed) {
        const actionBtn = document.createElement('button')
        actionBtn.type = 'button'
        actionBtn.className = 'question-action-btn'
        let showingAnswer = false
        actionBtn.innerHTML = '<i class="fas fa-eye"></i><span>查看答案</span>'
        actionBtn.addEventListener('click', function (e) {
            e.preventDefault()
            if (!showingAnswer) {
                textarea.value = answerKey?.answer_text || '（未设置标准答案）'
                textarea.classList.add('is-showing-answer')
                showingAnswer = true
                this.innerHTML = '<i class="fas fa-undo"></i><span>查看作答</span>'
            } else {
                textarea.value = originalAnswer
                textarea.classList.remove('is-showing-answer')
                showingAnswer = false
                this.innerHTML = '<i class="fas fa-eye"></i><span>查看答案</span>'
            }
        })
        footer.appendChild(actionBtn)
    }
    wrapper.appendChild(footer)

    slotNodes.value.set(questionId, { wrapper, textarea, status: badge, mode: 'study' })

    return wrapper
}

function renderReviewSlot(questionId, index) {
    const wrapper = document.createElement('div')
    wrapper.className = 'question-card question-card-review'
    wrapper.dataset.questionId = questionId

    const numberBadge = createPill(`第 ${index} 题`, 'is-number')
    wrapper.appendChild(numberBadge)

    const submission = submissionMap.value.get(questionId)
    const answerKey = answerKeyMap.value.get(questionId)

    const header = document.createElement('div')
    header.className = 'question-card-header'

    const answerBox = document.createElement('textarea')
    answerBox.className = 'question-textarea question-textarea-review'
    answerBox.rows = 3
    answerBox.readOnly = true
    answerBox.value = submission?.answer_text ?? '学生尚未提交'

    const refWrapper = document.createElement('div')
    refWrapper.className = 'question-reference-wrapper'
    const refLabel = document.createElement('span')
    refLabel.className = 'question-reference-label'
    refLabel.textContent = '📖 参考答案：'
    const refText = document.createElement('div')
    refText.className = 'question-reference-text'
    refText.textContent = answerKey?.answer_text || '（未设置参考答案）'
    refWrapper.append(refLabel, refText)

    const toolbar = document.createElement('div')
    toolbar.className = 'question-review-toolbar'

    const { text, cls } = buildStatusPill(submission)
    const status = createPill(text, cls)
    statusNodes.value.set(questionId, status)

    const correctBtn = document.createElement('button')
    correctBtn.type = 'button'
    correctBtn.className = 'question-icon-btn is-correct'
    correctBtn.innerHTML = '<i class="fas fa-check-circle"></i>'
    correctBtn.title = '正确'
    correctBtn.addEventListener('click', () => persistReviewResult(questionId, 'correct'))

    const partialBtn = document.createElement('button')
    partialBtn.type = 'button'
    partialBtn.className = 'question-icon-btn is-partial'
    partialBtn.innerHTML = '<i class="fas fa-adjust"></i>'
    partialBtn.title = '半对'
    partialBtn.addEventListener('click', () => persistReviewResult(questionId, 'partial'))

    const wrongBtn = document.createElement('button')
    wrongBtn.type = 'button'
    wrongBtn.className = 'question-icon-btn is-wrong'
    wrongBtn.innerHTML = '<i class="fas fa-times-circle"></i>'
    wrongBtn.title = '错误'
    wrongBtn.addEventListener('click', () => persistReviewResult(questionId, 'wrong'))

    toolbar.append(status, correctBtn, partialBtn, wrongBtn)
    wrapper.append(header, answerBox, refWrapper, toolbar)
    slotNodes.value.set(questionId, { wrapper, answerBox, status, mode: 'review', correctBtn, partialBtn, wrongBtn })

    return wrapper
}

function renderAnswerSlot(questionId, index) {
    const wrapper = document.createElement('div')
    wrapper.className = 'question-card question-card-answer'
    wrapper.dataset.questionId = questionId

    const numberBadge = createPill(`第 ${index} 题`, 'is-number')
    wrapper.appendChild(numberBadge)

    const key = answerKeyMap.value.get(questionId) || { answer_text: '', auto_grade: false }

    const header = document.createElement('div')
    header.className = 'question-card-header'

    const textarea = document.createElement('textarea')
    textarea.className = 'question-textarea question-textarea-answer'
    textarea.rows = 3
    textarea.placeholder = '设置标准答案'
    textarea.value = key.answer_text ?? ''

    textarea.addEventListener('input', () => {
        const existing = answerKeyMap.value.get(questionId) || { blog_id: blogId.value, question_id: questionId, answer_text: '', auto_grade: false }
        answerKeyMap.value.set(questionId, { ...existing, answer_text: textarea.value })
    })

    const footer = document.createElement('div')
    footer.className = 'question-card-footer question-card-footer-between'

    const status = createPill(key.auto_grade ? '自动批阅已开启' : '自动批阅关闭', key.auto_grade ? 'is-auto' : 'is-muted')
    statusNodes.value.set(questionId, status)

    const autoWrap = document.createElement('label')
    autoWrap.className = 'question-auto-grade'
    const checkbox = document.createElement('input')
    checkbox.type = 'checkbox'
    checkbox.checked = key.auto_grade || false
    checkbox.addEventListener('change', () => {
        const existing = answerKeyMap.value.get(questionId) || { blog_id: blogId.value, question_id: questionId, answer_text: '', auto_grade: false }
        answerKeyMap.value.set(questionId, { ...existing, auto_grade: checkbox.checked })
        status.textContent = checkbox.checked ? '自动批阅已开启' : '自动批阅关闭'
        status.className = `question-pill ${checkbox.checked ? 'is-auto' : 'is-muted'}`
    })
    autoWrap.appendChild(checkbox)
    autoWrap.appendChild(document.createTextNode('启用自动批阅'))

    footer.append(status, autoWrap)
    wrapper.append(header, textarea, footer)
    slotNodes.value.set(questionId, { wrapper, textarea, status, autoWrap, mode: 'answer' })

    return wrapper
}

// ========== 答题卡 ==========
function buildH1QuestionMap(markdownWithSlots) {
    const sections = []
    const lines = markdownWithSlots.split('\n')
    let currentH1 = null
    let currentQuestions = []
    let globalIndex = 0 // 全文章顺序号（与题干标签/答题角标一致，跨章节递增）

    for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('# ')) {
            if (currentH1 && currentQuestions.length > 0) {
                sections.push({ h1Title: currentH1, questions: [...currentQuestions] })
            }
            currentH1 = trimmed.replace(/^#\s+/, '')
            currentQuestions = []
        } else if (currentH1) {
            const idMatch = line.match(/data-question-id="([^"]+)"/)
            if (idMatch) {
                globalIndex++
                currentQuestions.push({
                    questionId: idMatch[1],
                    localIndex: globalIndex
                })
            }
        }
    }
    if (currentH1 && currentQuestions.length > 0) {
        sections.push({ h1Title: currentH1, questions: [...currentQuestions] })
    }

    return sections
}

function toggleAnswerSheet() {
    if (answerSheetActive.value) {
        answerSheetActive.value = false
        setTimeout(() => { answerSheetVisible.value = false }, 300)
    } else {
        if (tocActive.value) {
            tocActive.value = false
            setTimeout(() => { tocVisible.value = false }, 300)
        }
        answerSheetVisible.value = true
        nextTick(() => {
            requestAnimationFrame(() => {
                answerSheetActive.value = true
            })
        })
    }
}

function closeAnswerSheet() {
    answerSheetActive.value = false
    setTimeout(() => { answerSheetVisible.value = false }, 300)
}

// ========== 目录 ==========
function setupHeadingIds(container) {
    const headings = container.querySelectorAll('h1, h2')
    const tree = []
    let currentH1 = null
    let idCounter = 0

    headings.forEach(el => {
        el.id = `toc-heading-${idCounter++}`
        const text = el.textContent.trim()
        const level = el.tagName === 'H1' ? 1 : 2

        if (level === 1) {
            currentH1 = { id: el.id, text, level, children: [] }
            tree.push(currentH1)
        } else if (currentH1 && level === 2) {
            currentH1.children.push({ id: el.id, text, level })
        }
    })

    // 同步更新 h1QuestionSections 中的 headingId，供答题卡跳转使用
    if (tree.length > 0) {
        h1QuestionSections.value = h1QuestionSections.value.map((section, idx) => ({
            ...section,
            headingId: tree[idx]?.id || ''
        }))
    }

    return tree
}

function toggleToc() {
    if (tocActive.value) {
        tocActive.value = false
        setTimeout(() => { tocVisible.value = false }, 300)
    } else {
        if (answerSheetActive.value) {
            answerSheetActive.value = false
            setTimeout(() => { answerSheetVisible.value = false }, 300)
        }
        tocVisible.value = true
        nextTick(() => {
            requestAnimationFrame(() => {
                tocActive.value = true
            })
        })
    }
}

function closeToc() {
    tocActive.value = false
    setTimeout(() => { tocVisible.value = false }, 300)
}

function scrollToHeading(headingId) {
    if (!headingId) return
    const el = document.getElementById(headingId)
    if (el) {
        // 偏移吸顶头部 + 阅读工具条，使标题不被遮挡
        const NAV_BAR_HEIGHT = getStickyChromeOffset()
        const rect = el.getBoundingClientRect()
        window.scrollBy({
            top: rect.top - NAV_BAR_HEIGHT,
            behavior: 'smooth'
        })
    }
    closeToc()
    closeAnswerSheet()
}

function toggleTocH1(idx) {
    expandedTocH1.value = expandedTocH1.value === idx ? null : idx
}

/** 吸顶头部 + 阅读工具条的实际高度，用于目录/答题卡跳转时留出可见空间 */
function getStickyChromeOffset() {
    const header = document.querySelector('.site-header')
    const tools = document.querySelector('.reader-tools')
    const headerHeight = header?.offsetHeight || 76
    const toolsHeight = tools?.offsetHeight || 58
    return headerHeight + toolsHeight + 12
}

function findCenterHeading() {
    const headings = detailBodyRef.value?.querySelectorAll('h1')
    if (!headings?.length) return null

    const viewCenter = window.scrollY + window.innerHeight / 2
    let closest = headings[0]
    let minDist = Infinity

    headings.forEach(h => {
        const rect = h.getBoundingClientRect()
        const hCenter = rect.top + rect.height / 2 + window.scrollY
        const dist = Math.abs(hCenter - viewCenter)
        if (dist < minDist) {
            minDist = dist
            closest = h
        }
    })

    return closest.id || null
}

// ========== 加入错题本 ==========
/**
 * 答题卡片上的「加入错题本」按钮：把当前题主动加入错题本
 * 同一来源已有记录时提示，不重复添加
 */
async function handleAddToWrongBook(questionId, btn) {
    if (!blogId.value || !studentId.value) {
        showToast('当前没有可用的学生身份，无法添加', 'error')
        return
    }

    const key = String(questionId)
    const node = slotNodes.value.get(key)
    const submission = submissionMap.value.get(key)
    const answer = node?.textarea?.value || submission?.answer_text || ''
    try {
        await wrongQuestionsStore.collectManual({
            sourceArticleId: blogId.value,
            sourceQuestionId: key,
            myAnswer: answer
        })
        showToast('已加入错题本', 'success')
        if (btn) {
            btn.disabled = true
            btn.innerHTML = '<i class="fas fa-check"></i><span>已加入</span>'
        }
    } catch (error) {
        if (error.code === 'wrong_book_entry_exists') {
            showToast('该题已在错题本中', 'info')
            return
        }
        console.error('加入错题本失败:', error)
        showToast(`添加失败：${error.message || '请稍后重试'}`, 'error', 5000)
    }
}

// ========== 数据持久化 ==========
async function persistStudyAnswers({ silent = false, targetQuestionId = null } = {}) {
    if (isSubmitting.value) return false
    isSubmitting.value = true

    try {
        if (!blogId.value || !studentId.value) {
            if (!silent) setFabStatus(false, '当前文章没有可用的学生身份，无法提交')
            return false
        }

        const rows = []
        slotNodes.value.forEach((node, questionId) => {
            if (targetQuestionId && String(targetQuestionId) !== String(questionId)) return

            const submission = submissionMap.value.get(questionId)
            if (submission?.review_status === 'reviewed') return

            const answer = node.textarea?.value || ''
            if (!submission && answer.trim() === '') return

            rows.push({
                question_id: questionId,
                answer_text: answer
            })
        })

        if (!rows.length) {
            if (!silent) setFabStatus(true, '没有需要提交的内容')
            return true
        }

        await Promise.all(rows.map(row => learningGateway.submit(
            blogId.value, row.question_id, row.answer_text)))
        loadedStudyState = null

        if (!silent) setFabStatus(true, '提交成功！')
        return true
    } finally {
        isSubmitting.value = false
    }
}

async function persistAnswerKeys({ silent = false } = {}) {
    if (isSubmitting.value) return false
    isSubmitting.value = true

    try {
        if (!blogId.value) {
            if (!silent) setFabStatus(false, '当前文章无效，无法保存')
            return false
        }

        const rows = []
        let hasAnyContent = false

        slotNodes.value.forEach((node, questionId) => {
            const answerText = node.textarea?.value || ''
            const autoGrade = Boolean(node.autoWrap?.querySelector('input[type="checkbox"]')?.checked)

            if (answerText.trim() !== '' || autoGrade) hasAnyContent = true
            rows.push({
                blog_id: blogId.value,
                question_id: questionId,
                answer_text: answerText,
                auto_grade: autoGrade
            })
        })

        if (!rows.length) {
            if (!silent) setFabStatus(true, '没有可保存的答案设置')
            return true
        }

        if (!hasAnyContent) {
            if (!silent) setFabStatus(false, '请先填写标准答案内容再保存')
            return false
        }

        await learningGateway.replaceAnswerKeys(blogId.value, rows.map(row => ({
            questionId: row.question_id,
            answerText: row.answer_text,
            autoGrade: row.auto_grade
        })))

        if (!silent) setFabStatus(true, '保存成功！')
        return true
    } finally {
        isSubmitting.value = false
    }
}

async function persistReviewResult(questionId, reviewResult) {
    if (!blogId.value || !studentId.value) {
        setFabStatus(false, '请先从管理员页面选择学生后再批阅')
        return false
    }

    const submission = submissionMap.value.get(questionId) || {}
    let reviewed
    try {
        reviewed = await learningGateway.review(blogId.value, studentId.value, questionId, reviewResult)
    } catch (error) {
        console.error('保存批阅结果失败:', error)
        setFabStatus(false, '批阅保存失败，请重试')
        return false
    }

    submissionMap.value.set(questionId, {
        ...submission,
        review_status: reviewed.reviewStatus,
        review_result: reviewed.reviewResult,
        reviewed_at: reviewed.reviewedAt
    })

    const status = statusNodes.value.get(questionId)
    if (status) {
        const { text, cls } = buildStatusPill(submissionMap.value.get(questionId))
        status.textContent = text
        status.className = `question-pill ${cls}`
    }

    setFabStatus(true, '批阅已保存')
    return true
}

// ========== FAB 状态反馈 ==========
function setFabStatus(success, message) {
    if (success) {
        fabStatusClass.value = 'is-success'
        showToast(message || '成功', 'success')
    } else {
        fabStatusClass.value = 'is-error'
        showToast(message || '失败', 'error')
    }

    setTimeout(() => {
        fabStatusClass.value = ''
    }, 2500)
}

// ========== 刷新提交状态 ==========
async function refreshSubmissionStatus() {
    const blogIdValue = blogId.value
    const studentIdValue = studentId.value

    if (!blogIdValue || !studentIdValue) return

    const newSubmissions = await loadQuestionSubmissions(blogIdValue, studentIdValue)
    submissionMap.value = newSubmissions

    slotNodes.value.forEach((node, questionId) => {
        const submission = submissionMap.value.get(questionId)
        const statusNode = statusNodes.value.get(questionId)

        if (statusNode) {
            const { text, cls } = buildStatusPill(submission)
            statusNode.textContent = text
            statusNode.className = `question-pill ${cls}`
        }

        if (currentMode.value === 'study' && node.textarea) {
            const isReviewed = submission?.review_status === 'reviewed'
            if (isReviewed) {
                node.textarea.readOnly = true
                node.textarea.classList.add('is-locked')
                if (submission?.answer_text) {
                    node.textarea.value = submission.answer_text
                }
            } else {
                node.textarea.readOnly = false
                node.textarea.classList.remove('is-locked')
            }
        }
    })
}

// ========== 初始化题目槽位（扫描所有列，包括侧栏） ==========
function initializeQuestionSlots() {
    if (!detailBodyRef.value) return

    const slotElements = Array.from(detailBodyRef.value.querySelectorAll('.question-slot'))

    slotElements.forEach((slotEl, index) => {
        const questionId = questionIdList.value[index]
        if (!questionId) return

        let node = null
        if (currentMode.value === 'study') {
            node = renderStudySlot(questionId, index + 1)
        } else if (currentMode.value === 'review') {
            node = renderReviewSlot(questionId, index + 1)
        } else if (currentMode.value === 'answer') {
            node = renderAnswerSlot(questionId, index + 1)
        }

        if (node) {
            slotEl.replaceWith(node)
        }
    })
}

// ========== 重置状态 ==========
function resetDetailState() {
    questionCount.value = 0
    questionIdList.value = []
    questionTextMap.value = new Map()
    answerKeyMap.value = new Map()
    submissionMap.value = new Map()
    slotNodes.value = new Map()
    statusNodes.value = new Map()
    loadedStudyState = null
    contentVersion.value++
    isSubmitting.value = false
    fabStatusClass.value = ''
    renderedContent.value = ''
    layoutMode.value = 'both'
    hasSidebar.value = false
    h1QuestionSections.value = []
    answerSheetVisible.value = false
    answerSheetActive.value = false
    tocData.value = []
    tocVisible.value = false
    tocActive.value = false
    expandedTocH1.value = null
}

// ========== 加载内容 ==========
let isRendering = false

async function loadContent() {
    if (isRendering) return
    isRendering = true

    try {
        resetDetailState()

        // 确定模式
        const mode = route.query.mode || 'study'
        currentMode.value = ['study', 'review', 'answer'].includes(mode) ? mode : 'study'

        if (!blogId.value) {
            renderedContent.value = '<p>文章不存在。</p>'
            blogTitle.value = '文章不存在'
            isRendering = false
            return
        }

        const blog = currentArticle.value
        if (!blog) {
            renderedContent.value = '<p>文章未找到。</p>'
            blogTitle.value = '未找到'
            isRendering = false
            return
        }

        // 检查权限（学习模式）
        if (currentMode.value === 'study') {
            if (!authStore.isLoggedIn) {
                renderedContent.value = '<p>请先登录后查看。</p>'
                blogTitle.value = '请登录'
                isRendering = false
                return
            }
        }

        // 加载 Markdown 内容
        const response = await fetch(`${import.meta.env.BASE_URL}articles/${blog.path}`)
        if (!response.ok) throw new Error('文件加载失败')

        let content = await response.text()

        // 1. 处理图片嵌入语法
        content = processMarkdown(content)

        // 2. 注入题目占位符
        const slotResult = injectQuestionSlots(content)
        questionCount.value = slotResult.questionCount
        questionIdList.value = slotResult.questionIdList

        // 3. 构建 h1 → 题目映射（用于答题卡）
        h1QuestionSections.value = buildH1QuestionMap(slotResult.markdown)

        // 4. 检测是否有侧栏内容，并渲染 Markdown
        const sections = parseMarkdownWithSidebar(slotResult.markdown)
        hasSidebar.value = sections.some(s => s.sidebarContent.join('').trim().length > 0)
        renderedContent.value = renderMarkdownWithSidebar(slotResult.markdown)
        blogTitle.value = blog.title
        document.title = `${blog.title}${currentMode.value === 'study' ? '' : ` · ${currentMode.value === 'review' ? '批阅' : '答案设置'}`}`

        // 4. 等待 DOM 更新
        await nextTick()

        // 5. 渲染数学公式
        if (detailBodyRef.value) {
            await renderMath(detailBodyRef.value)
        }

        // 6. 加载答案和提交数据
        answerKeyMap.value = await loadQuestionAnswerKeys(blogId.value)

        if (currentMode.value === 'study' || currentMode.value === 'review') {
            if (studentId.value) {
                submissionMap.value = await loadQuestionSubmissions(blogId.value, studentId.value)
            }
        }

        // 7. 初始化题目槽位（扫描主栏和侧栏中的全部题目）
        await nextTick()
        initializeQuestionSlots()

        // 8. 为 h1/h2 注入 ID 并构建目录数据
        if (detailBodyRef.value) {
            tocData.value = setupHeadingIds(detailBodyRef.value)
        }

        // 9. 观察图片嵌入
        if (detailBodyRef.value) {
            observe(detailBodyRef.value)
        }

    } catch (e) {
        console.error('加载内容失败:', e)
        renderedContent.value = '<p>无法读取文章内容，请稍后重试。</p>'
    } finally {
        isRendering = false
        nextTick(updateReadingProgress)
    }
}

// ========== 布局切换 ==========
function cycleLayoutMode() {
    // 记录切换前屏幕中央所在的 h1
    const centerH1Id = findCenterHeading()

    const states = ['both', 'left', 'right']
    const idx = states.indexOf(layoutMode.value)
    layoutMode.value = states[(idx + 1) % states.length]

    // 切换后自动跳转到该 h1 顶部（偏移导航栏高度）
    if (centerH1Id) {
        nextTick(() => {
            const el = document.getElementById(centerH1Id)
            if (el) {
                const NAV_BAR_HEIGHT = getStickyChromeOffset()
                const rect = el.getBoundingClientRect()
                window.scrollBy({
                    top: rect.top - NAV_BAR_HEIGHT,
                    behavior: 'smooth'
                })
            }
        })
    }
}

// ========== FAB 按钮处理 ==========
async function handleFabClick() {
    if (isSubmitting.value) return

    let ok
    if (currentMode.value === 'study') {
        ok = await persistStudyAnswers({ silent: false })
    } else if (currentMode.value === 'answer') {
        ok = await persistAnswerKeys({ silent: false })
    }

    if (ok) {
        await refreshSubmissionStatus()
    }
}

// ========== 断点监听 ==========
const breakpointMql = window.matchMedia('(min-width: 1024px)')
let lastIsDesktop = breakpointMql.matches

function handleBreakpointChange(e) {
    const nowDesktop = e.matches
    if (nowDesktop !== lastIsDesktop) {
        lastIsDesktop = nowDesktop
        loadContent()
    }
}

// ========== 页面关闭前自动保存 ==========
function handlePageHide() {
    if (currentMode.value === 'study') {
        persistStudyAnswers({ silent: true })
    } else if (currentMode.value === 'answer') {
        persistAnswerKeys({ silent: true })
    }
}

// ========== 路由监听 ==========
watch(
    () => [route.params.id, route.query.mode, route.query.studentId],
    () => {
        loadContent()
    }
)

// 服务端会话异步恢复完成后重新加载内容
watch(() => authStore.isLoggedIn, (loggedIn) => {
    if (loggedIn) loadContent()
})

// ========== 生命周期 ==========
onMounted(async () => {
    await blogStore.loadArticleData()
    await loadContent()
    breakpointMql.addEventListener('change', handleBreakpointChange)
    window.addEventListener('pagehide', handlePageHide)
    window.addEventListener('scroll', handleReaderScroll, { passive: true })
    window.addEventListener('keydown', handleReaderKeydown)
    updateReadingProgress()
})

onUnmounted(() => {
    disconnect()
    breakpointMql.removeEventListener('change', handleBreakpointChange)
    window.removeEventListener('pagehide', handlePageHide)
    window.removeEventListener('scroll', handleReaderScroll)
    window.removeEventListener('keydown', handleReaderKeydown)
    if (progressFrame) cancelAnimationFrame(progressFrame)
})
</script>

<style scoped>
/* ==========================================================================
   NEST · 学巢 — 阅读器（Riso Campus 印刷学院风）
   奶油纸张 + 墨黑文字 + 印刷纯色块 + 硬边框 / 错位硬阴影。
   仅使用 theme.css token；不使用毛玻璃、发光与深色页面底。
   ========================================================================== */

.reader {
    --reader-fs: 1.08rem;
    --reader-lh: 1.85;
    --reader-sticky-top: calc(var(--header-h) - 4px);
    position: relative;
    width: 100%;
    max-width: var(--page-max);
    margin: 0 auto;
    padding: 30px 24px calc(88px + env(safe-area-inset-bottom) + 32px);
    color: var(--text-2);
}

.reader--fs-sm { --reader-fs: 0.98rem; }
.reader--fs-md { --reader-fs: 1.08rem; }
.reader--fs-lg { --reader-fs: 1.2rem; }

/* --- 顶部阅读进度（纸面刻度线） ----------------------------------------- */
.reader-progress {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    z-index: 320;
    height: 5px;
    background: var(--paper-2);
    border-bottom: 1px solid var(--ink);
    pointer-events: none;
}

.reader-progress__bar {
    display: block;
    height: 100%;
    border-right: 1px solid var(--ink);
    background: var(--vermilion);
    transition: width var(--dur-fast) linear;
}

.reader-shell {
    position: relative;
}

/* --- 文章头部（纸卡 + 网点装饰） --------------------------------------- */
.reader-hero {
    position: relative;
    overflow: hidden;
    margin-bottom: 20px;
    padding: 26px clamp(20px, 3.6vw, 42px) 24px;
    border-radius: var(--r-lg);
}

.reader-hero__halftone {
    position: absolute;
    right: -26px;
    top: -30px;
    width: 190px;
    height: 150px;
    background-image: radial-gradient(var(--cobalt) 2px, transparent 2.4px);
    background-size: 13px 13px;
    opacity: 0.14;
    pointer-events: none;
}

.reader-hero__top {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    flex-wrap: wrap;
}

.reader-mode {
    font-family: var(--font-mono);
    letter-spacing: 0.06em;
}

.reader-title {
    position: relative;
    margin: 12px 0 16px;
    font-family: var(--font-display);
    font-size: clamp(1.6rem, 3.2vw, 2.5rem);
    font-weight: 800;
    line-height: 1.14;
    letter-spacing: -0.025em;
    color: var(--ink);
    text-wrap: balance;
}

.reader-meta {
    position: relative;
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

/* 全页唯一的 ink 反白区块：阅读进度条 */
.reader-meter {
    position: relative;
    display: flex;
    align-items: center;
    gap: 14px;
    margin-top: 20px;
    padding: 9px 14px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--ink);
    box-shadow: 4px 4px 0 var(--vermilion);
}

.reader-meter .progress {
    flex: 1;
    height: 9px;
    border-color: var(--paper-1);
    background: var(--paper-2);
}

.reader-meter .progress__bar {
    background: var(--lime);
}

.reader-meter__label {
    flex-shrink: 0;
    color: var(--paper-1);
    font-size: 0.7rem;
    font-weight: 700;
    letter-spacing: 0.1em;
}

/* --- 阅读工具条（纸白胶囊 + 墨线硬阴影） -------------------------------- */
.reader-tools {
    position: sticky;
    top: var(--reader-sticky-top);
    z-index: 90;
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    margin-bottom: 20px;
    padding: 7px 9px;
    border-radius: var(--r-full);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 4px 4px 0 var(--ink);
}

.reader-fontsize {
    flex-shrink: 0;
    border-color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

.reader-fontsize__btn {
    min-width: 38px;
    height: 36px;
    justify-content: center;
    font-family: var(--font-mono);
}

.reader-tools__divider {
    width: 2px;
    height: 24px;
    margin: 0 5px;
    background: var(--ink);
    opacity: 0.32;
}

.reader-tool {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 7px;
    height: 40px;
    padding: 0 14px;
    border-radius: var(--r-full);
    border: 1.5px solid transparent;
    background: transparent;
    color: var(--text-2);
    font-size: 0.78rem;
    font-weight: 700;
    letter-spacing: 0.01em;
    white-space: nowrap;
    transition: color var(--dur-fast), background var(--dur-fast), border-color var(--dur-fast),
        transform var(--dur-fast) var(--ease-out);
}

.reader-tool i {
    font-size: 0.86rem;
}

.reader-tool:hover {
    color: var(--ink);
    background: var(--paper-2);
    border-color: var(--ink);
}

.reader-tool:active {
    transform: translate(1px, 1px);
}

.reader-tool.is-active {
    color: var(--paper-1);
    background: var(--ink);
    border-color: var(--ink);
}

.reader-tool--end {
    margin-left: auto;
}

.reader-tools__hint {
    margin-left: 4px;
    color: var(--text-3);
    font-size: 0.68rem;
    letter-spacing: 0.06em;
    white-space: nowrap;
}

/* --- 正文纸张（纸白卡片 + 墨线硬阴影） ---------------------------------- */
.reader-sheet {
    position: relative;
    padding: clamp(18px, 3.4vw, 50px);
    border-radius: var(--r-lg);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: var(--shadow-card);
    transition: max-width var(--dur) var(--ease-out), padding var(--dur) var(--ease-out);
}

.reader--focus .reader-sheet {
    max-width: 920px;
    margin: 0 auto;
}

.reader--focus .reader-hero__halftone,
.reader--focus .fab-right-group {
    display: none;
}

.reader--focus .reader-tools {
    max-width: 920px;
    margin-left: auto;
    margin-right: auto;
}

/* 专注模式：收起答题侧栏与装饰，只留正文（仅“答题区”布局时保留） */
.reader--focus .detail-layout-wrapper:not(.layout-right) :deep(.detail-sidebar-column) {
    display: none;
}

.reader--focus .detail-layout-wrapper:not(.layout-right) :deep(.detail-main-column) {
    flex: 1 1 100%;
    max-width: 100%;
}

.reader-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 64px 0;
    color: var(--text-3);
    font-size: 0.86rem;
}

.detail-body {
    position: relative;
    color: var(--text-2);
    font-size: var(--reader-fs);
    line-height: var(--reader-lh);
    overflow-wrap: break-word;
    word-break: break-word;
}

.detail-body:empty {
    display: none;
}

/* 双栏：正文 + 答题区 */
.detail-body :deep(.detail-section-two-column) {
    display: flex;
    align-items: flex-start;
    gap: 34px;
    margin: 0 0 34px;
    padding-bottom: 26px;
    border-bottom: 1.5px dashed var(--line-strong);
}

.detail-body :deep(.detail-section-two-column:last-child) {
    margin-bottom: 0;
    padding-bottom: 0;
    border-bottom: 0;
}

.detail-body :deep(.detail-main-column) {
    flex: 1 1 0;
    min-width: 0;
}

.detail-body :deep(.detail-sidebar-column) {
    flex: 0 1 36%;
    min-width: 0;
    position: sticky;
    top: calc(var(--header-h) + 82px);
    max-height: calc(100vh - var(--header-h) - 108px);
    overflow-y: auto;
    padding: 16px 18px;
    border-radius: var(--r-md);
    border: 1.5px solid var(--ink);
    background: var(--paper-0);
    box-shadow: 3px 3px 0 var(--ink);
}

.detail-layout-wrapper.layout-left :deep(.detail-sidebar-column) {
    display: none;
}

.detail-layout-wrapper.layout-left :deep(.detail-main-column) {
    flex: 1 1 100%;
    max-width: 100%;
}

.detail-layout-wrapper.layout-right :deep(.detail-main-column) {
    display: none;
}

.detail-layout-wrapper.layout-right :deep(.detail-sidebar-column) {
    flex: 1 1 100%;
    max-width: 100%;
    position: static;
    max-height: none;
    overflow: visible;
}

/* 没有答题侧栏的文章：限制行宽，长文更好读 */
.detail-layout-wrapper:not(.has-sidebar-content) {
    max-width: 880px;
    margin-inline: auto;
}

/* --- Markdown 排版 ------------------------------------------------------ */
.detail-body :deep(h1),
.detail-body :deep(h2),
.detail-body :deep(h3),
.detail-body :deep(h4),
.detail-body :deep(h5),
.detail-body :deep(h6) {
    color: var(--ink);
    font-family: var(--font-display);
    font-weight: 800;
    scroll-margin-top: calc(var(--header-h) + 74px);
}

.detail-body :deep(h1) {
    position: relative;
    margin: 1.7em 0 0.8em;
    padding-left: 22px;
    font-size: 1.68em;
    line-height: 1.22;
    letter-spacing: -0.025em;
}

.detail-body :deep(h1:first-child) {
    margin-top: 0;
}

.detail-body :deep(h1)::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0.16em;
    bottom: 0.16em;
    width: 8px;
    border-radius: 3px;
    border: 1.5px solid var(--ink);
    background: var(--vermilion);
}

.detail-body :deep(h2) {
    position: relative;
    margin: 1.5em 0 0.7em;
    padding-left: 18px;
    font-size: 1.32em;
    line-height: 1.28;
}

.detail-body :deep(h2)::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0.18em;
    bottom: 0.18em;
    width: 7px;
    border-radius: 3px;
    border: 1.5px solid var(--ink);
    background: var(--cobalt);
}

.detail-body :deep(h3) {
    margin: 1.4em 0 0.6em;
    padding-left: 10px;
    border-left: 4px solid var(--butter);
    font-size: 1.15em;
}

.detail-body :deep(h4),
.detail-body :deep(h5),
.detail-body :deep(h6) {
    margin: 1.2em 0 0.5em;
    font-size: 0.94em;
    color: var(--cobalt);
    font-family: var(--font-mono);
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
}

.detail-body :deep(p) {
    margin: 0.9em 0;
    color: var(--text-2);
}

.detail-body :deep(a) {
    color: var(--cobalt);
    font-weight: 600;
    text-decoration: underline;
    text-decoration-thickness: 1.5px;
    text-underline-offset: 3px;
}

.detail-body :deep(a:hover) {
    color: var(--ink);
    background: var(--lime);
}

.detail-body :deep(ul),
.detail-body :deep(ol) {
    margin: 0.9em 0;
    padding-left: 1.55em;
}

.detail-body :deep(ul) { list-style: disc; }
.detail-body :deep(ol) { list-style: decimal; }

.detail-body :deep(li) {
    margin: 0.34em 0;
    color: var(--text-2);
}

.detail-body :deep(li::marker) {
    color: var(--vermilion);
    font-weight: 700;
}

.detail-body :deep(li > ul),
.detail-body :deep(li > ol) {
    margin: 0.3em 0;
}

/* 黄绿荧光笔划线 */
.detail-body :deep(strong),
.detail-body :deep(b) {
    color: var(--ink);
    font-weight: 800;
    background: linear-gradient(transparent 58%, var(--lime) 58%);
    padding: 0 0.08em;
    -webkit-box-decoration-break: clone;
    box-decoration-break: clone;
}

.detail-body :deep(em) {
    color: var(--ink-soft);
    font-style: italic;
}

.detail-body :deep(blockquote) {
    margin: 1.3em 0;
    padding: 0.9em 1.1em;
    border: 1.5px solid var(--ink);
    border-left-width: 8px;
    border-left-color: var(--vermilion);
    border-radius: 0 var(--r-xs) var(--r-xs) 0;
    background: var(--paper-2);
    color: var(--ink-soft);
}

.detail-body :deep(blockquote p:first-child) { margin-top: 0; }
.detail-body :deep(blockquote p:last-child) { margin-bottom: 0; }

.detail-body :deep(hr) {
    height: 2px;
    margin: 2.2em 0;
    border: 0;
    background: var(--ink);
    opacity: 0.8;
}

.detail-body :deep(code) {
    padding: 0.14em 0.42em;
    border-radius: 5px;
    border: 1px solid var(--line-strong);
    background: var(--paper-2);
    color: var(--ink);
    font-family: var(--font-mono);
    font-size: 0.88em;
}

.detail-body :deep(pre) {
    margin: 1.3em 0;
    padding: 18px 20px;
    border-radius: var(--r-sm);
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
    color: var(--ink);
    box-shadow: 3px 3px 0 var(--line-strong);
    font-size: 0.88em;
    line-height: 1.75;
    overflow-x: auto;
}

.detail-body :deep(pre code) {
    padding: 0;
    border: 0;
    background: transparent;
    color: inherit;
    font-size: 1em;
}

/* 图片：纸白画框 + 圆角内边距 */
.detail-body :deep(img) {
    display: block;
    max-width: 100%;
    height: auto;
    margin: 1.3em auto;
    padding: 10px;
    border-radius: var(--r-sm);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 4px 4px 0 var(--ink);
}

.detail-body :deep(.image-wrapper),
.detail-body :deep(.image-figure) {
    max-width: 100%;
    margin: 1.3em 0;
}

.detail-body :deep(.image-wrapper img),
.detail-body :deep(.image-figure img) {
    margin: 0 auto;
}

.detail-body :deep(.image-caption) {
    margin-top: 0.6em;
    color: var(--text-3);
    font-size: 0.8em;
    letter-spacing: 0.02em;
    text-align: center;
}

.detail-body :deep(figure) {
    margin: 1.3em 0;
}

/* 表格：印刷表格线 */
.detail-body :deep(table) {
    width: 100%;
    margin: 1.3em 0;
    border-collapse: collapse;
    border: 1.5px solid var(--ink);
    font-size: 0.92em;
    overflow: hidden;
}

.detail-body :deep(th),
.detail-body :deep(td) {
    padding: 9px 14px;
    border: 1px solid var(--line-strong);
    text-align: left;
    vertical-align: top;
}

.detail-body :deep(th) {
    color: var(--ink);
    font-family: var(--font-display);
    font-weight: 800;
    background: var(--paper-2);
    border-bottom: 1.5px solid var(--ink);
    white-space: nowrap;
}

.detail-body :deep(tr:nth-child(even) td) {
    background: var(--paper-0);
}

/* KaTeX：墨黑公式 + 纸面公式块 */
.detail-body :deep(.katex) {
    color: var(--ink);
    font-size: 1.02em;
}

.detail-body :deep(.katex-display) {
    margin: 1.2em 0;
    padding: 14px 16px;
    border-radius: var(--r-sm);
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
}

.detail-body :deep(.katex .mord),
.detail-body :deep(.katex .mbin),
.detail-body :deep(.katex .mrel),
.detail-body :deep(.katex .mopen),
.detail-body :deep(.katex .mclose),
.detail-body :deep(.katex .mpunct),
.detail-body :deep(.katex .minner) {
    color: inherit;
}

/* 题干题号（v-html 中由脚本生成） */
.detail-body :deep(.stem-label) {
    display: inline-flex;
    align-items: center;
    margin-right: 8px;
    padding: 2px 12px;
    border-radius: var(--r-full);
    border: 1.5px solid var(--ink);
    background: var(--butter);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
    font-family: var(--font-mono);
    font-size: 0.74em;
    font-weight: 700;
    letter-spacing: 0.04em;
    vertical-align: middle;
}

/* --- 题块与作答卡片 ------------------------------------------------------ */
:deep(.question-slot) {
    display: block;
    margin: 22px 0;
}

:deep(.question-group) {
    margin: 26px 0;
    padding: 26px 22px 18px;
    border-radius: var(--r-md);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--line-strong);
}

:deep(.question-card) {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin: 22px 0;
    padding: 28px 18px 14px;
    border-radius: var(--r-md);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);
}

:deep(.question-card:hover) {
    transform: translate(-1px, -1px);
    box-shadow: 4px 4px 0 var(--ink);
}

:deep(.question-card-header) {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 20px;
    color: var(--text-3);
    font-size: 0.78rem;
    font-weight: 600;
}

:deep(.question-card-header:empty) {
    display: none;
}

/* 题号 / 状态角标 */
:deep(.question-pill) {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    min-height: 22px;
    padding: 2px 10px;
    border-radius: var(--r-full);
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
    color: var(--ink);
    font-family: var(--font-mono);
    font-size: 0.68rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    line-height: 1.5;
    white-space: nowrap;
}

:deep(.question-pill.is-number) {
    position: absolute;
    top: -12px;
    left: 16px;
    z-index: 1;
    background: var(--cobalt);
    color: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

:deep(.question-card-study > .question-pill:not(.is-number)) {
    position: absolute;
    top: -12px;
    left: auto;
    right: 16px;
    z-index: 1;
}

:deep(.question-pill.is-waiting),
:deep(.question-pill.is-muted) {
    background: var(--paper-2);
    color: var(--text-2);
}

:deep(.question-pill.is-pending) {
    background: var(--butter);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

:deep(.question-pill.is-partial) {
    background: var(--sky);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

:deep(.question-pill.is-correct),
:deep(.question-pill.is-auto) {
    background: var(--lime);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

:deep(.question-pill.is-wrong) {
    background: var(--vermilion);
    color: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

:deep(.question-pill.is-reviewed) {
    background: var(--paper-2);
    color: var(--ink);
}

/* 作答输入：纸上输入框 */
:deep(.question-textarea) {
    width: 100%;
    min-height: 82px;
    padding: 12px 14px;
    border-radius: var(--r-sm);
    border: 1.5px solid var(--ink);
    background: var(--paper-0);
    color: var(--text-1);
    font-family: var(--font-body);
    font-size: 0.95rem;
    line-height: 1.7;
    resize: vertical;
    outline: none;
    transition: background var(--dur-fast), box-shadow var(--dur-fast);
}

:deep(.question-textarea::placeholder) {
    color: var(--text-4);
}

:deep(.question-textarea:hover) {
    background: var(--paper-1);
}

:deep(.question-textarea:focus) {
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
}

:deep(.question-textarea.is-locked),
:deep(.question-textarea[readonly]) {
    border-style: dashed;
    background: var(--paper-2);
    color: var(--text-2);
}

:deep(.question-textarea.is-showing-answer) {
    border-color: var(--green) !important;
    background: var(--paper-1) !important;
    color: var(--text-1);
    box-shadow: 3px 3px 0 var(--green) !important;
}

/* 卡片底部操作 */
:deep(.question-card-footer) {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    padding-top: 2px;
}

:deep(.question-card-footer-between) {
    justify-content: space-between;
}

:deep(.question-action-btn) {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    min-height: 40px;
    padding: 8px 15px;
    border-radius: var(--r-full);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--ink);
    font-size: 0.78rem;
    font-weight: 700;
    box-shadow: 2px 2px 0 var(--ink);
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out),
        background var(--dur-fast);
}

:deep(.question-action-btn:hover) {
    background: var(--paper-2);
    transform: translate(-1px, -1px);
    box-shadow: 3px 3px 0 var(--ink);
}

:deep(.question-action-btn:active) {
    transform: translate(1px, 1px);
    box-shadow: none;
}

:deep(.question-action-btn:disabled) {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;
    box-shadow: 2px 2px 0 rgba(28, 26, 20, 0.35);
}

/* 批阅工具 */
:deep(.question-review-toolbar) {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

:deep(.question-icon-btn) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 42px;
    height: 42px;
    border-radius: var(--r-full);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--ink);
    font-size: 1rem;
    box-shadow: 2px 2px 0 var(--ink);
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out),
        background var(--dur-fast);
}

:deep(.question-icon-btn:hover) {
    transform: translate(-1px, -1px);
    box-shadow: 3px 3px 0 var(--ink);
}

:deep(.question-icon-btn:active) {
    transform: translate(1px, 1px);
    box-shadow: none;
}

:deep(.question-icon-btn.is-correct) {
    background: var(--lime);
    color: var(--ink);
}

:deep(.question-icon-btn.is-partial) {
    background: var(--sky);
    color: var(--ink);
}

:deep(.question-icon-btn.is-wrong) {
    background: var(--vermilion);
    color: var(--paper-1);
}

:deep(.question-auto-grade) {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: var(--text-2);
    font-size: 0.82rem;
    font-weight: 600;
}

:deep(.question-auto-grade input[type="checkbox"]) {
    width: 18px;
    height: 18px;
    accent-color: var(--cobalt);
}

/* 参考答案 */
:deep(.question-reference-wrapper) {
    padding: 12px 14px;
    border: 1.5px solid var(--ink);
    border-left-width: 7px;
    border-left-color: var(--cobalt);
    border-radius: 0 var(--r-xs) var(--r-xs) 0;
    background: var(--paper-2);
}

:deep(.question-reference-label) {
    display: block;
    margin-bottom: 4px;
    color: var(--cobalt);
    font-family: var(--font-mono);
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.06em;
}

:deep(.question-reference-text) {
    padding: 2px 0;
    color: var(--ink);
    font-size: 0.92rem;
    white-space: pre-wrap;
    word-break: break-word;
}

/* --- 悬浮提交 / 快捷控件 ------------------------------------------------- */
.fab-container {
    position: fixed;
    right: 24px;
    bottom: calc(24px + env(safe-area-inset-bottom));
    z-index: 998;
}

.fab-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 9px;
    min-width: 150px;
    height: 54px;
    padding: 0 22px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--vermilion);
    color: var(--paper-1);
    font-family: var(--font-body);
    font-size: 1rem;
    font-weight: 800;
    letter-spacing: 0.02em;
    box-shadow: 5px 5px 0 var(--ink);
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out),
        filter var(--dur-fast);
}

.fab-btn:hover {
    transform: translate(-2px, -2px);
    box-shadow: 7px 7px 0 var(--ink);
    filter: brightness(1.04);
}

.fab-btn:active {
    transform: translate(2px, 2px);
    box-shadow: 1px 1px 0 var(--ink);
}

.fab-btn:disabled {
    opacity: 0.45;
    transform: none;
    box-shadow: 3px 3px 0 rgba(28, 26, 20, 0.4);
}

.fab-btn.is-success {
    background: var(--lime) !important;
    color: var(--ink) !important;
}

.fab-btn.is-error {
    background: var(--coral) !important;
    color: var(--ink) !important;
}

/* 桌面端右侧快捷组 */
.fab-right-group {
    position: fixed;
    right: 24px;
    bottom: calc(24px + 54px + 22px + env(safe-area-inset-bottom));
    z-index: 997;
    display: flex;
    flex-direction: row;
    gap: 10px;
    align-items: center;
}

.fab-right-group button {
    position: static;
    z-index: auto;
}

.layout-toggle-fab,
.answer-sheet-fab,
.toc-fab {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 46px;
    height: 46px;
    border-radius: var(--r-full);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--ink);
    font-size: 1rem;
    box-shadow: 3px 3px 0 var(--ink);
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out),
        background var(--dur-fast);
}

.layout-toggle-fab:hover,
.answer-sheet-fab:hover,
.toc-fab:hover {
    background: var(--paper-2);
    transform: translate(-1px, -1px);
    box-shadow: 4px 4px 0 var(--ink);
}

.layout-toggle-fab:active,
.answer-sheet-fab:active,
.toc-fab:active {
    transform: translate(1px, 1px);
    box-shadow: 1px 1px 0 var(--ink);
}

/* --- 纸卡抽屉（答题卡 / 目录） ----------------------------------------- */
.answer-sheet-overlay {
    position: fixed;
    inset: 0;
    z-index: 1000;
    background: rgba(28, 26, 20, 0.42);
    opacity: 0;
    pointer-events: none;
    transition: opacity var(--dur) var(--ease-out);
}

.answer-sheet-overlay.overlay-active {
    opacity: 1;
    pointer-events: auto;
}

.answer-sheet-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    display: flex;
    flex-direction: column;
    width: min(396px, 92vw);
    height: 100%;
    border-right: 2px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 6px 0 0 var(--ink);
    transform: translateX(-102%);
    transition: transform var(--dur) var(--ease-out);
}

.answer-sheet-sidebar.sidebar-open {
    transform: translateX(0);
}

.answer-sheet-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding: 20px 20px 16px;
    border-bottom: 1.5px solid var(--ink);
    flex-shrink: 0;
}

.answer-sheet-heading {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
}

.answer-sheet-title {
    font-family: var(--font-display);
    font-size: 1.18rem;
    font-weight: 800;
    color: var(--ink);
}

.answer-sheet-sub {
    color: var(--text-3);
    font-family: var(--font-mono);
    font-size: 0.66rem;
    letter-spacing: 0.06em;
}

.answer-sheet-close-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: var(--r-xs);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--ink);
    font-size: 0.95rem;
    box-shadow: 2px 2px 0 var(--ink);
    flex-shrink: 0;
    transition: background var(--dur-fast), transform var(--dur-fast) var(--ease-out),
        box-shadow var(--dur-fast);
}

.answer-sheet-close-btn:hover {
    background: var(--butter);
    transform: translate(-1px, -1px);
    box-shadow: 3px 3px 0 var(--ink);
}

.answer-sheet-close-btn:active {
    transform: translate(1px, 1px);
    box-shadow: none;
}

.answer-sheet-legend {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
    padding: 12px 20px;
    border-bottom: 1.5px solid var(--ink);
    color: var(--text-3);
    font-size: 0.7rem;
    font-weight: 600;
    flex-shrink: 0;
}

.legend-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.legend-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
    flex-shrink: 0;
}

.legend-dot.is-pending { background: var(--butter); }
.legend-dot.is-correct { background: var(--lime); }
.legend-dot.is-wrong { background: var(--vermilion); }

.answer-sheet-body {
    flex: 1;
    overflow-y: auto;
    padding: 18px 20px 24px;
}

.answer-sheet-foot {
    padding: 16px 20px calc(16px + env(safe-area-inset-bottom));
    border-top: 1.5px solid var(--ink);
    background: var(--paper-0);
    flex-shrink: 0;
}

.answer-sheet-section {
    margin-bottom: 22px;
}

.answer-sheet-section:last-child {
    margin-bottom: 0;
}

.answer-sheet-section-title {
    margin-bottom: 10px;
    color: var(--ink);
    font-family: var(--font-display);
    font-size: 0.88rem;
    font-weight: 800;
    line-height: 1.45;
    word-break: break-word;
}

.answer-sheet-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.answer-sheet-cell {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: var(--r-xs);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--ink);
    font-family: var(--font-mono);
    font-size: 0.8rem;
    font-weight: 700;
    box-shadow: 2px 2px 0 var(--ink);
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);
}

.answer-sheet-cell:hover {
    transform: translate(-1px, -1px);
    box-shadow: 3px 3px 0 var(--ink);
}

.answer-sheet-cell:active {
    transform: translate(1px, 1px);
    box-shadow: none;
}

.answer-sheet-cell.cell-unsubmitted {
    background: var(--paper-2);
    color: var(--text-3);
}

.answer-sheet-cell.cell-pending {
    background: var(--butter);
    color: var(--ink);
}

.answer-sheet-cell.cell-correct {
    background: var(--lime);
    color: var(--ink);
}

.answer-sheet-cell.cell-partial {
    background: var(--sky);
    color: var(--ink);
}

.answer-sheet-cell.cell-wrong {
    background: var(--vermilion);
    color: var(--paper-1);
}

.answer-sheet-empty {
    padding: 48px 0;
    color: var(--text-3);
    font-size: 0.86rem;
    text-align: center;
}

/* 目录树 */
.toc-body {
    flex: 1;
    overflow-y: auto;
    padding: 10px 0 calc(20px + env(safe-area-inset-bottom));
}

.toc-h1-item {
    border-bottom: 1.5px solid var(--ink);
}

.toc-h1-item:last-child {
    border-bottom: 0;
}

.toc-h1-title {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 20px;
    color: var(--ink);
    font-family: var(--font-display);
    font-size: 0.92rem;
    font-weight: 800;
    user-select: none;
    transition: background var(--dur-fast);
}

.toc-h1-title:hover,
.toc-h1-title.is-open {
    background: var(--paper-2);
}

.toc-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    margin-left: -8px;
    border-radius: 8px;
    color: var(--ink);
    flex-shrink: 0;
}

.toc-toggle:hover {
    background: var(--butter);
}

.toc-arrow {
    font-size: 0.66rem;
}

.toc-text {
    cursor: pointer;
    line-height: 1.45;
}

.toc-text:hover {
    color: var(--cobalt);
}

.toc-h2-list {
    padding: 4px 0 8px;
    border-top: 1px dashed var(--line-strong);
    background: var(--paper-0);
}

.toc-h2-item {
    display: block;
    width: 100%;
    padding: 8px 20px 8px 46px;
    color: var(--text-2);
    font-size: 0.82rem;
    font-weight: 600;
    text-align: left;
    line-height: 1.45;
    transition: color var(--dur-fast), background var(--dur-fast);
}

.toc-h2-item:hover {
    color: var(--ink);
    background: var(--paper-2);
}

/* --- 响应式：375 / 768 / 1440 ------------------------------------------- */
@media (max-width: 1023px) {
    .fab-right-group {
        display: none;
    }

    .detail-body :deep(.detail-section-two-column) {
        flex-direction: column;
        gap: 18px;
        padding-bottom: 22px;
        margin-bottom: 26px;
    }

    .detail-body :deep(.detail-sidebar-column) {
        position: static;
        flex: 1 1 auto;
        width: 100%;
        max-width: 100%;
        max-height: none;
        overflow: visible;
    }

    .detail-layout-wrapper.layout-right :deep(.detail-sidebar-column) {
        max-width: 100%;
    }
}

@media (max-width: 900px) {
    .reader {
        padding: 22px 18px calc(88px + env(safe-area-inset-bottom) + 28px);
    }

    .reader-hero {
        padding: 22px 20px 20px;
    }

    .reader-tools {
        gap: 4px;
        border-radius: var(--r-lg);
        box-shadow: 3px 3px 0 var(--ink);
    }

    .reader-tools__hint,
    .reader-tools__divider {
        display: none;
    }
}

@media (max-width: 768px) {
    .reader-tools {
        top: calc(var(--header-h) - 12px);
    }

    .detail-body :deep(h1),
    .detail-body :deep(h2),
    .detail-body :deep(h3),
    .detail-body :deep(h4) {
        scroll-margin-top: calc(var(--header-h) + 96px);
    }
}

@media (max-width: 760px) {
    .reader {
        padding: 18px 14px calc(88px + env(safe-area-inset-bottom) + 56px);
    }

    .reader-hero {
        padding: 20px 16px 18px;
    }

    .reader-hero__halftone {
        display: none;
    }

    .reader-title {
        margin: 10px 0 12px;
        font-size: 1.55rem;
    }

    .reader-meter {
        margin-top: 14px;
        padding: 8px 12px;
        box-shadow: 3px 3px 0 var(--vermilion);
    }

    .reader-tools {
        top: calc(var(--header-h) - 12px);
        flex-wrap: wrap;
        padding: 6px;
        box-shadow: 3px 3px 0 var(--ink);
    }

    .detail-body :deep(h1),
    .detail-body :deep(h2),
    .detail-body :deep(h3),
    .detail-body :deep(h4) {
        scroll-margin-top: calc(var(--header-h) + 108px);
    }

    .reader-tool {
        height: 40px;
        padding: 0 12px;
    }

    .reader-tool span {
        display: none;
    }

    .reader-tool--end {
        margin-left: 0;
    }

    .reader-tool i {
        font-size: 0.95rem;
    }

    .reader-fontsize__btn {
        min-width: 34px;
        padding: 0 10px;
    }

    .reader-sheet {
        padding: 18px 16px 22px;
        box-shadow: 4px 4px 0 var(--ink);
    }

    .detail-body {
        --reader-fs: 1rem;
    }

    .detail-body :deep(th),
    .detail-body :deep(td) {
        padding: 7px 10px;
    }

    .detail-body :deep(table) {
        display: block;
        overflow-x: auto;
        white-space: nowrap;
    }

    .detail-body :deep(img) {
        padding: 7px;
        box-shadow: 3px 3px 0 var(--ink);
    }

    .fab-container {
        right: 14px;
        bottom: calc(88px + env(safe-area-inset-bottom));
    }

    .fab-btn {
        min-width: 132px;
        height: 50px;
        font-size: 0.95rem;
        box-shadow: 4px 4px 0 var(--ink);
    }

    .answer-sheet-sidebar {
        width: 100%;
        max-width: 100%;
        border-right: 0;
        box-shadow: none;
    }

    .answer-sheet-cell {
        width: 44px;
        height: 44px;
    }
}

@media (max-width: 380px) {
    .reader-meta .chip {
        height: 28px;
        padding: 0 9px;
        font-size: 0.7rem;
    }

    .reader-meter__label {
        font-size: 0.64rem;
    }

    .reader-tools {
        padding: 5px;
    }
}

</style>
