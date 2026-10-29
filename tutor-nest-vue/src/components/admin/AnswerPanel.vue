<!-- src/components/admin/AnswerPanel.vue -->
<template>
    <div class="panel answer-panel">
        <header class="panel-head answer-head">
            <div class="answer-head__copy">
                <h2 class="panel-title">
                    <i aria-hidden="true" class="fas fa-key"></i>
                    设置答案
                </h2>
                <p class="text-sm text-muted">
                    只显示当前学员在学的科目与已授权文章；填写标准答案后可开启自动批阅，一次保存全部题目。
                </p>
            </div>
            <div class="row row--wrap answer-head__chips">
                <span class="chip chip--cyan">
                    <i aria-hidden="true" class="fas fa-user-graduate"></i>
                    {{ currentStudentName }}
                </span>
            </div>
        </header>

        <div class="panel-body answer-body">
            <EmptyState v-if="!selectedStudentId" icon="fas fa-user-graduate" title="尚未选择学员"
                text="在顶部切换学员后，这里只显示他在学的科目与已授权文章。" />

            <div v-else-if="coursewareStatus === 'loading'" class="answer-skeletons" aria-hidden="true">
                <div class="skeleton answer-skeleton"></div>
                <div class="skeleton answer-skeleton"></div>
                <div class="skeleton answer-skeleton"></div>
            </div>

            <EmptyState v-else-if="coursewareStatus === 'error'" icon="fas fa-triangle-exclamation"
                title="课件目录加载失败" :text="coursewareStore.error || '请稍后重试。'">
                <button type="button" class="btn btn--soft btn--sm" @click="reloadCourseware">
                    <i aria-hidden="true" class="fas fa-rotate"></i> 重新加载
                </button>
            </EmptyState>

            <EmptyState v-else-if="coursewareStatus === 'empty'" icon="fas fa-hand-holding-heart"
                title="你还没有可见课件"
                text="你先到课件页认领学科或课件，认领后即可为这些课件设置标准答案。" />

            <EmptyState v-else-if="scopedArticles.length === 0" icon="fas fa-shield-halved"
                title="该学员尚未开通任何内容授权"
                text="先到「权限管理」为该学员整科开通在学科目，再回到这里设置标准答案。" />

            <template v-else>
                <div class="answer-subjects" role="group" aria-label="学科筛选">
                    <button v-for="item in subjectStats" :key="item.subject" type="button" class="answer-subject"
                        :class="[`is-tone-${item.tone}`, { 'is-active': currentSubject === item.subject }]"
                        :aria-pressed="currentSubject === item.subject" @click="selectSubject(item.subject)">
                        <i aria-hidden="true" class="fas fa-book-open"></i>
                        {{ item.subject }}
                    </button>
                </div>

                <div class="answer-toolbar">
                    <div class="seg" role="group" aria-label="答案设置状态筛选">
                        <button v-for="filter in filters" :key="filter.value" type="button" class="seg__btn"
                            :class="{ 'is-active': activeFilter === filter.value }"
                            :aria-pressed="activeFilter === filter.value" @click="activeFilter = filter.value">
                            {{ filter.label }}
                            <span class="seg__count">{{ filter.count }}</span>
                        </button>
                    </div>
                    <div class="input-wrap answer-search">
                        <i aria-hidden="true" class="fas fa-magnifying-glass"></i>
                        <input v-model="keyword" class="input" type="search" placeholder="搜索文章标题"
                            aria-label="搜索文章标题" />
                    </div>
                </div>

                <div class="answer-layout">
                    <section class="answer-list" aria-label="答案设置文章列表">
                        <div v-if="loadingKeys" class="answer-skeletons" aria-hidden="true">
                            <div class="skeleton answer-skeleton"></div>
                            <div class="skeleton answer-skeleton"></div>
                            <div class="skeleton answer-skeleton"></div>
                        </div>

                        <EmptyState v-else-if="!currentSubject" icon="fas fa-folder-open" title="未选择学科"
                            text="选择学科后浏览文章，点击文章填写标准答案。" />

                        <EmptyState v-else-if="filteredArticles.length === 0" icon="fas fa-filter"
                            title="没有符合条件的文章" text="切换筛选条件，或选择其他学科。" />

                        <div v-else class="answer-tree">
                            <ClickableTreeNode v-for="node in treeData" :key="node.name + (node.isFile ? node.blogId : '')"
                                :node="node" :marked-ids="answerKeyIds" marked-label="已设置" :tone="currentTone"
                                :initially-expanded="activeFilter === 'unset'" @file-click="openArticle" />
                        </div>
                    </section>

                    <section class="answer-editor" aria-live="polite">
                        <EmptyState v-if="!selectedArticle" icon="fas fa-pen-to-square" title="选择一篇文章开始设置答案"
                            text="左侧点击文章后，这里会列出题号、标准答案与自动批阅开关。" />

                        <div v-else-if="selectedArticle.loading" class="answer-editor__loading">
                            <div class="answer-editor__loading-head">
                                <div class="answer-editor__title-wrap">
                                    <span class="eyebrow">ANSWER KEYS</span>
                                    <h3 class="answer-editor__title">{{ selectedArticle.title }}</h3>
                                </div>
                                <button type="button" class="btn btn--soft btn--sm" @click="openOriginal">
                                    <i aria-hidden="true" class="fas fa-pen-to-square"></i>
                                    进入文章设置
                                </button>
                            </div>
                            <div class="skeleton answer-editor__skeleton"></div>
                            <div class="skeleton answer-editor__skeleton"></div>
                        </div>

                        <div v-else-if="selectedArticle.error" class="answer-editor__error">
                            <p class="field-error">
                                <i aria-hidden="true" class="fas fa-triangle-exclamation"></i>
                                {{ selectedArticle.error }}
                            </p>
                            <div class="row row--wrap">
                                <button type="button" class="btn btn--ghost btn--sm" @click="reloadArticle">
                                    <i aria-hidden="true" class="fas fa-rotate"></i> 重新加载
                                </button>
                                <button type="button" class="btn btn--soft btn--sm" @click="openOriginal">
                                    <i aria-hidden="true" class="fas fa-pen-to-square"></i> 进入文章设置
                                </button>
                            </div>
                        </div>

                        <template v-else>
                            <header class="answer-editor__head">
                                <div class="answer-editor__title-wrap">
                                    <span class="eyebrow">ANSWER KEYS</span>
                                    <h3 class="answer-editor__title">{{ selectedArticle.title }}</h3>
                                    <div class="row row--wrap answer-editor__meta">
                                        <span class="chip" :class="`chip--${currentTone}`">{{ selectedArticle.series }}</span>
                                        <span v-if="selectedArticle.date" class="tag">{{ selectedArticle.date }}</span>
                                        <span class="tag">{{ selectedArticle.questions.length }} 道题</span>
                                    </div>
                                </div>
                                <div class="answer-editor__head-actions">
                                    <span class="chip" :class="dirty ? 'chip--amber' : 'chip--lime'">
                                        <i aria-hidden="true" class="fas" :class="dirty ? 'fa-pen' : 'fa-circle-check'"></i>
                                        {{ dirty ? '待保存' : '已同步' }}
                                    </span>
                                    <button v-if="!selectedArticle.noQuestions" type="button"
                                        class="btn btn--soft btn--sm" @click="openOriginal">
                                        <i aria-hidden="true" class="fas fa-pen-to-square"></i>
                                        进入文章设置
                                    </button>
                                </div>
                            </header>

                            <EmptyState v-if="selectedArticle.questions.length === 0 && selectedArticle.noQuestions"
                                icon="fas fa-circle-info" title="该文章暂无题目"
                                text="这个课件没有检测到题目，无需设置标准答案。" />

                            <EmptyState v-else-if="selectedArticle.questions.length === 0" icon="fas fa-circle-question"
                                title="这篇文章里没有识别到题目"
                                text="可以点击「进入文章设置」进入整页答案设置模式。">
                                <button type="button" class="btn btn--soft btn--sm" @click="openOriginal">
                                    <i aria-hidden="true" class="fas fa-pen-to-square"></i>
                                    进入文章设置
                                </button>
                            </EmptyState>

                            <div v-else class="answer-questions">
                                <article v-for="question in selectedArticle.questions" :key="question.questionId"
                                    class="answer-card" :class="`is-tone-${currentTone}`">
                                    <header class="answer-card__head">
                                        <span class="answer-card__index">第 {{ question.index }} 题</span>
                                        <span class="tag mono">#{{ question.questionId }}</span>
                                        <span v-if="question.wasSet" class="chip chip--lime">
                                            <i aria-hidden="true" class="fas fa-circle-check"></i>
                                            已设置
                                        </span>
                                        <label class="answer-switch"
                                            :title="question.autoGrade ? '关闭自动批阅' : '开启自动批阅'">
                                            <input v-model="question.autoGrade" type="checkbox" role="switch"
                                                class="switch-input" :aria-label="`第 ${question.index} 题自动批阅`" />
                                            <span class="switch-track" aria-hidden="true">
                                                <span class="switch-thumb"></span>
                                            </span>
                                            <span class="answer-switch__text">自动批阅</span>
                                        </label>
                                    </header>
                                    <textarea v-model="question.answerText" class="textarea answer-card__input" rows="3"
                                        :placeholder="`填写第 ${question.index} 题的标准答案`"></textarea>
                                </article>
                            </div>

                            <footer v-if="!selectedArticle.noQuestions" class="answer-editor__footer">
                                <div class="answer-editor__state">
                                    <span class="chip" :class="dirty ? 'chip--amber' : 'chip--lime'">
                                        <i aria-hidden="true" class="fas" :class="dirty ? 'fa-pen' : 'fa-circle-check'"></i>
                                        {{ dirty ? '有未保存改动' : '已同步' }}
                                    </span>
                                    <span class="text-sm text-muted">
                                        共 {{ selectedArticle.questions.length }} 题 · 自动批阅
                                        {{ autoGradeCount }} 题
                                    </span>
                                </div>
                                <div class="answer-editor__actions">
                                    <button type="button" class="btn btn--primary"
                                        :disabled="saving || selectedArticle.questions.length === 0"
                                        @click="saveAnswerKeys">
                                        <i aria-hidden="true" class="fas"
                                            :class="saving ? 'fa-circle-notch fa-spin' : 'fa-cloud-arrow-up'"></i>
                                        {{ saving ? '保存中…' : '批量保存' }}
                                    </button>
                                </div>
                            </footer>
                        </template>
                    </section>
                </div>
            </template>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCoursewareStore } from '@/stores/coursewareStore'
