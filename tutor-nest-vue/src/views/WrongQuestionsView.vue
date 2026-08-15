<!--
  WrongQuestionsView.vue - 错题本
  ----------------------------------------------------------------------------
  功能说明：
    1. 展示当前学生的全部错题（手动添加 + 自动收集）
    2. 题目内容、学科、正确答案不存储在表中，按来源文章动态解析
       （subject ← articles.json series；题干 ← 文章 markdown 的【题干N】标记；
        correct_answer ← article_answer_keys）
    3. 统计、筛选（学科/状态/搜索）、排序、掌握标记、编辑、删除
    4. 手动添加：从文章选题（学科 → 文章 → 题号，实时预览题干）
-->
<template>
    <div class="wq-container">
        <!-- 未登录 -->
        <div v-if="!authStore.isLoggedIn" class="empty-tip">
            请先 <router-link to="/" class="tip-link">登录</router-link> 后查看错题本。
        </div>

        <template v-else>
            <!-- 头部 -->
            <div class="wq-header">
                <h1 class="wq-title">错题本</h1>
                <p class="wq-subtitle">
                    好记性不如烂笔头 —— 在这里整理每一道错题，直到真正掌握。
                </p>
            </div>

            <!-- 统计栏 -->
            <div class="wq-stats">
                <div class="wq-stat-card">
                    <div class="wq-stat-number">{{ stats.total }}</div>
                    <div class="wq-stat-label">总题数</div>
                </div>
                <div class="wq-stat-card is-pending">
                    <div class="wq-stat-number">{{ stats.unmastered }}</div>
                    <div class="wq-stat-label">未掌握</div>
                </div>
                <div class="wq-stat-card is-mastered">
                    <div class="wq-stat-number">{{ stats.mastered }}</div>
                    <div class="wq-stat-label">已掌握</div>
                </div>
            </div>

            <!-- 筛选工具栏 -->
            <div class="wq-toolbar">
                <div class="wq-status-tabs">
                    <button v-for="tab in statusTabs" :key="tab.value" class="wq-tab"
                        :class="{ 'is-active': statusFilter === tab.value }" @click="statusFilter = tab.value">
                        {{ tab.label }}
                    </button>
                    <router-link to="/wrong-training" class="wq-tab wq-tab-training">
                        <i class="fas fa-dumbbell"></i> 错题训练
                    </router-link>
                </div>

                <div class="wq-toolbar-right">
                    <select v-model="subjectFilter" class="wq-select" aria-label="按学科筛选">
                        <option value="">全部学科</option>
                        <option v-for="s in subjectOptions" :key="s" :value="s">{{ s }}</option>
                    </select>

                    <select v-model="sortMode" class="wq-select" aria-label="排序方式">
                        <option value="recent">最近更新</option>
                        <option value="wrong">错误次数</option>
                    </select>

                    <div class="wq-search">
                        <i class="fas fa-search"></i>
                        <input v-model="searchKeyword" type="text" placeholder="搜索题目、答案、错因、标签..." />
                    </div>
                </div>
            </div>

            <!-- 空状态 -->
            <div v-if="filteredQuestions.length === 0" class="empty-state">
                <div v-if="wrongQuestionsStore.questions.length === 0" class="empty-guide">
                    <div class="empty-icon"><i class="fas fa-book-open"></i></div>
                    <p>还没有错题记录。</p>
                    <p class="empty-hint">在文章答题时点击「加入错题本」手动添加；<br>被批阅为「错误」的题目会自动收集到这里。</p>
                </div>
                <div v-else class="empty-filtered">
                    <i class="fas fa-filter"></i>
                    <p>当前筛选条件下没有错题，换个条件试试吧。</p>
                </div>
            </div>

            <!-- 错题卡片列表 -->
            <div v-else ref="listContainer" class="wq-list">
                <div v-for="q in filteredQuestions" :key="q.id" class="wq-card"
                    :class="{ 'is-expanded': expandedId === q.id, 'is-mastered': q.mastered }">
                    <!-- 卡片头部 -->
                    <div class="wq-card-head" @click="toggleExpand(q.id)">
                        <div class="wq-subject-badge" :class="subjectBadgeClass(resolvedOf(q).subject)">
                            {{ resolvedOf(q).subject || '未分类' }}
                        </div>
                        <span class="wq-source-tag" :class="q.is_manual ? 'is-manual' : 'is-auto'">
                            {{ q.is_manual ? '手动' : '自动' }}
                        </span>
                        <span class="wq-status-pill" :class="q.mastered ? 'is-mastered' : 'is-pending'">
                            <i :class="q.mastered ? 'fas fa-check-circle' : 'fas fa-hourglass-half'"></i>
                            {{ q.mastered ? '已掌握' : '未掌握' }}
                        </span>
                        <span class="wq-wrong-count" :title="`共错了 ${q.wrong_count} 次`">
                            <i class="fas fa-times-circle"></i> {{ q.wrong_count }}
                        </span>
                        <span class="wq-expand-icon">
                            <i :class="expandedId === q.id ? 'fas fa-chevron-up' : 'fas fa-chevron-down'"></i>
                        </span>
                    </div>

                    <!-- 题目内容（动态解析，支持图片与数学公式） -->
                    <div class="wq-question-text" @click="toggleExpand(q.id)" v-html="resolvedOf(q).questionText
                        ? renderQuestionText(resolvedOf(q).questionText)
                        : (resolvedReady ? '（无题目内容）' : '加载中…')"></div>

                    <!-- 标签 -->
                    <div v-if="q.tags && q.tags.length" class="wq-tags">
                        <span v-for="tag in q.tags" :key="tag" class="wq-tag"># {{ tag }}</span>
                    </div>

                    <!-- 来源文章链接 -->
                    <div v-if="q.source_blog_id" class="wq-source-link">
                        <i class="fas fa-link"></i>
                        来自《{{ blogTitleOf(q.source_blog_id) }}》第 {{ resolvedOf(q).order ?? q.source_question_id }} 题
                        <router-link :to="`/blog/${q.source_blog_id}`" class="wq-source-jump">
                            跳转原文 <i class="fas fa-arrow-right"></i>
                        </router-link>
                    </div>

                    <!-- 展开详情（支持数学公式） -->
                    <div v-if="expandedId === q.id" class="wq-details">
                        <div class="wq-detail-row">
                            <div class="wq-detail-col">
                                <div class="wq-detail-label is-my"><i class="fas fa-pen"></i> 我的答案</div>
                                <div class="wq-detail-text" v-html="q.my_answer ? safeText(q.my_answer) : '（未填写）'">
                                </div>
                            </div>
                            <div class="wq-detail-col">
                                <div class="wq-detail-label is-correct"><i class="fas fa-check"></i> 正确答案</div>
                                <div class="wq-detail-text"
                                    v-html="resolvedOf(q).correctAnswer ? safeText(resolvedOf(q).correctAnswer) : '（未填写）'">
                                </div>
                            </div>
                        </div>
                        <div v-if="q.wrong_reason" class="wq-detail-block">
                            <div class="wq-detail-label is-reason"><i class="fas fa-lightbulb"></i> 错因分析</div>
                            <div class="wq-detail-text" v-html="safeText(q.wrong_reason)"></div>
                        </div>
                        <div v-if="q.note" class="wq-detail-block">
                            <div class="wq-detail-label is-note"><i class="fas fa-sticky-note"></i> 笔记</div>
                            <div class="wq-detail-text" v-html="safeText(q.note)"></div>
                        </div>
                    </div>

                    <!-- 卡片操作栏 -->
                    <div class="wq-card-actions">
                        <span class="wq-date">{{ formatDate(q.updated_at) }}</span>
                        <div class="wq-action-btns">
                            <button class="wq-action-btn" :class="q.mastered ? 'is-unmaster' : 'is-master'"
                                @click="handleToggleMastered(q)" :disabled="busyIds.has(q.id)">
                                <i :class="q.mastered ? 'fas fa-undo' : 'fas fa-check-circle'"></i>
                                {{ q.mastered ? '取消掌握' : '标记掌握' }}
                            </button>
                            <button class="wq-action-btn is-edit" @click="openEditModal(q)">
                                <i class="fas fa-edit"></i> 编辑
                            </button>
                            <button class="wq-action-btn is-delete" @click="requestDelete(q)">
                                <i class="fas fa-trash-alt"></i> 删除
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </template>

        <!-- 编辑错题模态框（添加错题已移至文章答题区） -->
        <div v-if="modalVisible" class="modal-overlay" @click.self="closeModal">
            <div class="modal-panel" ref="modalBody">
                <div class="modal-header">
                    <span class="modal-title">
                        <i class="fas fa-edit"></i> 编辑错题
                    </span>
                    <button class="modal-close" @click="closeModal"><i class="fas fa-times"></i></button>
                </div>

                <div class="modal-body">
                    <!-- 题干只读预览 -->
                    <div class="form-row">
                        <label class="form-label">题干（来自文章，不可修改）</label>
                        <div class="wq-preview" v-html="renderQuestionText(editPreviewText)"></div>
                    </div>

                    <div class="form-row">
                        <label class="form-label">我的答案</label>
                        <textarea v-model="form.my_answer" class="form-textarea" rows="2"
                            placeholder="你当时的作答"></textarea>
                    </div>

                    <div class="form-row">
                        <label class="form-label">错因分析</label>
                        <textarea v-model="form.wrong_reason" class="form-textarea" rows="2"
                            placeholder="为什么会做错？知识点没掌握、审题不清、粗心…"></textarea>
                    </div>

                    <div class="form-row">
                        <label class="form-label">知识点标签</label>
                        <input v-model="form.tagsInput" type="text" class="form-input" placeholder="用逗号分隔，如：氧化还原, 配平" />
                    </div>

                    <div class="form-row">
                        <label class="form-label">笔记</label>
                        <textarea v-model="form.note" class="form-textarea" rows="2"
                            placeholder="易错点提醒、总结、学习心得…"></textarea>
                    </div>
                </div>

                <div class="modal-footer">
                    <button class="modal-btn is-cancel" @click="closeModal">取消</button>
                    <button class="modal-btn is-save" :disabled="saving" @click="submitForm">
                        <i class="fas" :class="saving ? 'fa-spinner fa-spin' : 'fa-save'"></i>
                        {{ saving ? '保存中...' : '保存' }}
                    </button>
                </div>
            </div>
        </div>

        <!-- 删除确认模态框 -->
        <div v-if="confirmVisible" class="modal-overlay" @click.self="cancelDelete">
            <div class="modal-panel modal-panel-small">
                <div class="modal-header">
                    <span class="modal-title"><i class="fas fa-exclamation-triangle"></i> 删除错题</span>
                    <button class="modal-close" @click="cancelDelete"><i class="fas fa-times"></i></button>
                </div>
                <div class="confirm-body">
                    <p>{{ deletingItem?.is_manual
                        ? '确定要删除这道错题吗？删除后不可恢复。'
                        : '这道错题来自自动收集，删除后若再次做错会重新收集。确定删除吗？' }}</p>
                </div>
                <div class="modal-footer">
                    <button class="modal-btn is-cancel" @click="cancelDelete">取消</button>
                    <button class="modal-btn is-danger" :disabled="deleting" @click="confirmDelete">
                        <i class="fas" :class="deleting ? 'fa-spinner fa-spin' : 'fa-trash-alt'"></i>
                        {{ deleting ? '删除中...' : '确认删除' }}
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { useWrongQuestionsStore } from '@/stores/wrongQuestionsStore'
import { useKatex } from '@/composables/useKatex'
import { useImageEmbed } from '@/composables/useImageEmbed'
import { supabase } from '@/utils/supabase'
import { resolveQuestionText, resolveQuestionOrder, renderMarkdownTable } from '@/utils/questionText'

