<!-- src/views/admin/AdminContentView.vue -->
<template>
    <div class="admin-page courseware-view">
        <header class="panel cw-head">
            <div class="cw-head__copy">
                <span class="eyebrow">COURSEWARE CLAIMS</span>
                <h1 class="page-title cw-head__title">我的课件</h1>
                <p class="page-sub">
                    先认领学科或单篇课件：学科认领会自动覆盖该学科当前与之后同步的全部课件。业务页（权限、批阅、答案）只显示你认领的范围。
                </p>
            </div>
            <div class="cw-head__side">
                <div class="cw-head__stats">
                    <span class="chip chip--cyan">
                        <i aria-hidden="true" class="fas fa-book-open"></i>
                        认领 {{ mySubjectCount }} 门学科
                    </span>
                    <span class="chip chip--lime">
                        <i aria-hidden="true" class="fas fa-file-lines"></i>
                        认领 {{ myArticleCount }} 篇课件
                    </span>
                    <span v-if="unclaimedTotal > 0" class="chip chip--amber">
                        <i aria-hidden="true" class="fas fa-hand-holding-heart"></i>
                        待认领 {{ unclaimedTotal }} 篇
                    </span>
                </div>
                <button type="button" class="btn btn--primary cw-refresh"
                    :disabled="coursewareStore.loading || coursewareStore.syncing" @click="refreshWorkspace">
                    <i aria-hidden="true" class="fas"
                        :class="coursewareStore.loading || coursewareStore.syncing ? 'fa-circle-notch fa-spin' : 'fa-rotate'"></i>
                    {{ coursewareStore.syncing ? '同步中…' : coursewareStore.loading ? '刷新中…' : '刷新目录' }}
                </button>
            </div>
        </header>

        <div class="seg cw-tabs" role="tablist" aria-label="课件工作区切换">
            <button v-for="tab in tabs" :key="tab.id" type="button" role="tab" class="seg__btn cw-tab"
                :class="{ 'is-active': activeTab === tab.id }" :aria-selected="activeTab === tab.id"
                @click="switchTab(tab.id)">
                <i aria-hidden="true" :class="tab.icon"></i>
                {{ tab.label }}
                <span v-if="tab.count !== null" class="seg__count cw-tab__count">{{ tab.count }}</span>
            </button>
        </div>

        <!-- 我的课件 -->
        <section v-if="activeTab === 'mine'" class="panel cw-panel" aria-label="我的课件">
            <div class="panel-head cw-panel__head">
                <div>
                    <h2 class="panel-title">
                        <i aria-hidden="true" class="fas fa-book-bookmark"></i>
                        我的课件
                    </h2>
                    <p class="text-sm text-muted">按学科分组；展开学科查看课件，每篇课件都可单独释放（释放后回到「待认领」，可再次认领）。</p>
                </div>
                <button v-if="unclaimedTotal > 0" type="button" class="btn btn--soft btn--sm"
                    @click="switchTab('unclaimed')">
                    <i aria-hidden="true" class="fas fa-hand-holding-heart"></i>
                    去认领（{{ unclaimedTotal }}）
                </button>
            </div>

            <div class="panel-body cw-body">
                <div v-if="coursewareLoading" class="cw-skeletons" aria-hidden="true">
                    <div class="skeleton cw-skeleton"></div>
                    <div class="skeleton cw-skeleton"></div>
                    <div class="skeleton cw-skeleton"></div>
                </div>

                <EmptyState v-else-if="coursewareFailed" icon="fas fa-triangle-exclamation" title="课件目录加载失败"
                    :text="coursewareStore.error || '请稍后重试。'">
                    <button type="button" class="btn btn--soft btn--sm" @click="refreshWorkspace">
                        <i aria-hidden="true" class="fas fa-rotate"></i> 重新加载
                    </button>
                </EmptyState>

                <EmptyState v-else-if="catalogEmpty" icon="fas fa-box-open" title="还没有同步课件目录"
                    text="请确认静态课件目录 data/articles.json 可用后，点击「刷新目录」重试。" />

                <EmptyState v-else-if="mySubjectGroups.length === 0" icon="fas fa-hand-holding-heart"
                    title="你还没有认领任何课件"
                    text="到「待认领」里认领学科或单篇课件后，它们会出现在这里。">
                    <button type="button" class="btn btn--soft btn--sm" @click="switchTab('unclaimed')">
                        <i aria-hidden="true" class="fas fa-hand-holding-heart"></i> 去待认领
                    </button>
                </EmptyState>

                <div v-else class="cw-groups">
                    <article v-for="(group, index) in mySubjectGroups" :key="group.subject" class="cw-group"
                        :class="[`is-tone-${subjectTone(group.subject)}`, { 'is-open': isMySubjectOpen(group.subject) }]">
                        <header class="cw-group__head">
                            <button type="button" class="cw-group__toggle"
                                :aria-expanded="isMySubjectOpen(group.subject)"
                                :aria-controls="`cw-mine-body-${index}`"
                                @click="toggleMySubject(group.subject)">
                                <span class="cw-group__chevron"
                                    :class="{ 'is-collapsed': !isMySubjectOpen(group.subject) }">
                                    <i aria-hidden="true" class="fas fa-chevron-down"></i>
                                </span>
                                <span class="cw-group__dot" aria-hidden="true"></span>
                                <span class="cw-group__name" role="heading" aria-level="3">{{ group.subject }}</span>
                                <span class="chip" :class="group.claimedAsSubject ? 'chip--lime' : 'chip--amber'">
                                    {{ group.claimedAsSubject ? '已认领学科' : '单篇认领' }}
                                </span>
                                <span class="text-xs text-dim">{{ group.items.length }} 篇</span>
                            </button>
                            <button v-if="group.claimedAsSubject" type="button" class="btn btn--ghost btn--sm"
                                :disabled="Boolean(busyKey)"
                                @click="releaseSubject(group.subject)">
                                <i aria-hidden="true"
                                    class="fas"
                                    :class="isBusy(`subject:${group.subject}`) ? 'fa-circle-notch fa-spin' : 'fa-arrow-up-from-bracket'"></i>
                                {{ isBusy(`subject:${group.subject}`) ? '释放中…' : '释放学科' }}
                            </button>
                        </header>

                        <div v-show="isMySubjectOpen(group.subject)" :id="`cw-mine-body-${index}`"
                            class="cw-subject__body">
                            <ul class="cw-list">
                                <li v-for="item in group.items" :key="item.articleId" class="cw-item">
                                    <div class="cw-item__main">
                                        <span class="cw-item__title">{{ articleTitle(item) }}</span>
                                        <span class="cw-item__id mono">#{{ item.articleId }}</span>
                                        <span v-if="item.coveredBySubjectClaim" class="tag">学科覆盖</span>
                                        <span v-else-if="item.individuallyClaimed" class="tag">单篇认领</span>
                                    </div>
                                    <div class="cw-item__actions">
                                        <button type="button" class="btn btn--ghost btn--sm"
                                            title="释放后该课件回到待认领，可再次认领"
                                            :disabled="Boolean(busyKey)" @click="releaseArticle(item)">
                                            <i aria-hidden="true"
                                                class="fas"
                                                :class="isBusy(`article:${item.articleId}`) ? 'fa-circle-notch fa-spin' : 'fa-xmark'"></i>
                                            {{ isBusy(`article:${item.articleId}`) ? '释放中…' : '释放' }}
                                        </button>
                                        <a class="btn btn--soft btn--sm" :href="readHref(item.articleId)"
                                            target="_blank" rel="noopener">
                                            <i aria-hidden="true" class="fas fa-arrow-up-right-from-square"></i>
                                            打开阅读
                                        </a>
                                    </div>
                                </li>
                            </ul>
                        </div>
                    </article>
                </div>
            </div>
        </section>

        <!-- 待认领 -->
        <section v-else-if="activeTab === 'unclaimed'" class="panel cw-panel" aria-label="待认领课件">
            <div class="panel-head cw-panel__head">
                <div>
                    <h2 class="panel-title">
                        <i aria-hidden="true" class="fas fa-hand-holding-heart"></i>
                        待认领
                    </h2>
                    <p class="text-sm text-muted">认领学科后该科全部课件归你；也可以只认领单篇，或先打开阅读了解内容。</p>
                </div>
            </div>

            <div class="panel-body cw-body">
                <div v-if="coursewareLoading" class="cw-skeletons" aria-hidden="true">
                    <div class="skeleton cw-skeleton"></div>
                    <div class="skeleton cw-skeleton"></div>
                    <div class="skeleton cw-skeleton"></div>
                </div>

                <EmptyState v-else-if="coursewareFailed" icon="fas fa-triangle-exclamation" title="课件目录加载失败"
                    :text="coursewareStore.error || '请稍后重试。'">
                    <button type="button" class="btn btn--soft btn--sm" @click="refreshWorkspace">
                        <i aria-hidden="true" class="fas fa-rotate"></i> 重新加载
                    </button>
                </EmptyState>

                <EmptyState v-else-if="catalogEmpty" icon="fas fa-box-open" title="还没有同步课件目录"
                    text="请确认静态课件目录 data/articles.json 可用后，点击「刷新目录」重试。" />

                <EmptyState v-else-if="unclaimedGroups.length === 0" icon="fas fa-circle-check"
                    title="目录里的课件都已被认领" text="没有需要认领的课件，回到「我的课件」查看你的范围。" />

                <div v-else class="cw-claim-grid">
                    <article v-for="group in unclaimedGroups" :key="group.subject" class="cw-claim-card"
                        :class="[`is-tone-${subjectTone(group.subject)}`, { 'is-open': isUnclaimedOpen(group.subject) }]">
                        <header class="cw-claim-card__head">
                            <button type="button" class="cw-claim-card__toggle"
                                :aria-expanded="isUnclaimedOpen(group.subject)"
                                @click="toggleUnclaimed(group.subject)">
                                <span class="cw-claim-card__dot" aria-hidden="true"></span>
                                <span class="cw-claim-card__name">{{ group.subject }}</span>
                                <span class="tag">{{ group.items.length }} 篇待认领</span>
                                <i aria-hidden="true" class="fas cw-claim-card__caret"
                                    :class="isUnclaimedOpen(group.subject) ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                            </button>
                        </header>

                        <div class="cw-claim-card__body">
                            <div class="cw-claim-card__meta">
                                <span v-if="group.claimedByOthers.length" class="text-xs text-dim">
                                    其他管理员已认领：{{ group.claimedByOthers.join('、') }}
                                </span>
                                <span v-else class="text-xs text-dim">暂无管理员认领该学科</span>
                                <button type="button" class="btn btn--primary btn--sm"
                                    :disabled="Boolean(busyKey)" @click="claimSubject(group.subject)">
                                    <i aria-hidden="true"
                                        class="fas"
                                        :class="isBusy(`subject:${group.subject}`) ? 'fa-circle-notch fa-spin' : 'fa-hand-pointer'"></i>
                                    {{ isBusy(`subject:${group.subject}`) ? '认领中…' : '认领学科' }}
                                </button>
                            </div>

                            <ul v-if="isUnclaimedOpen(group.subject)" class="cw-list cw-list--compact cw-claim-list">
                                <li v-for="item in group.items" :key="item.articleId" class="cw-item">
                                    <div class="cw-item__main">
                                        <span class="cw-item__title">{{ articleTitle(item) }}</span>
                                        <span class="cw-item__id mono">#{{ item.articleId }}</span>
                                        <span v-if="item.excluded" class="tag cw-tag--released">已释放</span>
                                    </div>
                                    <div class="cw-item__actions">
                                        <button type="button" class="btn btn--soft btn--sm"
                                            :disabled="Boolean(busyKey)" @click="claimArticle(item)">
                                            <i aria-hidden="true"
                                                class="fas"
                                                :class="isBusy(`article:${item.articleId}`) ? 'fa-circle-notch fa-spin' : 'fa-hand-pointer'"></i>
                                            {{ isBusy(`article:${item.articleId}`) ? '认领中…' : item.excluded ? '重新认领' : '单篇认领' }}
                                        </button>
                                        <a class="btn btn--ghost btn--sm" :href="readHref(item.articleId)"
                                            target="_blank" rel="noopener">
                                            <i aria-hidden="true" class="fas fa-arrow-up-right-from-square"></i>
                                            打开阅读
                                        </a>
                                    </div>
                                </li>
                            </ul>
                        </div>
                    </article>
                </div>
            </div>
        </section>

        <!-- 设置答案 -->
        <section v-else class="cw-answers" aria-label="设置答案">
            <div class="panel cw-answer-bar">
                <div class="cw-answer-bar__switcher">
                    <span class="eyebrow">CURRENT LEARNER</span>
                    <StudentSwitcher @select="handleSelectStudent" />
                </div>
                <div class="cw-answer-bar__meta">
                    <span class="chip chip--cyan">
                        <i aria-hidden="true" class="fas fa-user-graduate"></i>
                        当前学员 · {{ currentStudentName }}
                    </span>
                    <span class="text-sm text-muted">
                        <i aria-hidden="true" class="fas fa-circle-info"></i>
                        只显示我的课件与在学科目
                    </span>
                </div>
            </div>

            <AnswerPanel :blog-data="blogData" :all-subjects="allSubjects" :students="adminStore.students"
                :selected-student-id="adminStore.currentStudentId" />
        </section>
    </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useArticleStore } from '@/stores/blogStore'