import { grantedArticles } from '@/utils/adminSubjects'
import { learningGateway } from '@/gateways/learningGateway'
import { showToast } from '@/utils/toast'
import EmptyState from '@/components/ui/EmptyState.vue'
import ClickableTreeNode from './ClickableTreeNode.vue'

const props = defineProps({
    blogData: { type: Array, default: () => [] },
    allSubjects: { type: Array, default: () => [] },
    students: { type: Array, default: () => [] },
    selectedStudentId: { type: [String, Number], default: null }
})

const router = useRouter()
const coursewareStore = useCoursewareStore()

const currentSubject = ref('')
const activeFilter = ref('unset')
const keyword = ref('')

const answerKeyIds = ref([])
const loadingKeys = ref(false)

const selectedArticle = ref(null)
const saving = ref(false)
const snapshot = ref('')

function subjectTone(subject) {
    if (!subject) return 'violet'
    if (subject.includes('初中英语')) return 'amber'
    if (subject.includes('英语')) return 'violet'
    if (subject.includes('化学')) return 'cyan'
    return 'pink'
}

const currentTone = computed(() => subjectTone(currentSubject.value))

// 只允许在当前学员「在学」的科目与「已授权」文章里设置答案：
// 未授权文章对该学员不可见，也不需要维护标准答案；同时限定在我的课件范围内。
const currentStudent = computed(() => props.students
    .find(item => String(item.id) === String(props.selectedStudentId)) || null)