const authStore = useAuthStore()
const blogStore = useArticleStore()
const wrongQuestionsStore = useWrongQuestionsStore()
const { renderMath } = useKatex()
const { processMarkdown, setBasePath } = useImageEmbed()
setBasePath('articles/图片/')

// ========== 动态解析（题干/学科/正确答案） ==========
// 错题表只存来源（source_blog_id + source_question_id），
// 展示时从文章 markdown 与答案表动态解析
const resolvedMap = ref(new Map())   // 错题 id → { subject, questionText, correctAnswer }
const resolvedReady = ref(false)     // 动态解析是否完成（用于区分「加载中」与「无内容」）
const articleCache = new Map()       // blogId → markdown（缓存）
const listContainer = ref(null)
const modalBody = ref(null)

function resolvedOf(q) {
    return resolvedMap.value.get(q.id) || {}
}

async function loadArticle(blogId) {
    const key = String(blogId)
    if (articleCache.has(key)) return articleCache.get(key)
    const blog = blogStore.blogData.find(b => Number(b.id) === Number(blogId))
    let md = null
    if (blog) {
        try {
            const res = await fetch(`${import.meta.env.BASE_URL}articles/${blog.path}`)
            if (res.ok) md = await res.text()
        } catch {
            md = null
        }
    }
    articleCache.set(key, md)
    return md
}

