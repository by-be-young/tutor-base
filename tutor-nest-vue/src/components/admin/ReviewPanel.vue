<!-- src/components/admin/ReviewPanel.vue -->
<template>
    <div class="panel review-panel">
        <header class="panel-head review-head">
            <div class="review-head__copy">
                <h2 class="panel-title">
                    <i aria-hidden="true" class="fas fa-pen-nib"></i>
                    批阅中心
                </h2>
                <p class="text-sm text-muted">查看学员作答、对照参考答案，一键完成三态批阅。</p>
            </div>
            <div class="review-head__chips">
                <span class="chip chip--cyan">
                    <i aria-hidden="true" class="fas fa-user-graduate"></i>
                    当前学员 · {{ currentStudentName }}
                </span>
                <span v-if="selectedStudentId" class="chip chip--lime">
                    <i aria-hidden="true" class="fas fa-shield-halved"></i>
                    已授权 {{ grantedArticleCount }} 篇
                </span>
            </div>
        </header>

        <div class="panel-body review-body-wrap">
            <div class="stat-grid review-stats">
                <div class="stat-card stat-card--amber">
                    <div class="stat-value">{{ pendingArticleCount }}</div>
                    <div class="stat-label">待批阅文章</div>
                    <div class="stat-sub">学员已提交待处理</div>
                </div>
                <div class="stat-card stat-card--coral">
                    <div class="stat-value">{{ pendingQuestionCount }}</div>
                    <div class="stat-label">待批阅题目</div>
                    <div class="stat-sub">逐题批阅后自动递减</div>
                </div>
                <div class="stat-card stat-card--lime">
                    <div class="stat-value">{{ reviewedArticleCount }}</div>
                    <div class="stat-label">已批阅文章</div>
                    <div class="stat-sub">批阅结果已归档</div>
                </div>
                <div class="stat-card stat-card--cyan">
                    <div class="stat-value">{{ submittedArticleCount }}</div>
                    <div class="stat-label">已提交文章</div>
                    <div class="stat-sub">含待批阅与已批阅</div>
                </div>
            </div>

            <div class="review-subjects" role="group" aria-label="学科筛选">
                <button v-for="item in subjectStats" :key="item.subject" type="button" class="review-subject"
                    :class="[`is-tone-${item.tone}`, { 'is-active': currentSubject === item.subject }]"
                    :aria-pressed="currentSubject === item.subject" @click="selectSubject(item.subject)">
                    <i aria-hidden="true" class="fas fa-book-open"></i>
                    {{ item.subject }}
                    <span class="review-subject__count">{{ item.pending }} 待批</span>
                </button>
            </div>

            <div class="review-toolbar">
                <div class="seg" role="group" aria-label="批阅状态筛选">
                    <button v-for="filter in filters" :key="filter.value" type="button" class="seg__btn"
                        :class="{ 'is-active': activeFilter === filter.value }"
                        :aria-pressed="activeFilter === filter.value" @click="activeFilter = filter.value">
                        {{ filter.label }}
                        <span class="seg__count">{{ filter.count }}</span>
                    </button>
                </div>
                <div class="input-wrap review-search">
                    <i aria-hidden="true" class="fas fa-magnifying-glass"></i>
                    <input v-model="keyword" class="input" type="search" placeholder="搜索文章标题"
                        aria-label="搜索文章标题" />
                </div>
            </div>

            <p v-if="progressError" class="field-error review-error">
                <i aria-hidden="true" class="fas fa-circle-exclamation"></i> {{ progressError }}
            </p>

            <div class="review-layout">
                <section class="review-list" aria-label="批阅文章列表">
                    <div v-if="loadingProgress" class="review-skeletons" aria-hidden="true">
                        <div class="skeleton review-skeleton"></div>
                        <div class="skeleton review-skeleton"></div>
                        <div class="skeleton review-skeleton"></div>
                    </div>

                    <EmptyState v-else-if="!selectedStudentId" icon="fas fa-user-graduate" title="尚未选择学员"
                        text="在顶部切换学员后，即可查看他的待批阅内容。" />

                    <EmptyState v-else-if="grantedArticleCount === 0" icon="fas fa-shield-halved"
                        title="该学员尚未开通内容授权"
                        text="请到「权限管理」为该学员开通文章权限后，再回到批阅中心查看作答。" />

                    <EmptyState v-else-if="!currentSubject" icon="fas fa-folder-open" title="未选择学科"
                        text="选择一个学科开始批阅。" />

                    <EmptyState v-else-if="filteredArticles.length === 0" icon="fas fa-mug-hot"
                        title="没有符合条件的文章" text="切换筛选条件，或等待学员提交新的作答。" />

                    <div v-else class="review-tree">
                        <ClickableTreeNode v-for="node in treeData" :key="node.name + (node.isFile ? node.blogId : '')"
                            :node="node" :badge-map="scopedPendingCountMap" badge-label="题待批阅" :tone="currentTone"
                            :initially-expanded="true" @file-click="openArticle" />
                    </div>
                </section>

                <section class="review-detail" aria-live="polite">
                    <EmptyState v-if="!selectedArticle" icon="fas fa-hand-pointer" title="选择一篇文章开始批阅"
                        text="左侧点击文章后，这里会显示学员作答与参考答案。" />

                    <div v-else-if="selectedArticle.loading" class="review-detail__loading">
                        <div class="skeleton review-detail__skeleton"></div>
                        <div class="skeleton review-detail__skeleton"></div>
                    </div>

                    <div v-else-if="selectedArticle.error" class="review-detail__error">
                        <p class="field-error">
                            <i aria-hidden="true" class="fas fa-triangle-exclamation"></i>
                            {{ selectedArticle.error }}
                        </p>
                        <button type="button" class="btn btn--soft btn--sm" @click="reloadDetail">
                            <i aria-hidden="true" class="fas fa-rotate"></i> 重新加载
                        </button>
                    </div>

                    <template v-else>
                        <header class="review-detail__head">
                            <div class="review-detail__title-wrap">
                                <span class="eyebrow">REVIEW</span>
                                <h3 class="review-detail__title">{{ selectedArticle.title }}</h3>
                                <div class="row row--wrap review-detail__meta">
                                    <span class="chip" :class="`chip--${currentTone}`">{{ selectedArticle.series }}</span>
                                    <span v-if="selectedArticle.date" class="tag">{{ selectedArticle.date }}</span>
                                    <span v-if="pendingOf(selectedArticle.id) > 0" class="chip chip--amber">
                                        <i aria-hidden="true" class="fas fa-hourglass-half"></i>
                                        {{ pendingOf(selectedArticle.id) }} 题待批阅
                                    </span>
                                </div>
                            </div>
                            <div class="review-detail__actions">
                                <button type="button" class="btn btn--ghost btn--sm" @click="reloadDetail">
                                    <i aria-hidden="true" class="fas fa-rotate"></i> 刷新
                                </button>
                                <button type="button" class="btn btn--soft btn--sm" @click="openOriginal">
                                    <i aria-hidden="true" class="fas fa-arrow-up-right-from-square"></i> 打开原文批阅
                                </button>
                            </div>
                        </header>

                        <EmptyState v-if="selectedArticle.questions.length === 0" icon="fas fa-inbox"
                            title="这篇文章还没有可批阅的作答" text="学员提交作答后，题目会出现在这里。" />

                        <div v-else class="review-questions">
                            <article v-for="question in selectedArticle.questions" :key="question.questionId"
                                class="review-card" :class="`is-tone-${currentTone}`">
                                <header class="review-card__head">
                                    <span class="review-card__index">第 {{ question.index }} 题</span>
                                    <span class="tag mono">#{{ question.questionId }}</span>
                                    <span v-if="question.autoGrade" class="tag">自动批阅</span>
                                    <span class="review-card__status" :class="statusTone(question)">
                                        <i aria-hidden="true" class="fas" :class="statusIcon(question)"></i>
                                        {{ statusText(question) }}
                                    </span>
                                </header>

                                <div class="review-card__answers">
                                    <div class="review-answer">
                                        <span class="review-answer__label">学员作答</span>
                                        <p class="review-answer__text" :class="{ 'is-empty': !question.studentAnswer }">
                                            {{ question.studentAnswer || '未提交作答' }}
                                        </p>
                                    </div>
                                    <div class="review-answer is-reference">
                                        <span class="review-answer__label">参考答案</span>
                                        <p class="review-answer__text" :class="{ 'is-empty': !question.referenceAnswer }">
                                            {{ question.referenceAnswer || '未设置标准答案' }}
                                        </p>
                                    </div>
                                </div>

                                <footer class="review-card__options">
                                    <span class="text-xs text-dim">批阅结果</span>
                                    <div class="review-choices">
                                        <button v-for="option in reviewOptions" :key="option.value" type="button"
                                            class="review-choice"
                                            :class="[`is-${option.value}`, { 'is-active': question.reviewResult === option.value }]"
                                            :disabled="!question.submitted || question.saving"
                                            @click="reviewQuestion(question, option.value)">
                                            <i aria-hidden="true" class="fas" :class="option.icon"></i>
                                            {{ option.label }}
                                        </button>
                                    </div>
                                </footer>
                            </article>
                        </div>
                    </template>
                </section>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { learningGateway } from '@/gateways/learningGateway'