const currentStudentName = computed(() => currentStudent.value?.username || '未选择学员')

const coursewareStatus = computed(() => {
    if (!coursewareStore.loaded) return coursewareStore.error ? 'error' : 'loading'
    return coursewareStore.visibleArticleIds.size === 0 ? 'empty' : 'ready'
})

function reloadCourseware() {
    coursewareStore.loadWorkspace(props.blogData).catch(() => {})
}

watch(() => props.blogData, (articles) => {
    if (!Array.isArray(articles) || articles.length === 0) return
    coursewareStore.ensureWorkspace(articles).catch(() => {})
}, { immediate: true })

const scopedArticles = computed(() => grantedArticles({
    articles: props.blogData,
    permissions: currentStudent.value?.permissions || [],
    hasLearner: Boolean(props.selectedStudentId)
}).filter(blog => coursewareStore.visibleArticleIds.has(Number(blog.id))))

const subjectArticles = computed(() => scopedArticles.value.filter(blog => blog.series === currentSubject.value))

const subjectStats = computed(() => props.allSubjects
    .map(subject => {
        const articles = scopedArticles.value.filter(blog => blog.series === subject)
        if (articles.length === 0) return null
        const setCount = articles.filter(blog => answerKeyIds.value.includes(Number(blog.id))).length
        return { subject, tone: subjectTone(subject), total: articles.length, setCount }
    })
    .filter(Boolean))