async function loadAnswerKeys(blogIds) {
    if (!blogIds.length) return new Map()
    const { data, error } = await supabase
        .from('article_answer_keys')
        .select('blog_id, question_id, answer_text')
        .in('blog_id', blogIds)

    if (error) {
        console.error('加载答案数据失败:', error)
        return new Map()
    }
    const map = new Map()
        ; (data || []).forEach(k => map.set(`${k.blog_id}-${String(k.question_id)}`, k.answer_text || ''))
    return map
}

// 解析全部错题的动态数据
async function resolveAll() {
    resolvedReady.value = false
    const list = wrongQuestionsStore.questions
    const blogIds = [...new Set(list.map(q => q.source_blog_id).filter(b => b != null))]

    await Promise.all(blogIds.map(loadArticle))
    const answerMap = await loadAnswerKeys(blogIds)

    const map = new Map()
    list.forEach(q => {
        let subject = ''
        let questionText = ''
        let correctAnswer = ''
        let order = null
        if (q.source_blog_id != null) {
            const blog = blogStore.blogData.find(b => Number(b.id) === Number(q.source_blog_id))
            subject = blog?.series || ''
            const md = articleCache.get(String(q.source_blog_id))
            if (md) {
                questionText = resolveQuestionText(md, q.source_question_id)
                order = resolveQuestionOrder(md, q.source_question_id)
            }
            correctAnswer = answerMap.get(`${q.source_blog_id}-${String(q.source_question_id)}`) || ''
        }
        map.set(q.id, { subject, questionText, correctAnswer, order })
    })
    resolvedMap.value = map
    resolvedReady.value = true
}