import { showToast } from '@/utils/toast'
import EmptyState from '@/components/ui/EmptyState.vue'
import ClickableTreeNode from './ClickableTreeNode.vue'

const props = defineProps({
    students: { type: Array, default: () => [] },
    selectedStudentId: { type: [String, Number], default: null },
    blogData: { type: Array, default: () => [] },
    allSubjects: { type: Array, default: () => [] }
})

const router = useRouter()

const currentSubject = ref('')
const activeFilter = ref('all')
const keyword = ref('')

const pendingCountMap = ref(new Map())
const studentSubmissionStatus = ref(new Map())
const answerKeysCache = ref(new Map())
const loadingProgress = ref(false)
const progressError = ref('')

const selectedArticle = ref(null)

const reviewOptions = [
    { value: 'correct', label: '正确', icon: 'fa-check' },
    { value: 'partial', label: '半对', icon: 'fa-circle-half-stroke' },
    { value: 'wrong', label: '错误', icon: 'fa-xmark' }
]

function subjectTone(subject) {
    if (!subject) return 'violet'
    if (subject.includes('初中英语')) return 'amber'
    if (subject.includes('化学')) return 'cyan'
    if (subject.includes('英语')) return 'violet'
    return 'pink'
}

const currentStudent = computed(() => props.students
    .find(item => String(item.id) === String(props.selectedStudentId)) || null)