import { useAdminStore } from '@/stores/adminStore'
import { useAuthStore } from '@/stores/authStore'
import { useCoursewareStore } from '@/stores/coursewareStore'
import { ensureArticleData } from '@/composables/useAdminWorkspace'
import { showToast } from '@/utils/toast'
import EmptyState from '@/components/ui/EmptyState.vue'
import StudentSwitcher from '@/components/admin/StudentSwitcher.vue'
import AnswerPanel from '@/components/admin/AnswerPanel.vue'

const blogStore = useArticleStore()
const adminStore = useAdminStore()
const authStore = useAuthStore()
const coursewareStore = useCoursewareStore()

const activeTab = ref('mine')
const busyKey = ref('')
const openUnclaimedSubjects = ref([])
const openMySubjects = ref([])
let mySubjectsInitialized = false

const blogData = computed(() => blogStore.blogData)
const allSubjects = computed(() => {
    const subjects = new Set()
    blogStore.blogData.forEach(blog => {
        if (blog.series) subjects.add(blog.series)
    })
    return Array.from(subjects).sort((left, right) => left.localeCompare(right, 'zh-CN'))
})

const accountId = computed(() => authStore.currentUser?.accountId ?? null)
const currentStudentName = computed(() => adminStore.currentStudent?.username || '未选择学员')