// ========== 渲染工具 ==========
/** 题干内容渲染：markdown 表格 → <table>，图片嵌入（![[图片]] → <img>），换行转 <br>，$...$ 公式由 KaTeX 渲染 */
function renderQuestionText(text) {
    return processMarkdown(renderMarkdownTable(text)).replace(/\n/g, '<br>')
}

/** 详情文本安全渲染：转义 HTML 防注入，换行转 <br>，$...$ 公式由 KaTeX 渲染 */
function safeText(text) {
    return String(text ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
        .replace(/\n/g, '<br>')
}

// 列表/模态框内容变化后渲染数学公式
function scheduleFormulaRender() {
    nextTick(() => {
        if (listContainer.value) renderMath(listContainer.value)
        if (modalBody.value) renderMath(modalBody.value)
    })
}

// ========== 筛选状态 ==========
const statusTabs = [
    { value: 'all', label: '全部' },
    { value: 'unmastered', label: '未掌握' },
    { value: 'mastered', label: '已掌握' }
]

const statusFilter = ref('all')
const subjectFilter = ref('')
const sortMode = ref('recent')
const searchKeyword = ref('')
const expandedId = ref(null)
const busyIds = ref(new Set())

// ========== 统计 ==========
const stats = computed(() => {
    const all = wrongQuestionsStore.questions
    const mastered = all.filter(q => q.mastered).length
    return {
        total: all.length,
        unmastered: all.length - mastered,
        mastered
    }
})

// ========== 学科选项 ==========
const subjectOptions = computed(() => {
    const set = new Set()
    resolvedMap.value.forEach(rd => {
        if (rd.subject) set.add(rd.subject)
    })
    blogStore.blogData.forEach(b => {
        if (b.series) set.add(b.series)
    })
    return Array.from(set).sort((a, b) => a.localeCompare(b))
})

// ========== 筛选与排序 ==========
const filteredQuestions = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase()

    let list = wrongQuestionsStore.questions.filter(q => {
        // 状态筛选
        if (statusFilter.value === 'unmastered' && q.mastered) return false
        if (statusFilter.value === 'mastered' && !q.mastered) return false
        // 学科筛选
        const subject = resolvedOf(q).subject
        if (subjectFilter.value && subject !== subjectFilter.value) return false
        // 关键词搜索
        if (keyword) {
            const haystack = [
                resolvedOf(q).questionText,
                resolvedOf(q).correctAnswer,
                q.my_answer,
                q.wrong_reason,
                q.note,
                (q.tags || []).join(' ')
            ].join(' ').toLowerCase()
            if (!haystack.includes(keyword)) return false
        }
        return true
    })

    // 排序
    if (sortMode.value === 'wrong') {
        list = [...list].sort((a, b) => (b.wrong_count || 0) - (a.wrong_count || 0))
    } else {
        list = [...list].sort((a, b) =>
            new Date(b.updated_at || 0) - new Date(a.updated_at || 0))
    }
    return list
})

// ========== 数据加载 ==========
async function loadData() {
    await blogStore.loadArticleData()
    const studentId = wrongQuestionsStore.getStudentId(authStore.currentUser)
    await wrongQuestionsStore.fetchQuestions(studentId)
    await resolveAll()
}

// ========== 卡片交互 ==========
function toggleExpand(id) {
    expandedId.value = expandedId.value === id ? null : id
}

function subjectBadgeClass(subject) {
    if (subject === '化学') return 'is-chemistry'
    if (subject === '英语') return 'is-english'
    return 'is-other'
}

function blogTitleOf(blogId) {
    const blog = blogStore.blogData.find(b => Number(b.id) === Number(blogId))
    return blog?.title || '未知文章'
}

function formatDate(dateStr) {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    if (isNaN(d)) return ''
    const pad = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// ========== 掌握切换 ==========
async function handleToggleMastered(q) {
    if (busyIds.value.has(q.id)) return
    busyIds.value.add(q.id)
    try {
        await wrongQuestionsStore.toggleMastered(q.id, !q.mastered)
        showToast(q.mastered ? '已取消掌握' : '已标记掌握，继续保持！', 'success')
    } catch (e) {
        showToast(e.message || '操作失败', 'error')
    } finally {
        busyIds.value.delete(q.id)
    }
}

// ========== 删除 ==========
const confirmVisible = ref(false)
const deletingItem = ref(null)
const deleting = ref(false)

function requestDelete(q) {
    deletingItem.value = q
    confirmVisible.value = true
}

function cancelDelete() {
    if (deleting.value) return
    confirmVisible.value = false
    deletingItem.value = null
}

async function confirmDelete() {
    if (!deletingItem.value || deleting.value) return
    deleting.value = true
    try {
        await wrongQuestionsStore.deleteQuestion(deletingItem.value.id, deletingItem.value.is_manual)
        showToast('已删除', 'success')
        confirmVisible.value = false
        deletingItem.value = null
    } catch (e) {
        showToast(e.message || '删除失败', 'error')
    } finally {
        deleting.value = false
    }
}

// ========== 编辑模态框 ==========
const modalVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)