const currentStudentName = computed(() => currentStudent.value?.username || '未选择')

// 当前学员已开放权限的文章 ID；无学员或未授权时为空集合。
const grantedIds = computed(() => {
    const permissions = currentStudent.value?.permissions
    if (!Array.isArray(permissions)) return new Set()
    return new Set(permissions.map(Number).filter(Number.isFinite))
})

const grantedArticles = computed(() => props.blogData.filter(blog => grantedIds.value.has(Number(blog.id))))

const grantedArticleCount = computed(() => grantedArticles.value.length)

const grantedSubjects = computed(() => {
    const subjects = new Set()
    grantedArticles.value.forEach(blog => { if (blog.series) subjects.add(blog.series) })
    return Array.from(subjects)
})

function scopeByGrant(source) {
    const scoped = new Map()
    source.forEach((value, blogId) => {
        const id = Number(blogId)
        if (grantedIds.value.has(id)) scoped.set(id, value)
    })
    return scoped
}

// progress 已按授权范围返回；权限变更或缓存残留时仍只保留授权文章，避免统计与列表不一致。
const scopedPendingCountMap = computed(() => scopeByGrant(pendingCountMap.value))
const scopedSubmissionStatus = computed(() => scopeByGrant(studentSubmissionStatus.value))

const currentTone = computed(() => subjectTone(currentSubject.value))