const coursewareLoading = computed(() => !coursewareStore.loaded && !coursewareStore.error)
const coursewareFailed = computed(() => !coursewareStore.loaded && Boolean(coursewareStore.error))
const catalogEmpty = computed(() => coursewareStore.loaded && coursewareStore.catalogItems.length === 0)

const visibleIds = computed(() => coursewareStore.visibleArticleIds)

function groupItems(items) {
    const groups = new Map()
    for (const item of items) {
        const subject = String(item?.subject || '')
        if (!subject) continue
        if (!groups.has(subject)) groups.set(subject, [])
        groups.get(subject).push(item)
    }
    return groups
}

const mySubjectGroups = computed(() => {
    const groups = groupItems(coursewareStore.catalogItems.filter(item =>
        visibleIds.value.has(Number(item.articleId))))
    return Array.from(groups.entries())
        .map(([subject, items]) => ({
            subject,
            claimedAsSubject: coursewareStore.subjectClaims.includes(subject),
            items: [...items].sort((left, right) => Number(left.articleId) - Number(right.articleId))
        }))
        .sort((left, right) => left.subject.localeCompare(right.subject, 'zh-CN'))
})

const unclaimedGroups = computed(() => {
    const groups = groupItems(coursewareStore.unclaimedItems)
    return Array.from(groups.entries())
        .map(([subject, items]) => {
            const summary = coursewareStore.subjects.find(entry => entry.subject === subject) || null
            return {
                subject,
                items: [...items].sort((left, right) => Number(left.articleId) - Number(right.articleId)),
                claimedByOthers: Array.isArray(summary?.claimedByOthers) ? summary.claimedByOthers : []
            }
        })
        .sort((left, right) => left.subject.localeCompare(right.subject, 'zh-CN'))
})