// 静态目录里 questions 数组为空表示该文章没有题目：不应出现在「待设置」，也不提示去设置答案。
function hasQuestions(blog) {
    return Array.isArray(blog?.questions) && blog.questions.length > 0
}

// 筛选标签右侧保留数量（待设置/已设置/全部），只针对当前学科；待设置排除无题文章。
const filters = computed(() => {
    const articles = subjectArticles.value
    const setCount = articles.filter(blog => answerKeyIds.value.includes(Number(blog.id))).length
    const pendingCount = articles.filter(blog =>
        hasQuestions(blog) && !answerKeyIds.value.includes(Number(blog.id))).length
    return [
        { value: 'unset', label: '待设置', count: pendingCount },
        { value: 'set', label: '已设置', count: setCount },
        { value: 'all', label: '全部', count: articles.length }
    ]
})

const filteredArticles = computed(() => {
    const search = keyword.value.trim().toLowerCase()
    let articles = subjectArticles.value

    if (activeFilter.value === 'set') {
        articles = articles.filter(blog => answerKeyIds.value.includes(Number(blog.id)))
    } else if (activeFilter.value === 'unset') {
        articles = articles.filter(blog =>
            hasQuestions(blog) && !answerKeyIds.value.includes(Number(blog.id)))
    }

    if (search) {
        articles = articles.filter(blog => String(blog.title || '').toLowerCase().includes(search))
    }

    return articles
})

const treeData = computed(() => buildTree(filteredArticles.value))

const dirty = computed(() => {
    const article = selectedArticle.value
    if (!article || article.loading || !article.questions?.length || !snapshot.value) return false
    return JSON.stringify(article.questions.map(rowsOf)) !== snapshot.value
})

const autoGradeCount = computed(() => {
    const questions = selectedArticle.value?.questions || []
    return questions.filter(question => question.autoGrade).length
})

function rowsOf(question) {
    return {
        questionId: question.questionId,
        answerText: question.answerText,
        autoGrade: Boolean(question.autoGrade)
    }
}

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

// 与文章详情页保持一致的题目占位符解析：`【@2】` 指定题号，`【@】` 自动递增。
function extractQuestionIds(markdown) {
    const ids = []
    const used = new Set()
    let autoCounter = 1
    for (const match of markdown.matchAll(/【@(\d*)】/g)) {
        let questionId
        if (match[1] !== '') {
            questionId = match[1]
            used.add(Number(match[1]))
        } else {
            while (used.has(autoCounter)) autoCounter++
            questionId = String(autoCounter)
            used.add(autoCounter)
            autoCounter++
        }
        if (!ids.includes(questionId)) ids.push(questionId)
    }
    return ids
}

async function fetchArticleMarkdown(path) {
    const response = await fetch(`${import.meta.env.BASE_URL}articles/${path}`)
    if (!response.ok) throw new Error('无法读取文章内容，请稍后重试。')
    return response.text()
}