const pendingArticleCount = computed(() => scopedPendingCountMap.value.size)
const pendingQuestionCount = computed(() => Array.from(scopedPendingCountMap.value.values()).reduce((sum, count) => sum + count, 0))
const reviewedArticleCount = computed(() => Array.from(scopedSubmissionStatus.value.values()).filter(status => status === 'reviewed').length)
const submittedArticleCount = computed(() => scopedSubmissionStatus.value.size)

const subjectArticles = computed(() => grantedArticles.value.filter(blog => blog.series === currentSubject.value))

const subjectStats = computed(() => props.allSubjects
    .filter(subject => grantedArticles.value.some(blog => blog.series === subject))
    .map(subject => {
        const articles = grantedArticles.value.filter(blog => blog.series === subject)
        const pending = articles.filter(blog => scopedSubmissionStatus.value.get(Number(blog.id)) === 'pending').length
        return { subject, tone: subjectTone(subject), pending }
    }))

const matchesStatus = (blog) => {
    const blogId = Number(blog.id)
    const status = scopedSubmissionStatus.value.get(blogId)
    if (activeFilter.value === 'pending') return status === 'pending'
    if (activeFilter.value === 'reviewed') return status === 'reviewed'
    if (activeFilter.value === 'unsubmitted') {
        return answerKeysCache.value.has(blogId) && !scopedSubmissionStatus.value.has(blogId)
    }
    return true
}

const filteredArticles = computed(() => {
    const search = keyword.value.trim().toLowerCase()
    return subjectArticles.value.filter(blog => {
        if (!matchesStatus(blog)) return false
        if (!search) return true
        return String(blog.title || '').toLowerCase().includes(search)
    })
})

const filters = computed(() => {
    const articles = subjectArticles.value
    const countBy = (value) => articles.filter(blog => {
        const blogId = Number(blog.id)
        const status = scopedSubmissionStatus.value.get(blogId)
        if (value === 'pending') return status === 'pending'
        if (value === 'reviewed') return status === 'reviewed'
        if (value === 'unsubmitted') {
            return answerKeysCache.value.has(blogId) && !scopedSubmissionStatus.value.has(blogId)
        }
        return true
    }).length
    return [
        { value: 'pending', label: '待批阅', count: countBy('pending') },
        { value: 'reviewed', label: '已批阅', count: countBy('reviewed') },
        { value: 'unsubmitted', label: '未提交', count: countBy('unsubmitted') },
        { value: 'all', label: '全部', count: articles.length }
    ]
})

const treeData = computed(() => buildTree(filteredArticles.value))

function buildTree(articles) {
    const root = { children: [] }

    articles.forEach(blog => {
        const parts = blog.path.split('/')
        const dirParts = parts.slice(1)
        const title = blog.title
        let current = root

        for (let i = 0; i < dirParts.length; i++) {
            const part = dirParts[i]
            if (i === dirParts.length - 1) {
                if (!current.children.find(child => child.name === title && child.isFile)) {
                    current.children.push({
                        name: title,
                        isFile: true,
                        blogId: blog.id,
                        date: blog.date,
                        series: blog.series
                    })
                }
            } else {
                let dirNode = current.children.find(child => child.name === part && !child.isFile)
                if (!dirNode) {
                    dirNode = { name: part, isFile: false, children: [] }
                    current.children.push(dirNode)
                }
                current = dirNode
            }
        }
    })

    function sortNode(node) {
        if (!node.children) return
        node.children.sort((left, right) => {
            if (left.isFile !== right.isFile) return left.isFile ? 1 : -1
            return left.name.localeCompare(right.name)
        })
        node.children.forEach(child => { if (!child.isFile) sortNode(child) })
    }
    sortNode(root)

    return root.children
}

function selectSubject(subject) {
    currentSubject.value = subject
    keyword.value = ''
}

function pendingOf(blogId) {
    return Number(scopedPendingCountMap.value.get(Number(blogId))) || 0
}