// 学科折叠：默认展开第一个学科，保证首屏不空；目录刷新后保留用户展开状态。
watch(mySubjectGroups, (groups) => {
    const names = new Set(groups.map(group => group.subject))
    openMySubjects.value = openMySubjects.value.filter(subject => names.has(subject))
    if (!mySubjectsInitialized && groups.length > 0) {
        openMySubjects.value = [groups[0].subject]
        mySubjectsInitialized = true
    }
}, { immediate: true })

const mySubjectCount = computed(() => mySubjectGroups.value.length)
const myArticleCount = computed(() => mySubjectGroups.value.reduce((total, group) => total + group.items.length, 0))
const unclaimedTotal = computed(() => coursewareStore.unclaimedItems.length)

const tabs = computed(() => [
    { id: 'mine', label: '我的课件', icon: 'fas fa-book-bookmark', count: myArticleCount.value },
    { id: 'unclaimed', label: '待认领', icon: 'fas fa-hand-holding-heart', count: unclaimedTotal.value },
    { id: 'answers', label: '设置答案', icon: 'fas fa-key', count: null }
])

function subjectTone(subject) {
    if (!subject) return 'violet'
    if (subject.includes('初中英语')) return 'amber'
    if (subject.includes('化学')) return 'cyan'
    if (subject.includes('英语')) return 'violet'
    return 'pink'
}

