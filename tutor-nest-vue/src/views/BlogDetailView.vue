<!-- src/views/BlogDetailView.vue -->
<template>
    <div class="detail-container">
        <!-- 绘制状态指示器 -->
        <div v-if="isDrawingActive" class="drawing-indicator">
            <i class="fas fa-pen"></i>
            <span>触控笔绘制已激活</span>
        </div>

        <!-- 标题区 -->
        <div class="detail-title-area">
            <h1 class="detail-title">{{ blogTitle }}</h1>
        </div>

        <!-- 正文内容 -->
        <div class="detail-body" ref="detailBodyRef" v-html="renderedContent"></div>

        <!-- 悬浮提交按钮 -->
        <div v-if="showFab" class="fab-container">
            <button class="fab-btn" :class="fabStatusClass" :disabled="isSubmitting" @click="handleFabClick"
                v-html="fabButtonHtml"></button>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useBlogStore } from '@/stores/blogStore'
import { useKatex } from '@/composables/useKatex'
import { useImageEmbed } from '@/composables/useImageEmbed'
import { useDrawingInDetail } from '@/composables/useDrawing'
import { supabase } from '@/utils/supabase'
import { marked } from 'marked'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const blogStore = useBlogStore()
const { renderMath } = useKatex()
const { processMarkdown, observe, disconnect } = useImageEmbed()
const { isActive: isDrawingActive, isDrawing } = useDrawingInDetail()

// 配置图片基础路径
const { setBasePath } = useImageEmbed()
setBasePath('blogs/图片/')

// ========== 状态管理 ==========
const detailBodyRef = ref(null)
const blogTitle = ref('加载中...')
const renderedContent = ref('')
const currentMode = ref('study') // study, review, answer
const isSubmitting = ref(false)
const fabStatusClass = ref('')
const contentVersion = ref(0)

// 题目相关状态
const questionCount = ref(0)
const questionIdList = ref([])
const answerKeyMap = ref(new Map())
const submissionMap = ref(new Map())
const slotNodes = ref(new Map())
const statusNodes = ref(new Map())

// ========== 计算属性 ==========
const blogId = computed(() => {
    const id = route.params.id
    return id ? parseInt(id, 10) : null
})

