<!-- src/views/ArticleDetailView.vue -->
<template>
    <div class="detail-container">
        <!-- 标题区 -->
        <div class="detail-title-area">
            <h1 class="detail-title">{{ blogTitle }}</h1>
        </div>

        <!-- 双栏内容区（保持每个section独立的双栏结构） -->
        <div class="detail-layout-wrapper" :class="'layout-' + layoutMode">
            <div class="detail-body" ref="detailBodyRef" v-html="renderedContent"></div>
        </div>

        <!-- 悬浮提交按钮 -->
        <div v-if="showFab" class="fab-container">
            <button class="fab-btn" :class="fabStatusClass" :disabled="isSubmitting" @click="handleFabClick"
                v-html="fabButtonHtml"></button>
        </div>

        <!-- 右下角悬浮按钮组 -->
        <div class="fab-right-group">
            <button v-if="hasSidebar" class="layout-toggle-fab" @click="cycleLayoutMode" :title="layoutToggleTitle">
                <i class="fas" :class="layoutToggleIcon"></i>
            </button>
            <button v-if="showAnswerSheetFab" class="answer-sheet-fab" @click="toggleAnswerSheet" title="答题卡">
                <i class="fas fa-th"></i>
            </button>
            <button v-if="showTocFab" class="toc-fab" @click="toggleToc" title="目录">
                <i class="fas fa-list"></i>
            </button>
        </div>

        <!-- 答题卡侧边栏 -->
        <div v-if="answerSheetVisible" class="answer-sheet-overlay" :class="{ 'overlay-active': answerSheetActive }" @click.self="closeAnswerSheet">
            <div class="answer-sheet-sidebar" :class="{ 'sidebar-open': answerSheetActive }" @click.stop>
                <div class="answer-sheet-header">
                    <span class="answer-sheet-title">答题卡</span>
                    <button class="answer-sheet-close-btn" @click="closeAnswerSheet">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="answer-sheet-body">
                    <div v-for="(section, sIdx) in answerSheetSections" :key="sIdx" class="answer-sheet-section">
                        <div class="answer-sheet-section-title">{{ section.h1Title }}</div>
                        <div class="answer-sheet-grid">
                            <div v-for="q in section.questions" :key="q.questionId"
                                 class="answer-sheet-cell"
                                 :class="'cell-' + q.status"
                                 @click="scrollToHeading(section.headingId)"
                                 :title="`第${q.localIndex}题: ${q.statusLabel}`">
                                {{ q.localIndex }}
                            </div>
                        </div>
                    </div>
                    <div v-if="answerSheetSections.length === 0" class="answer-sheet-empty">
                        暂无题目
                    </div>
                </div>
            </div>
        </div>

        <!-- 目录侧边栏 -->
        <div v-if="tocVisible" class="answer-sheet-overlay" :class="{ 'overlay-active': tocActive }" @click.self="closeToc">
            <div class="answer-sheet-sidebar" :class="{ 'sidebar-open': tocActive }" @click.stop>
                <div class="answer-sheet-header">
                    <span class="answer-sheet-title">目录</span>
                    <button class="answer-sheet-close-btn" @click="closeToc">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="toc-body">
                    <div v-for="(h1, idx) in tocData" :key="h1.id" class="toc-h1-item">
                        <div class="toc-h1-title">
                            <i class="fas toc-arrow" :class="expandedTocH1 === idx ? 'fa-chevron-down' : 'fa-chevron-right'" @click.stop="toggleTocH1(idx)"></i>
                            <span class="toc-text" @click="scrollToHeading(h1.id)">{{ h1.text }}</span>
                        </div>
                        <div v-if="expandedTocH1 === idx && h1.children.length" class="toc-h2-list">
                            <div v-for="h2 in h1.children" :key="h2.id" class="toc-h2-item" @click="scrollToHeading(h2.id)">
                                {{ h2.text }}
                            </div>
                        </div>
                    </div>
                    <div v-if="tocData.length === 0" class="answer-sheet-empty">
                        暂无目录
                    </div>
                </div>
            </div>
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
import { supabase } from '@/utils/supabase'
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

// 答题卡状态
const answerSheetVisible = ref(false)
const answerSheetActive = ref(false)
const h1QuestionSections = ref([])

// 目录状态
const tocVisible = ref(false)
const tocActive = ref(false)
const tocData = ref([])
const expandedTocH1 = ref(null)

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

// ========== 工具函数 ==========
function normalizeLineBreaks(text) {
    return String(text ?? '').replace(/\r\n/g, '\n')
}

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

// 占位符语法（仅支持全角形式，编号前后允许空格）：
//   答题占位符：【@】自动编号 / 【@N】显式编号
//   题干占位符：【题干N】标记第 N 题的题干片段（可多处使用，按出现顺序拼接）
//   公共题干占位符：【题干N-M】标记第 N~M 题的公共题干（大题），区间内每题共享
const ANSWER_TOKEN = /【@(\d*)】/g
const STEM_TOKEN = /【题干\s*(\d+)(?:\s*-\s*(\d+))?\s*】/g
// 单行判定用（match() 搭配全局正则会丢失捕获组，故另定义非全局版本）
const STEM_TOKEN_ONE = /【题干\s*(\d+)(?:\s*-\s*(\d+))?\s*】/