function articleTitle(item) {
    const title = String(item?.title || '').trim()
    return title || `课件 #${item?.articleId ?? ''}`
}

function readHref(articleId) {
    return `/#/blog/${articleId}`
}

function isBusy(key) {
    return busyKey.value === key
}

function isUnclaimedOpen(subject) {
    return openUnclaimedSubjects.value.includes(subject)
}

function toggleUnclaimed(subject) {
    openUnclaimedSubjects.value = isUnclaimedOpen(subject)
        ? openUnclaimedSubjects.value.filter(item => item !== subject)
        : [...openUnclaimedSubjects.value, subject]
}

function isMySubjectOpen(subject) {
    return openMySubjects.value.includes(subject)
}

function toggleMySubject(subject) {
    openMySubjects.value = isMySubjectOpen(subject)
        ? openMySubjects.value.filter(item => item !== subject)
        : [...openMySubjects.value, subject]
}

function switchTab(tabId) {
    activeTab.value = tabId
}

async function runAction(key, action, successMessage) {
    if (busyKey.value) return
    busyKey.value = key
    try {
        await action()
        showToast(successMessage, 'success')
    } catch (error) {
        showToast(error?.message || '操作失败，请稍后重试', 'error')
    } finally {
        busyKey.value = ''
    }
}

function claimSubject(subject) {
    return runAction(
        `subject:${subject}`,
        () => coursewareStore.claimSubject(subject),
        `已认领学科「${subject}」，该科全部课件归你`
    )
}

function releaseSubject(subject) {
    return runAction(
        `subject:${subject}`,
        () => coursewareStore.releaseSubject(subject),
        `已释放学科「${subject}」的认领`
    )
}

function claimArticle(item) {
    const title = articleTitle(item)
    return runAction(
        `article:${item.articleId}`,
        () => coursewareStore.claimArticle(item.articleId),
        `已认领课件「${title}」`
    )
}

function releaseArticle(item) {
    const title = articleTitle(item)
    return runAction(
        `article:${item.articleId}`,
        () => coursewareStore.releaseArticle(item.articleId),
        `已释放《${title}》`
    )
}