const form = ref(createEmptyForm())

function createEmptyForm() {
    return {
        my_answer: '',
        wrong_reason: '',
        tagsInput: '',
        note: ''
    }
}

// 当前错题的题干预览（来自文章，只读）
const editPreviewText = computed(() => {
    if (!editingId.value) return ''
    return resolvedOf({ id: editingId.value }).questionText || ''
})

function openEditModal(q) {
    editingId.value = q.id
    form.value = {
        my_answer: q.my_answer || '',
        wrong_reason: q.wrong_reason || '',
        tagsInput: (q.tags || []).join(', '),
        note: q.note || ''
    }
    modalVisible.value = true
}

function closeModal() {
    if (saving.value) return
    modalVisible.value = false
}

async function submitForm() {
    if (saving.value) return

    const payload = {
        my_answer: form.value.my_answer.trim(),
        wrong_reason: form.value.wrong_reason.trim(),
        note: form.value.note.trim(),
        tags: form.value.tagsInput
            .split(/[,，]/)
            .map(t => t.trim())
            .filter(Boolean)
    }

    saving.value = true
    try {
        await wrongQuestionsStore.updateQuestion(editingId.value, payload)
        showToast('修改成功', 'success')
        modalVisible.value = false
        await resolveAll()
    } catch (e) {
        showToast(e.message || '保存失败，请稍后重试', 'error')
    } finally {
        saving.value = false
    }
}

// ========== Toast ==========
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

// ========== 生命周期 ==========
onMounted(async () => {
    if (authStore.isLoggedIn) {
        await loadData()
        scheduleFormulaRender()
    }
})

// 登录状态变化时重新加载
watch(() => authStore.isLoggedIn, async (loggedIn) => {
    if (loggedIn) {
        await loadData()
        scheduleFormulaRender()
    }
})

// 列表数据、展开状态、模态框内容变化后重新渲染数学公式
watch([filteredQuestions, expandedId, modalVisible], () => {
    scheduleFormulaRender()
})
</script>

<style scoped>
.wq-container {
    max-width: 1300px;
    min-width: 800px;
    margin: 0 auto;
    padding: 30px 32px 100px;
    width: 100%;
}

.empty-tip {
    text-align: center;
    padding: 60px 20px;
    color: var(--gray);
    background: rgba(255, 255, 255, 0.3);
    border-radius: 24px;
}

.tip-link {
    color: var(--teal-dark);
}

/* ========== 头部 ========== */
.wq-header {
    margin-bottom: 24px;
}

.wq-title {
    font-size: 2.2rem;
    font-weight: 700;
    color: #2d4a3a;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 12px;
}

.wq-title::before {
    content: '\f7e6';
    font-family: 'Font Awesome 6 Free';
    font-weight: 900;
    font-size: 1.8rem;
    color: #d9ba4b;
}

.wq-subtitle {
    color: var(--gray);
    font-size: 0.95rem;
}

/* ========== 统计栏 ========== */
.wq-stats {
    display: flex;
    gap: 20px;
    margin-bottom: 20px;
}

.wq-stat-card {
    flex: 1;
    background: rgba(255, 255, 255, 0.45);
    backdrop-filter: blur(8px);
    border-radius: 18px;
    padding: 18px 24px;
    text-align: center;
    border: 1px solid rgba(255, 255, 255, 0.5);
    box-shadow: 0 4px 16px var(--shadow);
    transition: transform 0.25s ease;
}

.wq-stat-card:hover {
    transform: translateY(-2px);
}

.wq-stat-number {
    font-size: 2.1rem;
    font-weight: 700;
    color: #2d4a3a;
    line-height: 1.2;
}

.wq-stat-card.is-pending .wq-stat-number {
    color: #b6862a;
}

.wq-stat-card.is-mastered .wq-stat-number {
    color: #2f7b57;
}

.wq-stat-label {
    font-size: 0.9rem;
    color: var(--gray);
    margin-top: 4px;
}

/* ========== 工具栏 ========== */
.wq-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
    margin-bottom: 20px;
    padding: 14px 18px;
    background: rgba(255, 255, 255, 0.3);
    backdrop-filter: blur(8px);
    border-radius: 18px;
    border: 1px solid rgba(255, 255, 255, 0.4);
}

.wq-status-tabs {
    display: flex;
    gap: 8px;
}

.wq-tab {
    padding: 6px 18px;
    border-radius: 30px;
    border: 1px solid transparent;
    background: rgba(255, 255, 255, 0.3);
    color: var(--gray);
    font-size: 0.9rem;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
}

.wq-tab:hover {
    background: rgba(255, 255, 255, 0.6);
    color: #2d4a3a;
}

.wq-tab.is-active {
    background: rgba(91, 168, 164, 0.2);
    color: var(--teal-dark);
    border-color: var(--teal-dark);
    font-weight: 600;
}