function statusText(question) {
    if (!question.submitted) return '未提交'
    if (question.reviewStatus === 'pending') return '待批阅'
    const labels = { correct: '正确', partial: '半对', wrong: '错误' }
    return labels[question.reviewResult] || '已批阅'
}

function statusTone(question) {
    if (!question.submitted) return 'is-idle'
    if (question.reviewStatus === 'pending') return 'is-pending'
    return `is-${question.reviewResult || 'reviewed'}`
}

function statusIcon(question) {
    if (!question.submitted) return 'fa-minus'
    if (question.reviewStatus === 'pending') return 'fa-hourglass-half'
    const icons = { correct: 'fa-circle-check', partial: 'fa-circle-half-stroke', wrong: 'fa-circle-xmark' }
    return icons[question.reviewResult] || 'fa-circle-check'
}

async function loadSubmissionStatus() {
    if (!props.selectedStudentId) {
        pendingCountMap.value = new Map()
        studentSubmissionStatus.value = new Map()
        return
    }
    loadingProgress.value = true
    progressError.value = ''
    try {
        const data = await learningGateway.getAdministratorProgress(props.selectedStudentId)
        const statusMap = new Map()
        const countMap = new Map()
        ;(data || []).forEach(item => {
            const blogId = Number(item.articleId)
            if (item.pendingReviews > 0) {
                statusMap.set(blogId, 'pending')
                countMap.set(blogId, item.pendingReviews)
            } else if (item.submittedQuestions > 0) {
                statusMap.set(blogId, 'reviewed')
            }
        })
        studentSubmissionStatus.value = statusMap
        pendingCountMap.value = countMap
    } catch (error) {
        console.error('加载提交记录失败:', error)
        progressError.value = '提交记录加载失败，请稍后刷新重试。'
    } finally {
        loadingProgress.value = false
    }
}

async function loadAnswerKeysCache() {
    try {
        const articleIds = await learningGateway.getAnswerKeyArticleIds()
        const cache = new Map()
        articleIds.forEach(articleId => cache.set(Number(articleId), true))
        answerKeysCache.value = cache
    } catch (error) {
        console.error('加载答案数据失败:', error)
    }
}

async function openArticle(blogId) {
    if (!props.selectedStudentId) return
    const blog = props.blogData.find(item => Number(item.id) === Number(blogId))
    if (!blog) return

    selectedArticle.value = { ...blog, questions: [], loading: true, error: '' }
    try {
        const state = await learningGateway.getAdministratorState(blogId, props.selectedStudentId)
        const questions = (state?.questions || []).map(item => {
            const submission = item.submission || null
            return {
                questionId: String(item.questionId),
                studentAnswer: submission?.answerText || '',
                submitted: Boolean(submission),
                reviewStatus: submission?.reviewStatus || 'unsubmitted',
                reviewResult: submission?.reviewResult || null,
                referenceAnswer: item.answerText || '',
                autoGrade: Boolean(item.autoGrade),
                saving: false
            }
        })
        questions.sort((left, right) => {
            const leftNumber = Number(left.questionId)
            const rightNumber = Number(right.questionId)
            if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) return leftNumber - rightNumber
            return left.questionId.localeCompare(right.questionId)
        })
        questions.forEach((question, index) => { question.index = index + 1 })
        selectedArticle.value = { ...blog, questions, loading: false, error: '' }
    } catch (error) {
        selectedArticle.value = {
            ...blog,
            questions: [],
            loading: false,
            error: error.message || '加载批阅数据失败，请稍后重试。'
        }
    }
}

function reloadDetail() {
    if (selectedArticle.value) openArticle(selectedArticle.value.id)
}

function openOriginal() {
    if (!selectedArticle.value || !props.selectedStudentId) return
    router.push(`/blog/${selectedArticle.value.id}?mode=review&studentId=${props.selectedStudentId}&from=admin`)
}