async function refreshWorkspace() {
    try {
        await coursewareStore.refresh()
        showToast('课件目录已刷新', 'success')
    } catch (error) {
        showToast(error?.message || '刷新失败，请稍后重试', 'error')
    }
}

function handleSelectStudent(studentId) {
    adminStore.selectStudent(studentId)
}

async function ensureStudents() {
    if (adminStore.students.length > 0) return
    try {
        await adminStore.loadStudents()
        adminStore.selectInitialStudent(accountId.value)
    } catch (error) {
        showToast(error?.message || '加载学员列表失败，请稍后重试', 'error')
    }
}

watch(activeTab, tabId => {
    if (tabId === 'answers') ensureStudents()
})

onMounted(async () => {
    try {
        await ensureArticleData(blogStore)
        await coursewareStore.loadWorkspace(blogStore.blogData)
    } catch (error) {
        showToast(error?.message || '加载课件目录失败，请稍后重试', 'error')
    }
})
</script>

<style scoped>
.courseware-view {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

/* 顶部说明 */
.cw-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 20px;
    flex-wrap: wrap;
    padding: 20px 24px;
}

.cw-head__copy {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-width: 0;
}

.cw-head__title {
    font-family: var(--font-display);
    font-size: clamp(1.5rem, 2.4vw, 1.95rem);
    font-weight: 800;
    letter-spacing: -0.02em;
    color: var(--ink);
}.cw-head__side {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 12px;
}

.cw-head__stats {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    justify-content: flex-end;
}

.cw-head__stats .chip {
    box-shadow: 2px 2px 0 var(--ink);
}

.cw-refresh {
    flex-shrink: 0;
}

/* 内部标签：复用学员端 .seg 分段控件，仅补充滚动与对齐 */
.cw-tabs {
    align-self: flex-start;
    max-width: 100%;
    overflow-x: auto;
    scrollbar-width: none;
}

/* 面板 */
.cw-panel {
    overflow: hidden;
}

.cw-panel__head {
    align-items: flex-start;
    gap: 16px;
}

.cw-panel__head .panel-title i {
    color: var(--vermilion);
}

.cw-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.cw-skeletons {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.cw-skeleton {
    height: 72px;
    border-radius: var(--r-md);
}

/* 我的课件分组 */
.cw-groups {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.cw-group {
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
    overflow: hidden;
}

.cw-group__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    flex-wrap: wrap;
    padding-right: 12px;
    border-bottom: 1.5px solid transparent;
    background: var(--paper-2);
    transition: border-color var(--dur);
}

.cw-group.is-open .cw-group__head {
    border-bottom-color: var(--ink);
}

/* 标题行整行可点击：折叠展开整个学科课件列表 */
.cw-group__toggle {
    display: flex;
    align-items: center;
    gap: 9px;
    flex: 1 1 240px;
    min-width: 0;
    padding: 12px 16px;
    color: var(--ink);
    text-align: left;
    transition: background var(--dur);
}

.cw-group__toggle:hover {
    background: var(--paper-3);
}

.cw-group__chevron {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    font-size: 0.62rem;
    color: var(--text-3);
    flex-shrink: 0;
    transition: transform var(--dur) var(--ease-out);
}

.cw-group__chevron.is-collapsed {
    transform: rotate(-90deg);
}

.cw-group__dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    border: 1.5px solid var(--ink);
    background: var(--tone-color, var(--teal));
    flex-shrink: 0;
}

.cw-group__name {
    font-family: var(--font-display);
    font-size: 0.98rem;
    font-weight: 800;
    color: var(--ink);
}

.cw-group__toggle .chip {
    height: 24px;
    font-size: 0.68rem;
    box-shadow: 1px 1px 0 var(--ink);
}

/* 展开的学科课件列表限高内部滚动，页面不被撑高 */
.cw-subject__body {
    max-height: 320px;
    overflow-y: auto;
    overscroll-behavior: contain;
    padding-right: 4px;
    scrollbar-width: thin;
}

/* 课件列表 */
.cw-list {
    display: flex;
    flex-direction: column;
}