/* 错题训练入口按钮 */
.wq-tab-training {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: rgba(217, 186, 75, 0.15);
    color: #8a6d1a;
    border-color: rgba(217, 186, 75, 0.25);
    text-decoration: none;
}

.wq-tab-training:hover {
    background: rgba(217, 186, 75, 0.28);
    color: #705d13;
}

.wq-toolbar-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

.wq-select {
    padding: 8px 12px;
    border-radius: 12px;
    border: 1px solid rgba(120, 170, 155, 0.25);
    background: rgba(255, 255, 255, 0.7);
    color: #345143;
    font-size: 0.9rem;
    outline: none;
    cursor: pointer;
    font-family: inherit;
    transition: border-color 0.2s;
}

.wq-select:focus {
    border-color: var(--teal-dark);
}

.wq-search {
    position: relative;
    display: flex;
    align-items: center;
}

.wq-search i {
    position: absolute;
    left: 14px;
    color: rgba(90, 122, 106, 0.5);
    font-size: 0.85rem;
}

.wq-search input {
    padding: 8px 14px 8px 36px;
    border-radius: 30px;
    border: 1px solid rgba(120, 170, 155, 0.25);
    background: rgba(255, 255, 255, 0.7);
    color: #345143;
    font-size: 0.9rem;
    width: 240px;
    outline: none;
    transition: border-color 0.2s, box-shadow 0.2s;
    font-family: inherit;
}

.wq-search input:focus {
    border-color: var(--teal-dark);
    box-shadow: 0 0 0 3px rgba(91, 168, 164, 0.12);
}

/* ========== 空状态 ========== */
.empty-state {
    text-align: center;
    padding: 40px 20px;
    background: rgba(255, 255, 255, 0.3);
    border-radius: 24px;
}

.empty-guide .empty-icon {
    font-size: 3.5rem;
    color: rgba(217, 186, 75, 0.5);
    margin-bottom: 14px;
}

.empty-guide p {
    color: #3d5a4a;
    font-size: 1.05rem;
}

.empty-guide .empty-hint {
    color: var(--gray);
    font-size: 0.9rem;
    margin-top: 8px;
    line-height: 1.8;
}

.empty-filtered {
    color: var(--gray);
    font-size: 0.95rem;
}

.empty-filtered i {
    font-size: 2rem;
    margin-bottom: 10px;
    color: rgba(120, 170, 155, 0.4);
}

/* ========== 错题卡片 ========== */
.wq-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.wq-card {
    background: rgba(255, 255, 255, 0.55);
    backdrop-filter: blur(8px);
    border-radius: 20px;
    padding: 16px 22px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    border-left: 6px solid #d9ba4b;
    box-shadow: 0 6px 24px var(--shadow);
    transition: box-shadow 0.3s ease, transform 0.3s ease;
}

.wq-card:hover {
    box-shadow: 0 10px 32px rgba(80, 130, 120, 0.12);
}

.wq-card.is-expanded {
    box-shadow: 0 12px 36px rgba(80, 130, 120, 0.14);
}

.wq-card.is-mastered {
    border-left-color: #8fcfb8;
}

/* 卡片头部 */
.wq-card-head {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
    user-select: none;
    flex-wrap: wrap;
}

.wq-subject-badge {
    padding: 2px 14px;
    border-radius: 999px;
    font-size: 0.8rem;
    font-weight: 600;
    color: #2d4a3a;
}

.wq-subject-badge.is-chemistry {
    background: rgba(91, 168, 164, 0.18);
    color: #2f6a66;
}

.wq-subject-badge.is-english {
    background: rgba(143, 207, 184, 0.25);
    color: #2f6b50;
}

.wq-subject-badge.is-other {
    background: rgba(120, 170, 155, 0.12);
    color: #4c6b5b;
}

.wq-source-tag {
    padding: 1px 10px;
    border-radius: 999px;
    font-size: 0.72rem;
    font-weight: 600;
}

.wq-source-tag.is-manual {
    background: rgba(120, 170, 155, 0.1);
    color: #5a7a6a;
}

.wq-source-tag.is-auto {
    background: rgba(242, 226, 172, 0.45);
    color: #7b6420;
}

.wq-status-pill {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 2px 12px;
    border-radius: 999px;
    font-size: 0.78rem;
    font-weight: 600;
}

.wq-status-pill.is-pending {
    background: rgba(255, 224, 224, 0.7);
    color: #b65661;
}

.wq-status-pill.is-mastered {
    background: rgba(205, 237, 221, 0.75);
    color: #2f7b57;
}

.wq-wrong-count {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 0.78rem;
    color: #a87b19;
    margin-left: auto;
}

.wq-expand-icon {
    color: rgba(90, 122, 106, 0.5);
    font-size: 0.8rem;
}

/* 题目内容（支持图片与数学公式） */
.wq-question-text {
    margin-top: 12px;
    font-size: 1.05rem;
    line-height: 1.7;
    color: #2d4a3a;
    word-break: break-word;
    cursor: pointer;
}

