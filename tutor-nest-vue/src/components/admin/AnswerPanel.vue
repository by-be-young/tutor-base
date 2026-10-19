<!-- src/components/admin/AnswerPanel.vue -->
<template>
    <div class="panel answer-panel">
        <header class="panel-head answer-head">
            <div class="answer-head__copy">
                <h2 class="panel-title">
                    <i aria-hidden="true" class="fas fa-key"></i>
                    设置答案
                </h2>
                <p class="text-sm text-muted">选择文章填写标准答案，可开启自动批阅；一次保存全部题目。</p>
            </div>
            <span class="chip" :class="setArticleCount > 0 ? 'chip--lime' : 'chip--amber'">
                <i aria-hidden="true" class="fas" :class="setArticleCount > 0 ? 'fa-circle-check' : 'fa-hourglass-half'"></i>
                已设置 {{ setArticleCount }} / {{ totalArticles }} 篇
            </span>
        </header>

        <div class="panel-body answer-body">
            <div class="stat-grid answer-stats">
                <div class="stat-card stat-card--lime">
                    <div class="stat-value">{{ setArticleCount }}</div>
                    <div class="stat-label">已设置答案</div>
                    <div class="stat-sub">已配置参考答案的文章</div>
                </div>
                <div class="stat-card stat-card--amber">
                    <div class="stat-value">{{ unsetArticleCount }}</div>
                    <div class="stat-label">待设置答案</div>
                    <div class="stat-sub">尚未配置参考答案</div>
                </div>
                <div class="stat-card stat-card--violet">
                    <div class="stat-value">{{ totalArticles }}</div>
                    <div class="stat-label">内容库文章</div>
                    <div class="stat-sub">{{ allSubjects.length }} 个学科分组</div>
                </div>
            </div>

            <div class="answer-subjects" role="group" aria-label="学科筛选">
                <button v-for="item in subjectStats" :key="item.subject" type="button" class="answer-subject"
                    :class="[`is-tone-${item.tone}`, { 'is-active': currentSubject === item.subject }]"
                    :aria-pressed="currentSubject === item.subject" @click="selectSubject(item.subject)">
                    <i aria-hidden="true" class="fas fa-book-open"></i>
                    {{ item.subject }}
                    <span class="answer-subject__count">{{ item.setCount }}/{{ item.total }}</span>
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
                        <div class="skeleton answer-editor__skeleton"></div>
                        <div class="skeleton answer-editor__skeleton"></div>
                    </div>

                    <div v-else-if="selectedArticle.error" class="answer-editor__error">
                        <p class="field-error">
                            <i aria-hidden="true" class="fas fa-triangle-exclamation"></i>
                            {{ selectedArticle.error }}
                        </p>
                        <div class="row row--wrap">
                            <button type="button" class="btn btn--soft btn--sm" @click="reloadArticle">
                                <i aria-hidden="true" class="fas fa-rotate"></i> 重新加载
                            </button>
                            <button type="button" class="btn btn--ghost btn--sm" @click="openOriginal">
                                <i aria-hidden="true" class="fas fa-arrow-up-right-from-square"></i> 打开原文设置
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
                            <span class="chip" :class="dirty ? 'chip--amber' : 'chip--lime'">
                                <i aria-hidden="true" class="fas" :class="dirty ? 'fa-pen' : 'fa-circle-check'"></i>
                                {{ dirty ? '待保存' : '已同步' }}
                            </span>
                        </header>

                        <EmptyState v-if="selectedArticle.questions.length === 0" icon="fas fa-circle-question"
                            title="这篇文章里没有识别到题目"
                            text="可以使用「打开原文设置」进入整页答案设置模式。">
                            <button type="button" class="btn btn--soft btn--sm" @click="openOriginal">
                                打开原文设置
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

                        <footer class="answer-editor__footer">
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
                                <button type="button" class="btn btn--ghost btn--sm" @click="openOriginal">
                                    <i aria-hidden="true" class="fas fa-arrow-up-right-from-square"></i>
                                    打开原文
                                </button>
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
    blogData: { type: Array, default: () => [] },
    allSubjects: { type: Array, default: () => [] }
})

const router = useRouter()

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

const totalArticles = computed(() => props.blogData.length)

const setArticleCount = computed(() => props.blogData
    .filter(blog => answerKeyIds.value.includes(Number(blog.id))).length)

const unsetArticleCount = computed(() => Math.max(0, totalArticles.value - setArticleCount.value))

const currentTone = computed(() => subjectTone(currentSubject.value))

const subjectArticles = computed(() => props.blogData.filter(blog => blog.series === currentSubject.value))

const subjectStats = computed(() => props.allSubjects.map(subject => {
    const articles = props.blogData.filter(blog => blog.series === subject)
    const setCount = articles.filter(blog => answerKeyIds.value.includes(Number(blog.id))).length
    return { subject, tone: subjectTone(subject), total: articles.length, setCount }
}))

const filters = computed(() => {
    const articles = subjectArticles.value
    const setCount = articles.filter(blog => answerKeyIds.value.includes(Number(blog.id))).length
    return [
        { value: 'unset', label: '待设置', count: articles.length - setCount },
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
        articles = articles.filter(blog => !answerKeyIds.value.includes(Number(blog.id)))
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

    selectedArticle.value = { ...blog, questions: [], loading: true, error: '' }
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
        selectedArticle.value = { ...blog, questions, loading: false, error: '' }
        snapshot.value = JSON.stringify(questions.map(rowsOf))
    } catch (error) {
        selectedArticle.value = {
            ...blog,
            questions: [],
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

watch(() => props.allSubjects, (subjects) => {
    if (subjects.length > 0 && !currentSubject.value) {
        currentSubject.value = subjects[0]
    }
}, { immediate: true })

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

.answer-stats {
    gap: 12px;
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

.answer-subject__count {
    font-family: var(--font-mono);
    font-size: 0.64rem;
    color: var(--text-4);
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
}

@media (max-width: 760px) {
    .answer-body {
        padding: 18px;
    }

    .answer-stats {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .answer-editor__actions {
        width: 100%;
    }

    .answer-editor__actions .btn {
        flex: 1 1 auto;
    }
}

@media (max-width: 420px) {
    .answer-stats {
        grid-template-columns: minmax(0, 1fr);
    }

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

.answer-subject__count {
    color: inherit;
    opacity: 0.85;
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

/* 统计数字固定高度，避免数字变化造成布局抖动 */
.answer-stats .stat-value {
    display: flex;
    align-items: center;
    min-height: 2.3rem;
}
</style>