/** 清理题干片段中的 markdown 语法、标题行、分隔线与空行 */
function cleanStemText(text) {
    return text
        .replace(/\*\*(.+?)\*\*/g, '$1')
        .replace(/`(.+?)`/g, '$1')
        .split('\n')
        .filter(line => {
            const t = line.trim()
            if (!t) return false
            if (/^#{1,6}\s/.test(t)) return false // 排除标题行
            if (/^---\s*$/.test(t)) return false  // 排除分隔线
            return true
        })
        .join('\n')
        .trim()
}

/** 查找某个位置之前最近的一级标题（# 章节标题），找不到返回「本文」 */
function nearestH1(markdown, pos) {
    const lines = markdown.slice(0, pos).split('\n')
    for (let i = lines.length - 1; i >= 0; i--) {
        const m = lines[i]?.match(/^#\s+(.*)$/)
        if (m) {
            return m[1].replace(/\*\*(.+?)\*\*/g, '$1').trim() || '本文'
        }
    }
    return '本文'
}

function injectQuestionSlots(markdown) {
    questionTextMap.value = new Map()

    // 第一遍：按位置收集所有占位符（题干标记 + 答题标记），记录答题标记的原始位置。
    // 题干标记支持区间：【题干N】单题 / 【题干N-M】公共题干（区间内每题共享）
    const tokens = []
    for (const m of markdown.matchAll(STEM_TOKEN)) {
        const start = Number(m[1])
        const end = m[2] ? Number(m[2]) : start
        const ids = []
        for (let n = start; n <= end; n++) ids.push(String(n))
        tokens.push({ type: 'stem', ids, index: m.index, end: m.index + m[0].length })
    }
    for (const m of markdown.matchAll(ANSWER_TOKEN)) {
        tokens.push({ type: 'answer', id: m[1], index: m.index, end: m.index + m[0].length })
    }
    tokens.sort((a, b) => a.index - b.index)
    const answerRawIndices = tokens.filter(t => t.type === 'answer').map(t => t.index)

    // 题干片段：题干标记之后、下一个占位符之前的文本。
    // 一个「题干N」可出现在正文任何位置（如双栏分离布局），按出现顺序归属第 N 题
    const stemFragments = new Map()   // 题号 → 片段数组
    for (let i = 0; i < tokens.length; i++) {
        const t = tokens[i]
        if (t.type !== 'stem') continue
        const next = tokens[i + 1]
        const end = next ? next.index : markdown.length
        const fragment = cleanStemText(markdown.slice(t.end, end))
        if (!fragment) continue
        t.ids.forEach(id => {
            if (!stemFragments.has(id)) stemFragments.set(id, [])
            stemFragments.get(id).push(fragment)
        })
    }

    // 第二遍：大题分组框。
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

    // 第三遍：渲染 markdown
    //   答题标记 → 答题卡片（构建 questionId → 显示顺序号 映射）
    //   题干标记 → 样式化题号标签（显示顺序号，如「第 1 题」「第 1-3 题」，与实际 id 无关）
    let autoCounter = 1
    const usedIndices = new Set()
    const questionIds = []
    const answerPositions = new Map() // questionId → 答题占位符在原文中的位置
    const idOrderMap = new Map()      // questionId → 显示顺序号（从 1 按出现顺序递增）
    let slotCount = 0
    let answerIdx = 0
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
            answerPositions.set(questionId, answerRawIndices[answerIdx++])
            return `<div class="question-slot" data-question-id="${questionId}"></div>`
        })
        .replace(STEM_TOKEN, (_, n, m2) => {
            const on = idOrderMap.get(String(n))
            const om = m2 ? idOrderMap.get(String(m2)) : undefined
            const start = on ?? Number(n)
            const end = m2 ? (om ?? Number(m2)) : null
            return `<span class="stem-label">第 ${start}${end !== null ? '-' + end : ''} 题</span>`
        })

    // 第三遍：生成错题本题目内容
    //   有题干标记 → 拼接全部片段；无 → 回退「最近章节标题 · 第N题」
    questionIds.forEach(questionId => {
        const fragments = stemFragments.get(questionId)
        let text
        if (fragments && fragments.length > 0) {
            text = fragments.join('\n')
        } else {
            text = `${nearestH1(markdown, answerPositions.get(questionId) ?? 0)} · 第${questionId}题`
        }
        questionTextMap.value.set(questionId, text.length > 400 ? text.slice(0, 400) + '…' : text)
    })

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
    const { data, error } = await supabase
        .from('article_answer_keys')
        .select('blog_id, question_id, answer_text, auto_grade, updated_at')
        .eq('blog_id', blogId)

    if (error) {
        console.error('加载答案设置失败:', error)
        return new Map()
    }

    const map = new Map()
        ; (data || []).forEach(item => map.set(String(item.question_id), item))
    return map
}