async function openArticle(blogId) {
    const blog = props.blogData.find(item => Number(item.id) === Number(blogId))
    if (!blog) return

    // 静态目录没有题目的文章：编辑器只展示「暂无题目」，也不再请求答案键或提示去设置答案。
    const noQuestions = !hasQuestions(blog)
    if (noQuestions) {
        selectedArticle.value = { ...blog, questions: [], noQuestions: true, loading: false, error: '' }
        snapshot.value = ''
        return
    }

    selectedArticle.value = { ...blog, questions: [], loading: true, error: '', noQuestions }
    snapshot.value = ''
    try {
        const [markdownResult, keysResult] = await Promise.allSettled([
            fetchArticleMarkdown(blog.path),
            learningGateway.getAnswerKeys(blogId)
        ])
        if (keysResult.status === 'rejected') throw keysResult.reason

        const markdown = markdownResult.status === 'fulfilled' ? markdownResult.value : ''
        const keys = keysResult.value || []
        const keyMap = new Map(keys.map(item => [String(item.questionId), item]))

        // 以文章占位符解析出的题号为主；若正文解析异常，仍保留已存在的答案配置，避免保存时误删。
        const questionIds = extractQuestionIds(markdown)
        keyMap.forEach((_, questionId) => {
            if (!questionIds.includes(questionId)) questionIds.push(questionId)
        })

        if (!questionIds.length) {
            selectedArticle.value = {
                ...blog,
                questions: [],
                noQuestions,
                loading: false,
                error: markdownResult.status === 'rejected' ? '无法读取文章内容，请稍后重试。' : ''
            }
            return
        }

        const questions = questionIds.map((questionId, index) => {
            const key = keyMap.get(questionId)
            return {
                questionId,
                index: index + 1,
                answerText: key?.answerText || '',
                autoGrade: Boolean(key?.autoGrade),
                wasSet: Boolean(key && (String(key.answerText || '').trim() !== '' || key.autoGrade))
            }
        })
        selectedArticle.value = { ...blog, questions, loading: false, error: '', noQuestions }
        snapshot.value = JSON.stringify(questions.map(rowsOf))
    } catch (error) {
        selectedArticle.value = {
            ...blog,
            questions: [],
            noQuestions,
            loading: false,
            error: error.message || '加载文章题目失败，请稍后重试。'
        }
    }
}

function reloadArticle() {
    if (selectedArticle.value) openArticle(selectedArticle.value.id)
}

function openOriginal() {
    if (!selectedArticle.value) return
    router.push(`/blog/${selectedArticle.value.id}?mode=answer`)
}

function selectSubject(subject) {
    if (currentSubject.value === subject) {
        currentSubject.value = subject
        return
    }
    currentSubject.value = subject
    keyword.value = ''
    selectedArticle.value = null
    snapshot.value = ''
}

async function loadAnswerKeysCache() {
    loadingKeys.value = true
    try {
        const articleIds = await learningGateway.getAnswerKeyArticleIds()
        answerKeyIds.value = articleIds.map(Number).filter(Number.isFinite)
    } catch (error) {
        console.error('加载答案数据失败:', error)
    } finally {
        loadingKeys.value = false
    }
}

function rememberAsSet(articleId) {
    const id = Number(articleId)
    if (!answerKeyIds.value.includes(id)) {
        answerKeyIds.value = [...answerKeyIds.value, id]
    }
}

async function saveAnswerKeys() {
    const article = selectedArticle.value
    if (!article || saving.value || !article.questions.length) return

    const rows = article.questions.map(question => ({
        questionId: question.questionId,
        answerText: question.answerText,
        autoGrade: Boolean(question.autoGrade)
    }))
    const hasContent = rows.some(row => row.answerText.trim() !== '' || row.autoGrade)
    if (!hasContent) {
        showToast('请先填写至少一道题的标准答案，或开启自动批阅', 'warn')
        return
    }

    saving.value = true
    try {
        await learningGateway.replaceAnswerKeys(article.id, rows)
        article.questions.forEach(question => {
            question.wasSet = String(question.answerText || '').trim() !== '' || Boolean(question.autoGrade)
        })
        snapshot.value = JSON.stringify(article.questions.map(rowsOf))
        rememberAsSet(article.id)
        showToast(`「${article.title}」答案已保存`, 'success')
    } catch (error) {
        showToast(error.message || '答案保存失败，请稍后重试', 'error')
    } finally {
        saving.value = false
    }
}