async function reviewQuestion(question, result) {
    const article = selectedArticle.value
    if (!article || !props.selectedStudentId || question.saving) return
    if (!question.submitted) {
        showToast('该题尚未提交，无法批阅', 'info')
        return
    }

    question.saving = true
    try {
        await learningGateway.review(article.id, props.selectedStudentId, question.questionId, result)
        question.reviewStatus = 'reviewed'
        question.reviewResult = result
        const labels = { correct: '正确', partial: '半对', wrong: '错误' }
        showToast(`第 ${question.index} 题已批阅为「${labels[result] || result}」`, 'success')
        await loadSubmissionStatus()
    } catch (error) {
        showToast(error.message || '批阅保存失败，请稍后重试', 'error')
    } finally {
        question.saving = false
    }
}

watch(() => props.selectedStudentId, () => {
    selectedArticle.value = null
    loadSubmissionStatus()
})

watch(() => props.allSubjects, (subjects) => {
    if (subjects.length > 0 && !currentSubject.value) {
        currentSubject.value = subjects[0]
    }
}, { immediate: true })

// 学员切换或授权变化后，保证当前学科仍在授权范围内。
watch(grantedSubjects, (subjects) => {
    if (!subjects.includes(currentSubject.value)) {
        currentSubject.value = subjects[0] || ''
    }
}, { immediate: true })

// 授权被收回时关闭已不在授权范围内的批阅详情。
watch(grantedIds, (ids) => {
    if (selectedArticle.value && !ids.has(Number(selectedArticle.value.id))) {
        selectedArticle.value = null
    }
})

onMounted(() => {
    loadSubmissionStatus()
    loadAnswerKeysCache()
})
</script>

<style scoped>
.review-panel {
    overflow: hidden;
}

.review-head {
    align-items: flex-start;
}

.review-head__copy {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.review-head__chips {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.review-body-wrap {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.review-stats {
    gap: 12px;
}

.review-subjects {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.review-subject {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    height: 36px;
    padding: 0 14px;
    border-radius: var(--r-full);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.03);
    color: var(--text-2);
    font-size: 0.8rem;
    font-weight: 600;
    white-space: nowrap;
    transition: border-color var(--dur), background var(--dur), color var(--dur), box-shadow var(--dur);
}

.review-subject i {
    color: var(--tone-color, var(--violet-2));
    font-size: 0.74rem;
}

.review-subject:hover {
    color: var(--text-1);
    border-color: var(--tone-line, var(--line-strong));
}

.review-subject.is-active {
    color: #fff;
    border-color: var(--tone-line, rgba(139, 92, 246, 0.4));
    background: linear-gradient(120deg, var(--tone-soft, rgba(139, 92, 246, 0.16)), rgba(255, 255, 255, 0.03));
    box-shadow: 0 14px 30px -24px var(--tone-color, var(--violet));
}

.review-subject__count {
    font-family: var(--font-mono);
    font-size: 0.64rem;
    color: var(--text-4);
}

.review-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
}

.review-search {
    width: min(260px, 100%);
}

.review-search .input {
    height: 40px;
    border-radius: var(--r-full);
    font-size: 0.82rem;
}

.review-error {
    display: inline-flex;
    align-items: center;
    gap: 8px;
}

.review-layout {
    display: grid;
    grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.25fr);
    gap: 16px;
    align-items: start;
}

.review-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    max-height: 620px;
    overflow-y: auto;
    padding-right: 4px;
}