async function loadQuestionSubmissions(blogId, studentId) {
    if (!blogId || !studentId) return new Map()

    const numericId = Number(studentId)
    if (!Number.isFinite(numericId)) return new Map()

    const { data, error } = await supabase
        .from('article_question_submissions')
        .select('blog_id, student_id, question_id, answer_text, review_status, review_result, submitted_at, reviewed_at')
        .eq('blog_id', blogId)
        .eq('student_id', numericId)

    if (error) {
        console.error('加载学生提交失败:', error)
        return new Map()
    }

    const map = new Map()
        ; (data || []).forEach(item => {
            const questionId = String(item.question_id)
            map.set(questionId, { ...item, question_id: questionId })
        })
    return map
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
        // 偏移顶部导航栏的高度（~56px），使标题不紧贴视口顶部
        const NAV_BAR_HEIGHT = 64
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
    const studentIdStr = String(studentId.value)
    const blogIdVal = blogId.value

    // 检查是否已在错题本
    const { data: existing } = await supabase
        .from('wrong_questions')
        .select('id')
        .eq('student_id', studentIdStr)
        .eq('source_blog_id', blogIdVal)
        .eq('source_question_id', key)
        .maybeSingle()

    if (existing) {
        showToast('该题已在错题本中', 'info')
        return
    }

    const node = slotNodes.value.get(key)
    const submission = submissionMap.value.get(key)
    const answer = node?.textarea?.value || submission?.answer_text || ''

    const { error } = await supabase
        .from('wrong_questions')
        .insert({
            student_id: studentIdStr,
            source_blog_id: blogIdVal,
            source_question_id: key,
            my_answer: answer,
            is_manual: true,
            wrong_count: 1
        })

    if (error) {
        console.error('加入错题本失败:', error)
        showToast(`添加失败：${error.message || '请稍后重试'}`, 'error', 5000)
        return
    }

    showToast('已加入错题本', 'success')
    if (btn) {
        btn.disabled = true
        btn.innerHTML = '<i class="fas fa-check"></i><span>已加入</span>'
    }
}

// ========== 错题自动收集 ==========
/**
 * 题目被批阅为「错误」时，自动收集到错题本
 * @param {string|number} questionId 题目编号
 * @param {string} [myAnswer] 本次作答内容（可选，覆盖 submissionMap 中的值）
 */