// 学科/学员变化后保证当前学科仍在授权范围内；切换学员时关闭旧文章，避免跨学员误改答案。
watch([subjectStats, () => props.selectedStudentId], ([stats], previous) => {
    const subjects = stats.map(item => item.subject)
    if (!subjects.includes(currentSubject.value)) {
        currentSubject.value = subjects[0] || ''
    }
    if (previous && previous[1] !== props.selectedStudentId) {
        selectedArticle.value = null
        snapshot.value = ''
        keyword.value = ''
        activeFilter.value = 'unset'
    }
}, { immediate: true })

// 已选文章不再属于当前学员授权范围时关闭编辑器
watch(scopedArticles, (articles) => {
    const article = selectedArticle.value
    if (!article) return
    if (!articles.some(blog => Number(blog.id) === Number(article.id))) {
        selectedArticle.value = null
        snapshot.value = ''
    }
})

onMounted(() => {
    loadAnswerKeysCache()
})
</script>

<style scoped>
.answer-panel {
    overflow: hidden;
}

.answer-head {
    align-items: flex-start;
}

.answer-head__chips {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.answer-head__copy {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.answer-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.answer-subjects {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.answer-subject {
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

.answer-subject i {
    color: var(--tone-color, var(--violet-2));
    font-size: 0.74rem;
}

.answer-subject:hover {
    color: var(--text-1);
    border-color: var(--tone-line, var(--line-strong));
}

.answer-subject.is-active {
    color: #fff;
    border-color: var(--tone-line, rgba(139, 92, 246, 0.4));
    background: linear-gradient(120deg, var(--tone-soft, rgba(139, 92, 246, 0.16)), rgba(255, 255, 255, 0.03));
    box-shadow: 0 14px 30px -24px var(--tone-color, var(--violet));
}

.answer-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
}

.answer-search {
    width: min(260px, 100%);
}

.answer-search .input {
    height: 40px;
    border-radius: var(--r-full);
    font-size: 0.82rem;
}

.answer-layout {
    display: grid;
    grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.3fr);
    gap: 16px;
    align-items: start;
}

.answer-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    max-height: 640px;
    overflow-y: auto;
    padding-right: 4px;
}

.answer-tree {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.answer-skeletons {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.answer-skeleton {
    height: 58px;
    border-radius: var(--r-sm);
}

.answer-editor {
    border-radius: var(--r-lg);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.02);
    padding: 18px;
    min-height: 320px;
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.answer-editor__loading {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.answer-editor__skeleton {
    height: 130px;
    border-radius: var(--r-md);
}

.answer-editor__error {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
}

.answer-editor__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
}

.answer-editor__head-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.answer-editor__loading-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
}

.answer-editor__title-wrap {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
}

.answer-editor__title {
    font-family: var(--font-display);
    font-size: 1.1rem;
    font-weight: 800;
    line-height: 1.3;
}

.answer-editor__meta {
    gap: 6px;
}

.answer-questions {
    display: flex;
    flex-direction: column;
    gap: 12px;
    /* 题号/题目卡片较多时限制高度并内部滚动 */
    max-height: min(58vh, 560px);
    overflow-y: auto;
    overscroll-behavior: contain;
    padding-right: 4px;
    scrollbar-width: thin;
}

.answer-card {
    padding: 14px;
    border-radius: var(--r-md);
    border: 1px solid var(--line);
    background: linear-gradient(160deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.012));
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.answer-card__head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.answer-card__index {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--tone-color, var(--violet-2));
    letter-spacing: 0.06em;
}

.answer-card__input {
    min-height: 84px;
    font-size: 0.86rem;
    line-height: 1.6;
    background: rgba(2, 4, 12, 0.35);
}

/* 自动批阅开关 */
.answer-switch {
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    user-select: none;
    padding: 4px 2px;
}

.switch-input {
    position: absolute;
    width: 1px;
    height: 1px;
    opacity: 0;
    pointer-events: none;
}

.switch-track {
    position: relative;
    width: 38px;
    height: 22px;
    border-radius: var(--r-full);
    background: rgba(255, 255, 255, 0.1);
    border: 1px solid var(--line-strong);
    transition: background var(--dur), border-color var(--dur);
    flex-shrink: 0;
}

.switch-thumb {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: var(--text-3);
    transition: transform var(--dur) var(--ease-spring), background var(--dur);
}

.switch-input:checked + .switch-track {
    background: var(--grad-primary);
    border-color: transparent;
}

.switch-input:checked + .switch-track .switch-thumb {
    transform: translateX(16px);
    background: #fff;
}

.switch-input:focus-visible + .switch-track {
    outline: 2px solid var(--cyan);
    outline-offset: 3px;
}

.answer-switch__text {
    font-size: 0.72rem;
    font-weight: 600;
    color: var(--text-2);
}

.answer-editor__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    padding-top: 14px;
    border-top: 1px solid var(--line);
}