.cw-list--compact {
    border-top: 1.5px dashed var(--line-strong);
}

.cw-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    padding: 10px 16px;
}

.cw-item + .cw-item {
    border-top: 1px solid var(--line);
}

.cw-item__main {
    display: flex;
    align-items: center;
    gap: 9px;
    flex-wrap: wrap;
    min-width: 0;
}

.cw-item__title {
    font-size: 0.86rem;
    font-weight: 600;
    color: var(--ink);
    word-break: break-word;
}

.cw-item__id {
    font-size: 0.68rem;
    color: var(--text-4);
}

.cw-item .tag {
    background: var(--paper-2);
}

.cw-item__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

/* 待认领卡片 */
.cw-claim-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 16px;
    align-items: start;
}

.cw-claim-card {
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
    overflow: hidden;
}

.cw-claim-card__head {
    border-bottom: 1.5px solid var(--ink);
    background: var(--paper-2);
}

.cw-claim-card__toggle {
    display: flex;
    align-items: center;
    gap: 9px;
    width: 100%;
    padding: 12px 14px;
    color: var(--ink);
    text-align: left;
}

.cw-claim-card__dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    border: 1.5px solid var(--ink);
    background: var(--tone-color, var(--teal));
    flex-shrink: 0;
}

.cw-claim-card__name {
    font-family: var(--font-display);
    font-size: 0.95rem;
    font-weight: 800;
    color: var(--ink);
}

.cw-claim-card__toggle .tag {
    background: var(--paper-1);
}

.cw-claim-card__caret {
    margin-left: auto;
    font-size: 0.72rem;
    color: var(--text-3);
}

.cw-claim-card__body {
    padding: 12px 14px 14px;
    display: flex;
    flex-direction: column;
    gap: 10px;
}

/* 待认领展开的单篇列表同样限高内部滚动 */
.cw-claim-list {
    max-height: 320px;
    overflow-y: auto;
    overscroll-behavior: contain;
    padding-right: 4px;
    scrollbar-width: thin;
}

.cw-tag--released {
    background: var(--butter);
    border-color: var(--ink);
    color: var(--ink);
}

.cw-claim-card__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
}

/* 设置答案 */
.cw-answers {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.cw-answer-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
    padding: 14px 18px;
}

.cw-answer-bar__switcher {
    display: flex;
    flex-direction: column;
    gap: 6px;
    flex: 1 1 260px;
    max-width: 360px;
    min-width: 0;
}

.cw-answer-bar__meta {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

.cw-answer-bar__meta .chip {
    box-shadow: 2px 2px 0 var(--ink);
}

.cw-answer-bar__meta .text-sm {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

/* 学科色 */
.is-tone-cyan { --tone-color: var(--teal); --tone-text: var(--paper-1); }
.is-tone-violet { --tone-color: var(--cobalt); --tone-text: var(--paper-1); }
.is-tone-amber { --tone-color: var(--butter); --tone-text: var(--ink); }
.is-tone-pink { --tone-color: var(--pink); --tone-text: var(--ink); }

@media (max-width: 900px) {
    .cw-head__side {
        width: 100%;
        align-items: stretch;
    }

    .cw-head__stats {
        justify-content: flex-start;
    }

    .cw-claim-grid {
        grid-template-columns: minmax(0, 1fr);
    }
}

@media (max-width: 760px) {
    .courseware-view {
        gap: 16px;
    }

    .cw-head {
        padding: 16px;
    }

    .cw-body {
        padding: 16px;
    }

    .cw-tabs {
        width: 100%;
    }

    .cw-tab {
        flex: 1 1 0;
        justify-content: center;
        padding: 0 10px;
        font-size: 0.8rem;
    }

    .cw-item {
        padding: 10px 12px;
    }

    .cw-subject__body,
    .cw-claim-list {
        max-height: 280px;
    }

    .cw-item__actions {
        width: 100%;
        justify-content: flex-end;
    }

    .cw-answer-bar {
        padding: 14px;
    }

    .cw-answer-bar__switcher {
        max-width: none;
    }
}
</style>