.review-tree {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.review-skeletons {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.review-skeleton {
    height: 58px;
    border-radius: var(--r-sm);
}

.review-detail {
    border-radius: var(--r-lg);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.02);
    padding: 18px;
    min-height: 320px;
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.review-detail__loading {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.review-detail__skeleton {
    height: 120px;
    border-radius: var(--r-md);
}

.review-detail__error {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
}

.review-detail__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 14px;
    flex-wrap: wrap;
}

.review-detail__title-wrap {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
}

.review-detail__title {
    font-family: var(--font-display);
    font-size: 1.1rem;
    font-weight: 800;
    line-height: 1.3;
}

.review-detail__meta {
    gap: 6px;
}

.review-detail__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.review-questions {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.review-card {
    padding: 14px;
    border-radius: var(--r-md);
    border: 1px solid var(--line);
    background: linear-gradient(160deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.012));
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.review-card__head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.review-card__index {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--tone-color, var(--violet-2));
    letter-spacing: 0.06em;
}

.review-card__status {
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 24px;
    padding: 0 10px;
    border-radius: var(--r-full);
    font-size: 0.68rem;
    font-weight: 700;
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.04);
    color: var(--text-3);
    white-space: nowrap;
}

.review-card__status.is-pending {
    color: #fde68a;
    border-color: rgba(251, 191, 36, 0.32);
    background: rgba(251, 191, 36, 0.1);
}

.review-card__status.is-correct {
    color: #d9f99d;
    border-color: rgba(163, 230, 53, 0.32);
    background: rgba(163, 230, 53, 0.1);
}

.review-card__status.is-partial {
    color: #fde68a;
    border-color: rgba(251, 191, 36, 0.32);
    background: rgba(251, 191, 36, 0.1);
}

.review-card__status.is-wrong {
    color: #fecdd3;
    border-color: rgba(251, 113, 133, 0.34);
    background: rgba(251, 113, 133, 0.1);
}

.review-card__status.is-idle {
    color: var(--text-4);
}

.review-card__answers {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
}

.review-answer {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 11px 12px;
    border-radius: var(--r-sm);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.025);
    min-width: 0;
}

.review-answer.is-reference {
    border-style: dashed;
    border-color: var(--line-strong);
    background: rgba(255, 255, 255, 0.015);
}

.review-answer__label {
    font-size: 0.66rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--text-4);
    font-family: var(--font-mono);
}

.review-answer__text {
    font-size: 0.84rem;
    color: var(--text-1);
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 180px;
    overflow-y: auto;
}

.review-answer__text.is-empty {
    color: var(--text-4);
    font-style: italic;
}

.review-card__options {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
    padding-top: 4px;
}

.review-choices {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
}

.review-choice {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 32px;
    padding: 0 13px;
    border-radius: var(--r-full);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.03);
    color: var(--text-2);
    font-size: 0.78rem;
    font-weight: 600;
    transition: border-color var(--dur), background var(--dur), color var(--dur), transform var(--dur) var(--ease-out);
}

.review-choice:hover:not(:disabled) {
    transform: translateY(-1px);
    color: var(--text-1);
}

.review-choice:disabled {
    opacity: 0.45;
}

.review-choice.is-correct.is-active {
    color: #d9f99d;
    border-color: rgba(163, 230, 53, 0.45);
    background: rgba(163, 230, 53, 0.16);
}

.review-choice.is-partial.is-active {
    color: #fde68a;
    border-color: rgba(251, 191, 36, 0.45);
    background: rgba(251, 191, 36, 0.16);
}

.review-choice.is-wrong.is-active {
    color: #fecdd3;
    border-color: rgba(251, 113, 133, 0.45);
    background: rgba(251, 113, 133, 0.16);
}

/* 学科色调变量 */
.is-tone-cyan { --tone-color: var(--cyan); --tone-soft: rgba(34, 211, 238, 0.13); --tone-line: rgba(34, 211, 238, 0.34); }
.is-tone-violet { --tone-color: var(--violet-2); --tone-soft: rgba(139, 92, 246, 0.15); --tone-line: rgba(139, 92, 246, 0.36); }
.is-tone-amber { --tone-color: var(--amber); --tone-soft: rgba(251, 191, 36, 0.13); --tone-line: rgba(251, 191, 36, 0.34); }
.is-tone-pink { --tone-color: var(--pink); --tone-soft: rgba(244, 114, 182, 0.13); --tone-line: rgba(244, 114, 182, 0.34); }

/* 深色选择器内联样式兼容 */
.review-search .input::-webkit-search-cancel-button {
    filter: invert(0.7);
}