.answer-editor__state {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

.answer-editor__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

/* 学科色调变量 */
.is-tone-cyan { --tone-color: var(--cyan); --tone-soft: rgba(34, 211, 238, 0.13); --tone-line: rgba(34, 211, 238, 0.34); }
.is-tone-violet { --tone-color: var(--violet-2); --tone-soft: rgba(139, 92, 246, 0.15); --tone-line: rgba(139, 92, 246, 0.36); }
.is-tone-amber { --tone-color: var(--amber); --tone-soft: rgba(251, 191, 36, 0.13); --tone-line: rgba(251, 191, 36, 0.34); }
.is-tone-pink { --tone-color: var(--pink); --tone-soft: rgba(244, 114, 182, 0.13); --tone-line: rgba(244, 114, 182, 0.34); }

.answer-search .input::-webkit-search-cancel-button {
    filter: invert(0.7);
}

@media (max-width: 1024px) {
    .answer-layout {
        grid-template-columns: minmax(0, 1fr);
    }

    .answer-list {
        max-height: none;
        overflow: visible;
    }

    /* 单列布局下跟随页面滚动，避免嵌套滚动 */
    .answer-questions {
        max-height: none;
        overflow: visible;
        padding-right: 0;
    }
}

@media (max-width: 760px) {
    .answer-body {
        padding: 18px;
    }

    .answer-editor__head-actions,
    .answer-editor__loading-head {
        width: 100%;
    }

    .answer-editor__actions {
        width: 100%;
    }

    .answer-editor__actions .btn {
        flex: 1 1 auto;
    }
}

@media (max-width: 420px) {
    .answer-subject {
        flex: 1 1 100%;
        justify-content: center;
    }
}
</style>

<style scoped>
/* ==========================================================================
   Riso Campus：纸白答案卡、墨线、实色学科块与开关。
   ========================================================================== */
.panel-title i {
    color: var(--vermilion);
}

.answer-head .chip {
    box-shadow: 2px 2px 0 var(--ink);
}

.answer-subject {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--text-2);
    box-shadow: 2px 2px 0 var(--ink);
}

.answer-subject:hover {
    color: var(--ink);
    border-color: var(--ink);
    background: var(--paper-2);
}

.answer-subject.is-active {
    color: var(--tone-text);
    border-color: var(--ink);
    background: var(--tone-color);
    box-shadow: 2px 2px 0 var(--ink);
}

.answer-subject.is-active i {
    color: var(--tone-text);
}

.answer-editor {
    border: 1.5px solid var(--ink);
    background: var(--paper-0);
    box-shadow: 3px 3px 0 var(--ink);
}

.answer-editor__title {
    color: var(--ink);
}

.answer-card {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.answer-card__index {
    color: var(--tone-color);
}

.answer-card .tag {
    background: var(--paper-2);
}

.answer-card__input {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
}

.switch-track {
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
}

.switch-thumb {
    background: var(--ink);
}

.switch-input:checked + .switch-track {
    border-color: var(--ink);
    background: var(--lime);
}

.switch-input:checked + .switch-track .switch-thumb {
    background: var(--ink);
}

.answer-switch__text {
    color: var(--ink);
}

.answer-editor__footer {
    border-top: 1.5px solid var(--ink);
}

/* 学科色 */
.is-tone-cyan { --tone-color: var(--teal); --tone-text: var(--paper-1); }
.is-tone-violet { --tone-color: var(--cobalt); --tone-text: var(--paper-1); }
.is-tone-amber { --tone-color: var(--butter); --tone-text: var(--ink); }
.is-tone-pink { --tone-color: var(--pink); --tone-text: var(--ink); }

.answer-search .input::-webkit-search-cancel-button {
    filter: none;
}
</style>