/* 题干内容为 v-html 注入，须用 :deep() 才能命中 */
.wq-question-text :deep(img) {
    max-width: 100%;
    border-radius: 8px;
    margin: 4px 0;
}

/* 题干中的表格 */
.wq-question-text :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 10px 0;
    font-size: 0.95em;
}

.wq-question-text :deep(th),
.wq-question-text :deep(td) {
    border: 1px solid rgba(100, 145, 128, 0.5);
    padding: 6px 12px;
    text-align: left;
    vertical-align: middle;
}

.wq-question-text :deep(th) {
    background: rgba(91, 168, 164, 0.15);
    font-weight: 600;
}

/* 标签 */
.wq-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
}

.wq-tag {
    padding: 2px 12px;
    border-radius: 999px;
    background: rgba(91, 168, 164, 0.08);
    color: #3a6a60;
    font-size: 0.8rem;
    border: 1px solid rgba(91, 168, 164, 0.15);
}

/* 来源链接 */
.wq-source-link {
    margin-top: 10px;
    font-size: 0.85rem;
    color: var(--gray);
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
}

.wq-source-jump {
    margin-left: auto;
    color: var(--teal-dark);
    font-weight: 600;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    transition: gap 0.2s ease;
}

.wq-source-jump:hover {
    gap: 8px;
}

/* 展开详情 */
.wq-details {
    margin-top: 14px;
    border-top: 1px dashed rgba(120, 170, 155, 0.2);
    padding-top: 14px;
    animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
    from {
        opacity: 0;
        transform: translateY(6px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.wq-detail-row {
    display: flex;
    gap: 16px;
}

.wq-detail-col {
    flex: 1;
    min-width: 0;
}

.wq-detail-block {
    margin-top: 12px;
}

.wq-detail-label {
    font-size: 0.82rem;
    font-weight: 600;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
}

.wq-detail-label.is-my {
    color: #b6862a;
}

.wq-detail-label.is-correct {
    color: #2f7b57;
}

.wq-detail-label.is-reason {
    color: #b65661;
}

.wq-detail-label.is-note {
    color: #4a6da8;
}

.wq-detail-text {
    font-size: 0.95rem;
    color: #345143;
    background: rgba(245, 248, 246, 0.7);
    border-radius: 12px;
    padding: 10px 14px;
    word-break: break-word;
    line-height: 1.7;
}

/* 操作栏 */
.wq-card-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 14px;
    flex-wrap: wrap;
}

.wq-date {
    font-size: 0.78rem;
    color: rgba(90, 122, 106, 0.6);
}

.wq-action-btns {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}

.wq-action-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 16px;
    border-radius: 999px;
    border: 1px solid transparent;
    font-size: 0.85rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
}

.wq-action-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.wq-action-btn.is-master {
    background: rgba(205, 237, 221, 0.55);
    color: #2f7b57;
    border-color: rgba(80, 176, 122, 0.2);
}

.wq-action-btn.is-master:hover {
    background: rgba(205, 237, 221, 0.85);
}

.wq-action-btn.is-unmaster {
    background: rgba(240, 244, 242, 0.6);
    color: #6d7a72;
    border-color: rgba(120, 170, 155, 0.2);
}

.wq-action-btn.is-unmaster:hover {
    background: rgba(240, 244, 242, 0.9);
}

.wq-action-btn.is-edit {
    background: rgba(255, 251, 230, 0.7);
    color: #8a6d1a;
    border-color: rgba(217, 186, 75, 0.25);
}

.wq-action-btn.is-edit:hover {
    background: rgba(255, 248, 214, 0.95);
}

.wq-action-btn.is-delete {
    background: rgba(255, 235, 235, 0.6);
    color: #b65661;
    border-color: rgba(208, 116, 126, 0.2);
}

.wq-action-btn.is-delete:hover {
    background: rgba(255, 224, 224, 0.9);
}

/* ========== 模态框 ========== */
.modal-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.35);
    z-index: 2000;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
    animation: fadeInOverlay 0.25s ease;
}

@keyframes fadeInOverlay {
    from {
        opacity: 0;
    }

    to {
        opacity: 1;
    }
}

.modal-panel {
    width: 100%;
    max-width: 640px;
    max-height: 90vh;
    overflow-y: auto;
    background: #fbfaf6;
    border-radius: 24px;
    box-shadow: 0 24px 80px rgba(0, 0, 0, 0.25);
    animation: fadeInPanel 0.3s ease;
}

.modal-panel-small {
    max-width: 440px;
}

@keyframes fadeInPanel {
    from {
        opacity: 0;
        transform: translateY(20px) scale(0.97);
    }

    to {
        opacity: 1;
        transform: translateY(0) scale(1);
    }
}

.modal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px 24px 14px;
    border-bottom: 1px solid rgba(120, 170, 155, 0.15);
    position: sticky;
    top: 0;
    background: #fbfaf6;
    border-radius: 24px 24px 0 0;
    z-index: 1;
}