@media (max-width: 1024px) {
    .review-layout {
        grid-template-columns: minmax(0, 1fr);
    }

    .review-list {
        max-height: none;
        overflow: visible;
    }
}

@media (max-width: 760px) {
    .review-body-wrap {
        padding: 18px;
    }

    .review-stats {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .review-card__answers {
        grid-template-columns: minmax(0, 1fr);
    }

    .review-detail__actions {
        width: 100%;
    }

    .review-detail__actions .btn {
        flex: 1 1 auto;
    }
}

@media (max-width: 420px) {
    .review-stats {
        grid-template-columns: minmax(0, 1fr);
    }
}
</style>

<style scoped>
/* ==========================================================================
   Riso Campus：纸白批阅卡、墨线、三态实色印章。
   ========================================================================== */
.panel-title i {
    color: var(--vermilion);
}

.review-head .chip {
    box-shadow: 2px 2px 0 var(--ink);
}

.review-subject {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--text-2);
    box-shadow: 2px 2px 0 var(--ink);
}

.review-subject:hover {
    color: var(--ink);
    border-color: var(--ink);
    background: var(--paper-2);
}

.review-subject.is-active {
    color: var(--tone-text);
    border-color: var(--ink);
    background: var(--tone-color);
    box-shadow: 2px 2px 0 var(--ink);
}

.review-subject.is-active i {
    color: var(--tone-text);
}

.review-subject__count {
    color: inherit;
    opacity: 0.85;
}

.review-detail {
    border: 1.5px solid var(--ink);
    background: var(--paper-0);
    box-shadow: 3px 3px 0 var(--ink);
}

.review-detail__title {
    color: var(--ink);
}

.review-card {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.review-card__index {
    color: var(--tone-color);
}

.review-card .tag {
    background: var(--paper-2);
}

.review-card__status {
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
    color: var(--text-3);
    box-shadow: 1px 1px 0 var(--ink);
}

.review-card__status.is-pending {
    color: var(--ink);
    background: var(--butter);
    border-color: var(--ink);
}

.review-card__status.is-correct {
    color: var(--ink);
    background: var(--lime);
    border-color: var(--ink);
}

.review-card__status.is-partial {
    color: var(--ink);
    background: var(--butter);
    border-color: var(--ink);
}

.review-card__status.is-wrong {
    color: var(--ink);
    background: var(--coral);
    border-color: var(--ink);
}

.review-card__status.is-idle {
    color: var(--text-3);
    background: var(--paper-2);
}

.review-answer {
    border: 1.5px solid var(--line-strong);
    background: var(--paper-1);
}

.review-answer.is-reference {
    border: 1.5px dashed var(--ink);
    background: var(--paper-2);
}

.review-answer__text {
    color: var(--ink);
}

.review-answer__label {
    color: var(--text-3);
}

.review-choice {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

.review-choice:hover:not(:disabled) {
    color: var(--ink);
    background: var(--paper-2);
    transform: translate(-1px, -1px);
    box-shadow: 3px 3px 0 var(--ink);
}

.review-choice.is-correct.is-active {
    color: var(--ink);
    border-color: var(--ink);
    background: var(--lime);
}

.review-choice.is-partial.is-active {
    color: var(--ink);
    border-color: var(--ink);
    background: var(--butter);
}

.review-choice.is-wrong.is-active {
    color: var(--ink);
    border-color: var(--ink);
    background: var(--coral);
}

/* 学科色 */
.is-tone-cyan { --tone-color: var(--teal); --tone-text: var(--paper-1); }
.is-tone-violet { --tone-color: var(--cobalt); --tone-text: var(--paper-1); }
.is-tone-amber { --tone-color: var(--butter); --tone-text: var(--ink); }
.is-tone-pink { --tone-color: var(--pink); --tone-text: var(--ink); }

.review-search .input::-webkit-search-cancel-button {
    filter: none;
}

/* 统计数字固定高度，避免数字变化造成布局抖动 */
.review-stats .stat-value {
    display: flex;
    align-items: center;
    min-height: 2.3rem;
}
</style>