const currentBlog = computed(() => {
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
function injectQuestionSlots(markdown) {
    const tokenRegex = /(?:【\s*@\s*(\d*)\s*】|\[\s*@\s*(\d*)\s*\])/g
    let autoCounter = 1
    const usedIndices = new Set()
    const questionIds = []
    let slotCount = 0

    const processed = markdown.replace(tokenRegex, (match, id1, id2) => {
        const numericId = (id1 !== undefined) ? id1 : id2
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
        questionIds.push(questionId)
        return `<div class="question-slot" data-question-id="${questionId}"></div>`
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

function renderMarkdownWithSidebar(markdown, isDesktop) {
    if (!isDesktop) {
        const cleaned = markdown.replace(/^---\s*$/gm, '')
        return renderMarkdown(cleaned)
    }

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
            const mainHtml = renderMarkdown(section.h1 + '\n' + mainMd)
            const sidebarHtml = renderMarkdown(sidebarMd)
            html += `
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

    const header = document.createElement('div')
    header.className = 'question-card-header'

    const textarea = document.createElement('textarea')
    textarea.className = 'question-textarea question-textarea-study'
    textarea.rows = 3
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

    const footer = document.createElement('div')
    footer.className = 'question-card-footer question-card-footer-between'

    const { text, cls } = buildStatusPill(submission)
    const status = createPill(text, cls)
    footer.appendChild(status)
    statusNodes.value.set(questionId, status)

    const actionBtn = document.createElement('button')
    actionBtn.type = 'button'
    actionBtn.className = 'question-action-btn'

    if (isReviewed) {
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
    } else {
        actionBtn.innerHTML = '<i class="fas fa-paper-plane"></i><span>提交已做</span>'
        actionBtn.addEventListener('click', async function () {
            this.disabled = true
            const ok = await persistStudyAnswers({ silent: false, targetQuestionId: questionId })
            this.disabled = false
            if (ok) {
                await refreshSubmissionStatus()
            }
        })
    }

    footer.appendChild(actionBtn)
    wrapper.append(header, textarea, footer)
    slotNodes.value.set(questionId, { wrapper, textarea, status, mode: 'study' })

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
            if (toast.parentNode) toast.remove()
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

// ========== 初始化题目槽位 ==========
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
    answerKeyMap.value = new Map()
    submissionMap.value = new Map()
    slotNodes.value = new Map()
    statusNodes.value = new Map()
    contentVersion.value++
    isSubmitting.value = false
    fabStatusClass.value = ''
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

        const blog = currentBlog.value
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
        const response = await fetch(`/blogs/${blog.path}`)
        if (!response.ok) throw new Error('文件加载失败')

        let content = await response.text()

        // 1. 处理图片嵌入语法
        content = processMarkdown(content)

        // 2. 注入题目占位符
        const slotResult = injectQuestionSlots(content)
        questionCount.value = slotResult.questionCount
        questionIdList.value = slotResult.questionIdList

        // 3. 渲染 Markdown
        const isDesktop = window.innerWidth >= 1024
        renderedContent.value = renderMarkdownWithSidebar(slotResult.markdown, isDesktop)
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

        // 7. 初始化题目槽位
        await nextTick()
        initializeQuestionSlots()

        // 8. 观察图片嵌入
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
    await blogStore.loadBlogData()
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

/* 绘制状态指示器 */
.drawing-indicator {
    position: fixed;
    top: 80px;
    right: 20px;
    background: rgba(255, 0, 127, 0.1);
    border: 1px solid rgba(255, 0, 127, 0.3);
    border-radius: 20px;
    padding: 8px 16px;
    font-size: 0.85rem;
    color: #FF007F;
    display: flex;
    align-items: center;
    gap: 8px;
    z-index: 9998;
    backdrop-filter: blur(8px);
    animation: fadeInDown 0.3s ease;
}

@keyframes fadeInDown {
    from {
        opacity: 0;
        transform: translateY(-10px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.drawing-indicator i {
    font-size: 1rem;
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

/* 双栏布局 */
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
    position: relative;
}

.detail-body :deep(.detail-sidebar-column) {
    flex: 1;
    min-width: 0;
    max-width: 320px;
    position: sticky;
    top: 20px;
    background: rgba(255, 255, 255, 0.3);
    border-radius: 16px;
    padding: 16px 20px;
    border: 1px solid rgba(120, 170, 155, 0.15);
    max-height: calc(100vh - 40px);
    overflow-y: auto;
}

/* 题目卡片样式 */
:deep(.question-slot) {
    display: block;
    margin: 18px 0;
}

:deep(.question-card) {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 18px 20px;
    border-radius: 22px;
    background: rgba(255, 255, 255, 0.82);
    border: 1px solid rgba(120, 170, 155, 0.16);
    box-shadow: 0 10px 24px rgba(80, 130, 120, 0.08);
}

:deep(.question-card-header) {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

:deep(.question-textarea) {
    width: 100%;
    resize: vertical;
    min-height: 88px;
    border-radius: 16px;
    border: 1px solid rgba(120, 170, 155, 0.18);
    background: rgba(255, 255, 255, 0.95);
    padding: 14px 16px;
    font-size: 1rem;
    color: #345143;
    outline: none;
    line-height: 1.65;
    transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
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
    gap: 10px;
    flex-wrap: wrap;
}

:deep(.question-card-footer-between) {
    justify-content: space-between;
}

:deep(.question-pill) {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 0 12px;
    min-height: 28px;
    border-radius: 999px;
    background: rgba(120, 170, 155, 0.12);
    color: #4c6b5b;
    font-size: 0.82rem;
    border: 1px solid rgba(120, 170, 155, 0.14);
}

:deep(.question-pill.is-waiting),
:deep(.question-pill.is-muted) {
    background: rgba(216, 222, 219, 0.4);
    color: #6d756e;
}

:deep(.question-pill.is-pending) {
    background: rgba(242, 226, 172, 0.48);
    color: #7b6420;
    border-color: rgba(227, 203, 124, 0.32);
}

:deep(.question-pill.is-correct),
:deep(.question-pill.is-auto) {
    background: rgba(205, 237, 221, 0.75);
    color: #2f7b57;
    border-color: rgba(80, 176, 122, 0.18);
}

:deep(.question-pill.is-partial) {
    background: rgba(255, 234, 188, 0.8);
    color: #a87b19;
    border-color: rgba(227, 188, 90, 0.24);
}

:deep(.question-pill.is-wrong) {
    background: rgba(255, 224, 224, 0.82);
    color: #b65661;
    border-color: rgba(208, 116, 126, 0.24);
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
}
</style>