.modal-title {
    font-size: 1.2rem;
    font-weight: 700;
    color: #2d4a3a;
    display: flex;
    align-items: center;
    gap: 10px;
}

.modal-title i {
    color: #d9ba4b;
}

.modal-close {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    border: none;
    background: rgba(120, 170, 155, 0.08);
    color: #4c6b5b;
    cursor: pointer;
    font-size: 1rem;
    transition: background 0.2s ease;
}

.modal-close:hover {
    background: rgba(120, 170, 155, 0.2);
}

.modal-body {
    padding: 20px 24px;
}

.form-row {
    margin-bottom: 16px;
}

.form-label {
    display: block;
    font-size: 0.9rem;
    font-weight: 600;
    color: #345143;
    margin-bottom: 6px;
}

.form-required {
    color: #d57587;
}

.form-input,
.form-select,
.form-textarea {
    width: 100%;
    padding: 10px 14px;
    border-radius: 12px;
    border: 1px solid rgba(120, 170, 155, 0.25);
    background: rgba(255, 255, 255, 0.9);
    color: #345143;
    font-size: 0.95rem;
    outline: none;
    transition: border-color 0.2s, box-shadow 0.2s;
    font-family: inherit;
    line-height: 1.6;
}

.form-textarea {
    resize: vertical;
    min-height: 60px;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
    border-color: rgba(224, 199, 106, 0.8);
    box-shadow: 0 0 0 4px rgba(243, 227, 162, 0.25);
}

/* 题干预览 */
.wq-preview {
    padding: 10px 14px;
    background: rgba(245, 248, 246, 0.7);
    border-radius: 12px;
    border: 1px solid rgba(120, 170, 155, 0.15);
    font-size: 0.95rem;
    color: #345143;
    line-height: 1.7;
    word-break: break-word;
    max-height: 200px;
    overflow-y: auto;
}

/* 题干预览内容为 v-html 注入，须用 :deep() 才能命中 */
.wq-preview :deep(img) {
    max-width: 100%;
    border-radius: 8px;
    margin: 4px 0;
}

/* 题干预览中的表格 */
.wq-preview :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 8px 0;
    font-size: 0.92em;
}

.wq-preview :deep(th),
.wq-preview :deep(td) {
    border: 1px solid rgba(100, 145, 128, 0.5);
    padding: 5px 10px;
    text-align: left;
    vertical-align: middle;
}

.wq-preview :deep(th) {
    background: rgba(91, 168, 164, 0.15);
    font-weight: 600;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    padding: 0 24px 20px;
}

.modal-btn {
    padding: 9px 26px;
    border-radius: 30px;
    border: none;
    font-size: 0.95rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
    display: inline-flex;
    align-items: center;
    gap: 7px;
}

.modal-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.modal-btn.is-cancel {
    background: rgba(120, 170, 155, 0.1);
    color: #4c6b5b;
}

.modal-btn.is-cancel:hover {
    background: rgba(120, 170, 155, 0.2);
}

.modal-btn.is-save {
    background: linear-gradient(135deg, rgba(255, 248, 214, 0.98), rgba(243, 227, 162, 0.92));
    color: #705d13;
    box-shadow: 0 6px 16px rgba(217, 186, 75, 0.3);
}

.modal-btn.is-save:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 22px rgba(217, 186, 75, 0.4);
}

.modal-btn.is-danger {
    background: linear-gradient(135deg, #f08a8a, #e0606a);
    color: white;
    box-shadow: 0 6px 16px rgba(224, 96, 106, 0.3);
}

.modal-btn.is-danger:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 22px rgba(224, 96, 106, 0.4);
}

.confirm-body {
    padding: 24px;
    color: #345143;
    line-height: 1.7;
    font-size: 0.98rem;
}

/* Toast 弹窗（与文章页保持一致） */
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

/* ========== 响应式 ========== */
@media (min-width: 1024px) {
    .wq-container {
        padding: 50px 60px 120px;
    }

    .wq-title {
        font-size: 2.6rem;
    }
}

@media (min-width: 641px) and (max-width: 1023px) {
    .wq-container {
        padding: 30px 28px 70px;
        min-width: auto;
    }

    .wq-search input {
        width: 180px;
    }
}

@media (max-width: 640px) {
    .wq-container {
        padding: 16px 14px 90px;
        min-width: auto;
    }

    .wq-title {
        font-size: 1.6rem;
    }

    .wq-stats {
        gap: 10px;
    }

    .wq-stat-card {
        padding: 14px 8px;
    }

    .wq-stat-number {
        font-size: 1.6rem;
    }

    .wq-toolbar {
        flex-direction: column;
        align-items: stretch;
        gap: 12px;
    }

    .wq-search input {
        width: 100%;
    }

    .wq-card {
        padding: 14px 16px;
    }

    .wq-detail-row {
        flex-direction: column;
        gap: 10px;
    }

    .wq-card-actions {
        flex-direction: column;
        align-items: flex-start;
    }

}
</style>