async function autoCollectWrongQuestion(questionId, myAnswer) {
    if (!blogId.value || !studentId.value) return

    const key = String(questionId)
    const submission = myAnswer !== undefined
        ? { answer_text: myAnswer }
        : submissionMap.value.get(key)

    try {
        await wrongQuestionsStore.autoCollect({
            studentId: studentId.value,
            myAnswer: submission?.answer_text || '',
            sourceArticleId: blogId.value,
            sourceQuestionId: key
        })
    } catch (err) {
        console.error('自动收集错题失败:', err)
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
        const now = new Date().toISOString()
        const autoWrongQueue = []

        slotNodes.value.forEach((node, questionId) => {
            if (targetQuestionId && String(targetQuestionId) !== String(questionId)) return

            const submission = submissionMap.value.get(questionId)
            if (submission?.review_status === 'reviewed') return

            const answer = node.textarea?.value || ''
            const answerKey = answerKeyMap.value.get(questionId)
            const autoGrade = Boolean(answerKey?.auto_grade && answerKey.answer_text)

            if (autoGrade && answer.trim() === '') return
            if (!submission && answer.trim() === '' && !autoGrade) return

            let reviewStatus = 'pending'
            let reviewResult = null
            let reviewedAt = null

            if (autoGrade) {
                reviewStatus = 'reviewed'
                reviewResult = normalizeLineBreaks(answer) === normalizeLineBreaks(answerKey.answer_text) ? 'correct' : 'wrong'
                reviewedAt = now
                if (reviewResult === 'wrong') {
                    autoWrongQueue.push({ questionId, answer })
                }
            }

            rows.push({
                blog_id: blogId.value,
                student_id: studentId.value,
                question_id: questionId,
                answer_text: answer,
                review_status: reviewStatus,
                review_result: reviewResult,
                submitted_at: now,
                reviewed_at: reviewedAt
            })
        })

        if (!rows.length) {
            if (!silent) setFabStatus(true, '没有需要提交的内容')
            return true
        }

        const { error } = await supabase
            .from('article_question_submissions')
            .upsert(rows, { onConflict: 'blog_id,student_id,question_id' })

        if (error) {
            console.error('保存学生答案失败:', error)
            if (!silent) setFabStatus(false, '提交失败，请稍后重试')
            return false
        }

        // 自动批阅为「错误」的题目收集到错题本（静默执行，不影响提交结果）
        autoWrongQueue.forEach(item => {
            autoCollectWrongQuestion(item.questionId, item.answer)
        })

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

        const { error } = await supabase
            .from('article_answer_keys')
            .upsert(rows, { onConflict: 'blog_id,question_id' })

        if (error) {
            console.error('保存答案设置失败:', error)
            if (!silent) setFabStatus(false, '保存失败，请稍后重试')
            return false
        }

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
    const now = new Date().toISOString()

    const { error } = await supabase
        .from('article_question_submissions')
        .upsert([{
            blog_id: blogId.value,
            student_id: studentId.value,
            question_id: questionId,
            answer_text: submission.answer_text || '',
            review_status: 'reviewed',
            review_result: reviewResult,
            submitted_at: submission.submitted_at || now,
            reviewed_at: now
        }], { onConflict: 'blog_id,student_id,question_id' })

    if (error) {
        console.error('保存批阅结果失败:', error)
        setFabStatus(false, '批阅保存失败，请重试')
        return false
    }

    submissionMap.value.set(questionId, {
        ...submission,
        review_status: 'reviewed',
        review_result: reviewResult,
        reviewed_at: now
    })

    const status = statusNodes.value.get(questionId)
    if (status) {
        const { text, cls } = buildStatusPill(submissionMap.value.get(questionId))
        status.textContent = text
        status.className = `question-pill ${cls}`
    }

    // 批阅为「错误」时自动收集到错题本（静默执行）
    if (reviewResult === 'wrong') {
        autoCollectWrongQuestion(questionId, submission.answer_text)
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

// Toast 弹窗
let toastTimer = null
function showToast(message, type = 'info', duration = 3000) {
    const existing = document.querySelector('.custom-toast')
    if (existing) {
        existing.remove()
        if (toastTimer) {
            clearTimeout(toastTimer)
            toastTimer = null
        }
    }

    const toast = document.createElement('div')
    toast.className = `custom-toast toast-${type}`
    toast.textContent = message
    document.body.appendChild(toast)

    requestAnimationFrame(() => toast.classList.add('toast-visible'))

    toastTimer = setTimeout(() => {
        toast.classList.remove('toast-visible')
        setTimeout(() => {
            if (toast.parentNode) toast.parentNode.removeChild(toast)
            toastTimer = null
        }, 300)
    }, duration)
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
                const NAV_BAR_HEIGHT = 64
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

// ========== 生命周期 ==========
onMounted(async () => {
    await blogStore.loadArticleData()
    await loadContent()
    breakpointMql.addEventListener('change', handleBreakpointChange)
    window.addEventListener('pagehide', handlePageHide)
})

onUnmounted(() => {
    disconnect()
    breakpointMql.removeEventListener('change', handleBreakpointChange)
    window.removeEventListener('pagehide', handlePageHide)
})
</script>

<style scoped>
.detail-container {
    max-width: 1300px;
    min-width: 800px;
    margin: 0 auto;
    padding: 30px 32px 80px;
}

.detail-title-area {
    margin-bottom: 24px;
}

.detail-title {
    font-size: 2.2rem;
    font-weight: 700;
    color: #2d4a3a;
    margin-bottom: 6px;
}

/* 每个section的双栏布局 */
.detail-body :deep(.detail-section-two-column) {
    display: flex;
    gap: 40px;
    align-items: flex-start;
    margin-bottom: 40px;
    border-bottom: 2px dashed rgba(120, 170, 155, 0.15);
    padding-bottom: 32px;
}

.detail-body :deep(.detail-section-two-column:last-child) {
    border-bottom: none;
    margin-bottom: 0;
    padding-bottom: 0;
}

.detail-body :deep(.detail-main-column) {
    flex: 2;
    min-width: 0;
    overflow: visible !important;
}

.detail-body :deep(.detail-sidebar-column) {
    flex: 1;
    min-width: 0;
    position: sticky;
    top: 20px;
    max-height: calc(100vh - 40px);
    overflow-y: auto;
    background: rgba(255, 255, 255, 0.3);
    border-radius: 16px;
    padding: 16px 20px;
    border: 1px solid rgba(120, 170, 155, 0.15);
}

/* 单栏模式：隐藏对侧列，可见列占满 */
.detail-layout-wrapper.layout-left :deep(.detail-sidebar-column) {
    display: none;
}
.detail-layout-wrapper.layout-left :deep(.detail-main-column) {
    flex: 1;
    max-width: 100%;
}

.detail-layout-wrapper.layout-right :deep(.detail-main-column) {
    display: none;
}
.detail-layout-wrapper.layout-right :deep(.detail-sidebar-column) {
    flex: 1;
    max-width: none;
}

/* 正文样式 */
.detail-body {
    background: rgba(255, 255, 255, 0.55);
    backdrop-filter: blur(8px);
    border-radius: 24px;
    padding: 40px 48px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    box-shadow: 0 8px 32px var(--shadow);
    line-height: 1.85;
    color: #2d4a3a;
    font-size: 1.05rem;
    overflow: visible;
}

/* Markdown 内容样式 */
.detail-body :deep(h1) {
    color: #2d8cf0;
    font-size: 2.2rem;
    font-weight: 800;
    margin: 1.4em 0 0.8em 0;
    border-left: 6px solid #2d8cf0;
    padding: 10px 14px;
    background: linear-gradient(90deg, rgba(234, 246, 255, 0.95) 0%, rgba(234, 246, 255, 0.6) 60%);
    border-radius: 8px;
}

.detail-body :deep(h2) {
    color: #19be6b;
    font-size: 1.8rem;
    font-weight: 700;
    margin: 1.2em 0 0.75em 0;
    border-left: 5px solid #19be6b;
    padding: 9px 12px;
    background: linear-gradient(90deg, rgba(234, 255, 243, 0.95) 0%, rgba(234, 255, 243, 0.6) 60%);
    border-radius: 6px;
}

.detail-body :deep(h3) {
    color: #ff8f9e;
    font-size: 1.4rem;
    font-weight: 700;
    margin: 1.2em 0 0.8em 0;
    border-left: 4px solid #ff8f9e;
    padding: 8px 12px;
    background: linear-gradient(90deg, rgba(255, 251, 230, 0.9) 0%, rgba(255, 251, 230, 0.6) 60%);
    border-radius: 8px;
}

.detail-body :deep(p) {
    margin: 0.8em 0;
}

.detail-body :deep(ul),
.detail-body :deep(ol) {
    padding-left: 1.8em;
    margin: 0.6em 0;
}

.detail-body :deep(blockquote) {
    border-left: 4px solid var(--teal);
    padding: 0.6em 1.2em;
    margin: 1em 0;
    background: rgba(120, 200, 190, 0.08);
    border-radius: 0 12px 12px 0;
}

.detail-body :deep(code) {
    font-family: "Courier New", monospace;
    background: rgba(120, 170, 155, 0.12);
    padding: 0.15em 0.5em;
    border-radius: 6px;
    font-size: 0.9em;
    color: #2a7a6a;
}

.detail-body :deep(pre) {
    background: #1a2a22;
    color: #d4ece4;
    padding: 18px 22px;
    border-radius: 16px;
    overflow-x: auto;
    margin: 1.2em 0;
}

.detail-body :deep(pre code) {
    background: transparent;
    color: inherit;
    padding: 0;
}

.detail-body :deep(img) {
    max-width: 100%;
    border-radius: 16px;
    margin: 1em 0;
}

.detail-body :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 1em 0;
}

.detail-body :deep(th),
.detail-body :deep(td) {
    border: 1px solid rgba(120, 170, 155, 0.2);
    padding: 8px 14px;
    text-align: left;
}

.detail-body :deep(th) {
    background: rgba(120, 170, 155, 0.08);
}

.detail-body :deep(strong),
.detail-body :deep(b) {
    color: #d57587;
}

/* 题目卡片样式 */
:deep(.question-slot) {
    display: block;
    margin: 18px 0;
}

:deep(.question-card) {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 6px 10px;
    border-radius: 22px;
    background: rgba(255, 255, 255, 0.25);
    border: 1px solid rgba(120, 170, 155, 0.07);
    box-shadow: none;
    position: relative;
}

:deep(.question-card-header) {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    opacity: 0.45;
    font-size: 0.82rem;
    min-height: 20px;
}

/* 状态角标：左上角绝对定位 */
:deep(.question-card-study > .question-pill) {
    position: absolute;
    top: -10px;
    left: 12px;
    z-index: 1;
    padding: 1px 10px;
    min-height: 20px;
    border-radius: 999px;
    font-size: 0.68rem;
    font-weight: 600;
    line-height: 1.5;
    background: rgba(120, 170, 155, 0.08);
    color: #4c6b5b;
    border: 1px solid rgba(120, 170, 155, 0.15);
    display: inline-flex;
    align-items: center;
    gap: 4px;
}

:deep(.question-card-study > .question-pill.is-waiting),
:deep(.question-card-study > .question-pill.is-muted) {
    background: rgba(240, 244, 242, 0.7);
    color: #7a8a80;
    border-color: rgba(180, 200, 190, 0.3);
}

:deep(.question-card-study > .question-pill.is-pending) {
    background: rgba(242, 226, 172, 0.6);
    color: #7b6420;
    border-color: rgba(227, 203, 124, 0.35);
}

:deep(.question-card-study > .question-pill.is-correct) {
    background: rgba(205, 237, 221, 0.75);
    color: #2f7b57;
    border-color: rgba(80, 176, 122, 0.25);
}

:deep(.question-card-study > .question-pill.is-partial) {
    background: rgba(255, 234, 188, 0.7);
    color: #a87b19;
    border-color: rgba(227, 188, 90, 0.3);
}

:deep(.question-card-study > .question-pill.is-wrong) {
    background: rgba(255, 224, 224, 0.7);
    color: #b65661;
    border-color: rgba(208, 116, 126, 0.3);
}

/* 题号角标：左上角（与状态角标同款样式） */
:deep(.question-card-study > .question-pill.is-number) {
    background: rgba(91, 168, 164, 0.15);
    color: #2f6a66;
    border-color: rgba(91, 168, 164, 0.3);
}

/* 状态角标：右上角 */
:deep(.question-card-study > .question-pill:not(.is-number)) {
    left: auto;
    right: 12px;
}

/* 题干题号标签（正文中显示「第 N 题」） */
:deep(.stem-label) {
    display: inline-block;
    padding: 2px 12px;
    border-radius: 999px;
    background: rgba(91, 168, 164, 0.15);
    color: #2f6a66;
    font-size: 0.82rem;
    font-weight: 600;
    border: 1px solid rgba(91, 168, 164, 0.3);
    margin-right: 8px;
    vertical-align: middle;
}

/* 大题分组框（公共题干 + 小题，纯样式作用） */
:deep(.question-group) {
    border: 2px solid rgba(91, 168, 164, 0.35);
    border-radius: 20px;
    padding: 20px 22px 14px;
    background: rgba(240, 248, 246, 0.45);
    margin: 22px 0;
}

:deep(.question-textarea) {
    width: 100%;
    resize: vertical;
    min-height: 64px;
    border-radius: 14px;
    border: 1px solid rgba(120, 170, 155, 0.22);
    background: rgba(255, 255, 255, 0.98);
    padding: 10px 14px;
    font-size: 1rem;
    color: #345143;
    outline: none;
    line-height: 1.6;
    transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
    box-shadow: 0 2px 8px rgba(80, 130, 120, 0.04);
}

:deep(.question-textarea:focus) {
    border-color: rgba(224, 199, 106, 0.8);
    box-shadow: 0 0 0 4px rgba(243, 227, 162, 0.25);
}

:deep(.question-textarea.is-showing-answer) {
    background: #eaffea !important;
    border-color: #4caf50 !important;
    box-shadow: 0 0 0 3px rgba(76, 175, 80, 0.2) !important;
}

:deep(.question-textarea.is-locked),
:deep(.question-textarea[readonly]) {
    background: rgba(249, 247, 240, 0.95);
    color: #66756c;
}

:deep(.question-card-footer) {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 6px;
    opacity: 0.5;
    padding-top: 2px;
}

:deep(.question-card-footer-between) {
    justify-content: space-between;
}

/* 非角标的 pill（review/answer 模式中使用） */
:deep(.question-card:not(.question-card-study) > .question-pill),
:deep(.question-card-review .question-pill),
:deep(.question-card-answer .question-pill) {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 0 10px;
    min-height: 24px;
    border-radius: 999px;
    background: rgba(120, 170, 155, 0.08);
    color: #4c6b5b;
    font-size: 0.75rem;
    border: 1px solid rgba(120, 170, 155, 0.10);
}

:deep(.question-card-review .question-pill.is-waiting),
:deep(.question-card-review .question-pill.is-muted),
:deep(.question-card-answer .question-pill.is-waiting),
:deep(.question-card-answer .question-pill.is-muted) {
    background: rgba(216, 222, 219, 0.3);
    color: #6d756e;
}

:deep(.question-card-review .question-pill.is-pending),
:deep(.question-card-answer .question-pill.is-pending) {
    background: rgba(242, 226, 172, 0.35);
    color: #7b6420;
    border-color: rgba(227, 203, 124, 0.25);
}

:deep(.question-card-review .question-pill.is-correct),
:deep(.question-card-review .question-pill.is-auto),
:deep(.question-card-answer .question-pill.is-correct),
:deep(.question-card-answer .question-pill.is-auto) {
    background: rgba(205, 237, 221, 0.55);
    color: #2f7b57;
    border-color: rgba(80, 176, 122, 0.15);
}

:deep(.question-card-review .question-pill.is-partial),
:deep(.question-card-answer .question-pill.is-partial) {
    background: rgba(255, 234, 188, 0.6);
    color: #a87b19;
    border-color: rgba(227, 188, 90, 0.2);
}

:deep(.question-card-review .question-pill.is-wrong),
:deep(.question-card-answer .question-pill.is-wrong) {
    background: rgba(255, 224, 224, 0.6);
    color: #b65661;
    border-color: rgba(208, 116, 126, 0.2);
}

:deep(.question-review-toolbar) {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

:deep(.question-icon-btn) {
    width: 42px;
    height: 42px;
    border-radius: 50%;
    border: 1px solid transparent;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
    box-shadow: 0 6px 16px rgba(80, 130, 120, 0.08);
    background: rgba(255, 255, 255, 0.92);
    color: #496556;
}

:deep(.question-icon-btn.is-correct) {
    background: rgba(205, 237, 221, 0.88);
    color: #2f7b57;
    border-color: rgba(80, 176, 122, 0.18);
}

:deep(.question-icon-btn.is-partial) {
    background: rgba(255, 234, 188, 0.9);
    color: #a87b19;
    border-color: rgba(227, 188, 90, 0.24);
}

:deep(.question-icon-btn.is-wrong) {
    background: rgba(255, 224, 224, 0.92);
    color: #b65661;
    border-color: rgba(208, 116, 126, 0.24);
}

:deep(.question-icon-btn:hover) {
    transform: translateY(-1px);
    box-shadow: 0 10px 20px rgba(80, 130, 120, 0.12);
}

:deep(.question-auto-grade) {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: #5b6d63;
    font-size: 0.9rem;
}

:deep(.question-auto-grade input) {
    width: 18px;
    height: 18px;
    accent-color: #d9ba4b;
}

:deep(.question-action-btn) {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    align-self: flex-end;
    padding: 10px 16px;
    border-radius: 999px;
    border: 1px solid rgba(120, 170, 155, 0.18);
    background: linear-gradient(135deg, rgba(255, 248, 214, 0.98), rgba(243, 227, 162, 0.92));
    color: #705d13;
    font-weight: 600;
    cursor: pointer;
    box-shadow: 0 8px 18px rgba(80, 130, 120, 0.08);
    transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

:deep(.question-action-btn:hover) {
    transform: translateY(-1px);
    box-shadow: 0 12px 22px rgba(80, 130, 120, 0.12);
}

:deep(.question-action-btn:disabled) {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}

:deep(.question-reference-wrapper) {
    margin-top: 4px;
    padding: 10px 14px;
    background: rgba(245, 248, 250, 0.8);
    border-radius: 12px;
    border-left: 3px solid #7ab8a0;
}

:deep(.question-reference-label) {
    font-size: 0.85rem;
    font-weight: 600;
    color: #4c6b5b;
    display: block;
    margin-bottom: 4px;
}

:deep(.question-reference-text) {
    font-size: 0.95rem;
    color: #2d4a3a;
    padding: 4px 0;
    white-space: pre-wrap;
    word-break: break-word;
}

/* FAB 按钮 */
.fab-container {
    position: fixed;
    bottom: 30px;
    right: 30px;
    z-index: 999;
}

.fab-btn {
    width: 156px;
    height: 56px;
    border-radius: 28px;
    border: none;
    background: linear-gradient(135deg, rgba(255, 248, 214, 0.98), rgba(243, 227, 162, 0.92));
    color: #705d13;
    font-size: 1.4rem;
    cursor: pointer;
    box-shadow: 0 6px 24px rgba(217, 186, 75, 0.4);
    transition: all 0.3s ease;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
}

.fab-btn:hover {
    transform: scale(1.08) translateY(-2px);
    box-shadow: 0 10px 32px rgba(217, 186, 75, 0.5);
}

.fab-btn:active {
    transform: scale(0.94);
}

.fab-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;
}

.fab-btn.is-success {
    background: #4caf50 !important;
    color: white !important;
}

.fab-btn.is-error {
    background: #ef5350 !important;
    color: white !important;
}

/* 右下角悬浮按钮组 */
.fab-right-group {
    position: fixed;
    bottom: 100px;
    right: 30px;
    z-index: 998;
    display: flex;
    flex-direction: row;
    gap: 12px;
    align-items: center;
}

.fab-right-group button {
    position: static;
    z-index: auto;
}

/* 布局切换悬浮球 + 答题卡悬浮球 + 目录悬浮球（相同样式） */
.layout-toggle-fab,
.answer-sheet-fab,
.toc-fab {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    border: none;
    background: linear-gradient(135deg, var(--teal), var(--teal-dark));
    color: white;
    font-size: 1.1rem;
    cursor: pointer;
    box-shadow: 0 4px 20px rgba(123, 200, 196, 0.45);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.25s ease;
}

.layout-toggle-fab:hover,
.answer-sheet-fab:hover {
    transform: scale(1.12) translateY(-2px);
    box-shadow: 0 8px 28px rgba(123, 200, 196, 0.55);
}

.layout-toggle-fab:active,
.answer-sheet-fab:active {
    transform: scale(0.92);
}

/* 答题卡侧边栏 */
.answer-sheet-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 1000;
    background: rgba(0, 0, 0, 0.15);
    opacity: 0;
    transition: opacity 0.3s ease;
}
.answer-sheet-overlay.overlay-active {
    opacity: 1;
}

.answer-sheet-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    width: 300px;
    height: 100%;
    background: #ffffff;
    box-shadow: 4px 0 24px rgba(0, 0, 0, 0.12);
    z-index: 1001;
    display: flex;
    flex-direction: column;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
}
.answer-sheet-sidebar.sidebar-open {
    transform: translateX(0);
}

.answer-sheet-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px 20px 16px;
    border-bottom: 1px solid rgba(120, 170, 155, 0.12);
    flex-shrink: 0;
}

.answer-sheet-title {
    font-size: 1.25rem;
    font-weight: 700;
    color: #2d4a3a;
}

.answer-sheet-close-btn {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    border: none;
    background: rgba(120, 170, 155, 0.08);
    color: #4c6b5b;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1rem;
    transition: background 0.2s ease;
}
.answer-sheet-close-btn:hover {
    background: rgba(120, 170, 155, 0.2);
}

.answer-sheet-body {
    flex: 1;
    overflow-y: auto;
    padding: 16px 20px 24px;
}

.answer-sheet-section {
    margin-bottom: 20px;
}
.answer-sheet-section:last-child {
    margin-bottom: 0;
}

.answer-sheet-section-title {
    font-size: 0.88rem;
    font-weight: 600;
    color: #3a5a4a;
    margin-bottom: 10px;
    line-height: 1.4;
    word-break: break-word;
}

.answer-sheet-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.answer-sheet-cell {
    width: 32px;
    height: 32px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.8rem;
    font-weight: 600;
    cursor: default;
    border: 1px solid rgba(120, 170, 155, 0.15);
    background: rgba(240, 244, 242, 0.5);
    color: #6d7a72;
    transition: transform 0.15s ease;
}
.answer-sheet-cell:hover {
    transform: scale(1.15);
}

/* 答题卡单元格状态色 */
.answer-sheet-cell.cell-unsubmitted {
    background: rgba(240, 244, 242, 0.4);
    color: #8a9a90;
    border-color: rgba(120, 170, 155, 0.12);
}
.answer-sheet-cell.cell-pending {
    background: rgba(242, 226, 172, 0.55);
    color: #7b6420;
    border-color: rgba(227, 203, 124, 0.3);
}
.answer-sheet-cell.cell-correct {
    background: rgba(205, 237, 221, 0.7);
    color: #2f7b57;
    border-color: rgba(80, 176, 122, 0.2);
}
.answer-sheet-cell.cell-partial {
    background: rgba(255, 234, 188, 0.7);
    color: #a87b19;
    border-color: rgba(227, 188, 90, 0.25);
}
.answer-sheet-cell.cell-wrong {
    background: rgba(255, 224, 224, 0.7);
    color: #b65661;
    border-color: rgba(208, 116, 126, 0.25);
}

.answer-sheet-empty {
    text-align: center;
    color: #8a9a90;
    font-size: 0.9rem;
    padding: 40px 0;
}

/* 目录树样式 */
.toc-body {
    flex: 1;
    overflow-y: auto;
    padding: 8px 0 24px;
}

.toc-h1-item {
    border-bottom: 1px solid rgba(120, 170, 155, 0.08);
}
.toc-h1-item:last-child {
    border-bottom: none;
}

.toc-h1-title {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 20px;
    cursor: pointer;
    font-size: 0.92rem;
    font-weight: 600;
    color: #2d4a3a;
    transition: background 0.15s ease;
    user-select: none;
}
.toc-h1-title:hover {
    background: rgba(120, 170, 155, 0.06);
}
.toc-h1-title i {
    font-size: 0.7rem;
    color: #7a9a8a;
    width: 12px;
    flex-shrink: 0;
}

.toc-h2-list {
    background: rgba(245, 248, 246, 0.4);
}

.toc-h2-item {
    padding: 8px 20px 8px 44px;
    font-size: 0.85rem;
    color: #4c6b5b;
    cursor: pointer;
    transition: background 0.15s ease;
    user-select: none;
}
.toc-h2-item:hover {
    background: rgba(120, 170, 155, 0.08);
    color: #1d4a3a;
}

/* Toast 弹窗 */
:global(.custom-toast) {
    position: fixed;
    top: 30px;
    left: 50%;
    transform: translateX(-50%) translateY(-20px);
    padding: 8px 25px;
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(12px);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
    font-size: 1.05rem;
    font-weight: 500;
    color: #2d4a3a;
    z-index: 9999;
    opacity: 0;
    transition: opacity 0.3s ease, transform 0.3s ease;
    border: 1px solid rgba(255, 255, 255, 0.6);
}

:global(.custom-toast.toast-visible) {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
}

:global(.custom-toast.toast-success) {
    border: 4px solid #4caf50;
    color: #1e4a2a;
}

:global(.custom-toast.toast-error) {
    border: 4px solid #ef5350;
    color: #7a2a2a;
}

:global(.custom-toast.toast-info) {
    border: 4px solid #42a5f5;
    color: #1a3a5a;
}

/* 响应式 */
@media (min-width: 1024px) {
    .detail-container {
        width: 100%;
        padding: 50px 60px 120px;
    }

    .detail-title {
        font-size: 3rem;
    }

    .detail-body {
        padding: 56px 64px;
        font-size: 1.25rem;
    }
}

@media (min-width: 641px) and (max-width: 1023px) {
    .detail-container {
        width: 100%;
        padding: 30px 28px 70px;
    }

    .detail-body :deep(.detail-section-two-column) {
        flex-direction: column;
        gap: 10px;
        padding-bottom: 16px;
        margin-bottom: 16px;
    }

    .detail-body :deep(.detail-sidebar-column) {
        max-width: 100%;
        max-height: none;
        overflow-y: visible;
        position: static;
        padding: 10px 0 0 0;
        border: none;
        background: transparent;
    }
}

@media (max-width: 640px) {
    .detail-container {
        width: 100%;
        padding: 16px 14px 40px;
        min-width: auto;
    }

    .detail-title {
        font-size: 1.6rem;
    }

    .detail-body {
        padding: 18px 16px;
        font-size: 0.95rem;
    }

    .detail-body :deep(.detail-section-two-column) {
        flex-direction: column;
        gap: 10px;
        padding-bottom: 16px;
        margin-bottom: 16px;
    }

    .detail-body :deep(.detail-sidebar-column) {
        max-width: 100%;
        position: static;
        padding: 10px 0 0 0;
        border: none;
        max-height: none;
        overflow-y: visible;
        background: transparent;
    }

    .fab-container {
        bottom: 16px;
        right: 16px;
    }

    .fab-btn {
        width: 120px;
        height: 44px;
        font-size: 1.1rem;
    }

    .fab-right-group {
        bottom: 72px;
        right: 16px;
        gap: 10px;
    }

    .layout-toggle-fab,
    .answer-sheet-fab,
    .toc-fab {
        width: 42px;
        height: 42px;
        font-size: 1rem;
    }

    .answer-sheet-sidebar {
        width: 260px;
    }
}
</style>