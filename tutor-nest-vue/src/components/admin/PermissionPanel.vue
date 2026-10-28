<!-- src/components/admin/PermissionPanel.vue -->
<template>
    <div class="panel perm-panel">
        <header class="panel-head perm-head">
            <div class="perm-head__copy">
                <h2 class="panel-title">
                    <i class="fas fa-shield-halved" aria-hidden="true"></i>
                    权限管理
                </h2>
                <p class="text-sm text-muted">勾选文章即授权当前学员访问，保存后立即生效。</p>
            </div>

            <div class="perm-ring" role="img" :aria-label="`已授权 ${grantedCount} / ${totalArticles} 篇文章`">
                <svg viewBox="0 0 120 120" aria-hidden="true">
                    <circle class="perm-ring__track" cx="60" cy="60" r="52" />
                    <circle class="perm-ring__bar" cx="60" cy="60" r="52" :stroke-dasharray="ringLength"
                        :stroke-dashoffset="ringOffset" />
                </svg>
                <span class="perm-ring__center">
                    <strong>{{ grantedCount }}</strong>
                    <small>/ {{ totalArticles }}</small>
                    <em>已授权</em>
                </span>
            </div>
        </header>

        <div class="panel-body perm-body">
            <EmptyState v-if="!selectedStudentId" icon="fas fa-user-graduate" title="尚未选择学员"
                text="在顶部切换或新增学员后，即可配置内容授权。" />

            <div v-else-if="allSubjects.length === 0" class="perm-skeletons" aria-hidden="true">
                <div class="skeleton perm-skeleton"></div>
                <div class="skeleton perm-skeleton"></div>
                <div class="skeleton perm-skeleton"></div>
            </div>

            <template v-else>
                <p class="perm-scope-hint">
                    <i aria-hidden="true" class="fas fa-circle-info"></i>
                    学员通常只上其中几门科目：先点学科卡右侧按钮整科开通，再在下方按目录微调具体文章。
                </p>

                <div class="perm-subjects">
                    <div v-for="item in visibleSubjectCards" :key="item.subject" class="perm-subject"
                        :class="[`is-tone-${item.tone}`, `is-stage-${item.stage}`, { 'is-active': currentSubject === item.subject }]">
                        <button type="button" class="perm-subject__open" :aria-pressed="currentSubject === item.subject"
                            :aria-label="`查看 ${item.subject} 的文章授权`" @click="selectSubject(item.subject)">
                            <span class="perm-subject__name">
                                {{ item.subject }}
                                <span class="perm-subject__stage">{{ item.stageText }}</span>
                            </span>
                            <span class="perm-subject__count">{{ item.granted }}<em>/{{ item.total }}</em></span>
                            <span class="progress perm-subject__bar">
                                <span class="progress__bar" :style="{ width: `${item.percent}%` }"></span>
                            </span>
                        </button>
                        <button type="button" class="perm-subject__toggle" :disabled="item.total === 0"
                            :aria-label="`${item.stage === 'active' ? '取消' : '开通'} ${item.subject} 全部文章`"
                            :title="item.stage === 'active' ? '取消整科授权' : `开通整科 ${item.total} 篇`"
                            @click="toggleSubject(item.subject)">
                            <i aria-hidden="true" class="fas"
                                :class="item.stage === 'active' ? 'fa-xmark' : item.stage === 'partial' ? 'fa-plus' : 'fa-check'"></i>
                            {{ item.stage === 'active' ? '取消整科' : item.stage === 'partial' ? '补全整科' : '整科开通' }}
                        </button>
                    </div>
                </div>

                <div class="perm-toolbar">
                    <div class="seg" role="group" aria-label="授权状态筛选">
                        <button v-for="filter in filters" :key="filter.value" type="button"
                            class="seg__btn" :class="{ 'is-active': activeFilter === filter.value }"
                            :aria-pressed="activeFilter === filter.value" @click="activeFilter = filter.value">
                            {{ filter.label }}
                            <span class="seg__count">{{ filter.count }}</span>
                        </button>
                    </div>
                    <label class="perm-only-active" :class="{ 'is-disabled': !hasActiveSubjects }"
                        :title="hasActiveSubjects ? '只显示学员已开通的学科' : '该学员还没有开通任何学科'">
                        <input v-model="onlyActive" type="checkbox" class="perm-only-active__input"
                            :disabled="!hasActiveSubjects" />
                        只看在学科目
                    </label>
                    <span class="text-xs text-dim">当前学科共 {{ subjectArticles.length }} 篇 · 按目录归档</span>
                </div>

                <EmptyState v-if="!currentSubject" icon="fas fa-folder-open" title="未选择学科"
                    text="先在上方选择一个学科，再为学员分配文章权限。" />

                <EmptyState v-else-if="filteredArticles.length === 0" icon="fas fa-filter"
                    :title="activeFilter === 'authorized' ? '该学科还没有已授权文章' : '该筛选条件下暂无文章'"
                    text="切换筛选条件或直接浏览全部文章。">
                    <button type="button" class="btn btn--soft btn--sm" @click="activeFilter = 'all'">
                        查看全部
                    </button>
                </EmptyState>

                <div v-else class="perm-tree">
                    <PermissionTreeNode v-for="node in treeData" :key="node.name + (node.isFile ? node.blogId : '')"
                        :node="node" :permissions="currentPermissions" :tone="currentTone"
                        :initially-expanded="true" @toggle="handlePermissionToggle"
                        @toggle-many="handlePermissionToggleMany" />
                </div>
            </template>
        </div>

        <footer class="perm-footer">
            <div class="perm-footer__info">
                <span class="chip" :class="dirty ? 'chip--amber' : 'chip--lime'">
                    <i aria-hidden="true" class="fas" :class="dirty ? 'fa-pen' : 'fa-circle-check'"></i>
                    {{ dirty ? '有未保存改动' : '已同步' }}
                </span>
                <span class="text-sm text-muted">
                    已开通 <strong class="perm-footer__num">{{ activeSubjectCount }}</strong> / {{ scope.length }} 门学科
                </span>
            </div>
            <button type="button" class="btn btn--primary" :disabled="!dirty || saving"
                @click="emit('save-permissions')">
                <i aria-hidden="true" class="fas" :class="saving ? 'fa-circle-notch fa-spin' : 'fa-cloud-arrow-up'"></i>
                {{ saving ? '保存中…' : '保存授权' }}
            </button>
        </footer>
    </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { activeSubjects, buildSubjectScope, stageLabel, subjectToggleIds } from '@/utils/adminSubjects'
import EmptyState from '@/components/ui/EmptyState.vue'
import PermissionTreeNode from './PermissionTreeNode.vue'

const props = defineProps({
    students: { type: Array, default: () => [] },
    selectedStudentId: { type: [String, Number], default: null },
    blogData: { type: Array, default: () => [] },
    allSubjects: { type: Array, default: () => [] },
    dirty: { type: Boolean, default: false },
    saving: { type: Boolean, default: false }
})

const emit = defineEmits(['permission-change', 'save-permissions'])

const adminStore = useAdminStore()

const currentSubject = ref('')
const activeFilter = ref('all')
const onlyActive = ref(false)

const RING_RADIUS = 52
const ringLength = 2 * Math.PI * RING_RADIUS

function subjectTone(subject) {
    if (!subject) return 'violet'
    if (subject.includes('初中英语')) return 'amber'
    if (subject.includes('化学')) return 'cyan'
    if (subject.includes('英语')) return 'violet'
    return 'pink'
}

const currentPermissions = computed(() => {
    const student = props.students.find(item => String(item.id) === String(props.selectedStudentId))
    return student?.permissions || []
})

const totalArticles = computed(() => props.blogData.length)
const grantedCount = computed(() => currentPermissions.value.length)

const ringOffset = computed(() => {
    const ratio = totalArticles.value ? Math.min(1, grantedCount.value / totalArticles.value) : 0
    return ringLength * (1 - ratio)
})

const subjectStats = computed(() => {
    const scope = buildSubjectScope({
        subjects: props.allSubjects,
        articles: props.blogData,
        permissions: currentPermissions.value
    })
    const grantedBySubject = new Map(scope.map(item => [item.subject, item]))
    return props.allSubjects.map(subject => {
        const stat = grantedBySubject.get(subject)
            || { subject, total: 0, granted: 0, stage: 'inactive' }
        return {
            ...stat,
            tone: subjectTone(subject),
            stageText: stageLabel(stat.stage),
            percent: stat.total ? Math.round((stat.granted / stat.total) * 100) : 0
        }
    })
})

// 学员实际在学的科目（有任意已授权文章），用于「只看在学科目」过滤
const scope = computed(() => buildSubjectScope({
    subjects: props.allSubjects,
    articles: props.blogData,
    permissions: currentPermissions.value
}))
const subjectStatusMap = computed(() => new Map(scope.value.map(item => [item.subject, item.stage])))
const activeSubjectCount = computed(() => activeSubjects(scope.value).length)
const hasActiveSubjects = computed(() => activeSubjectCount.value > 0)

const currentTone = computed(() => subjectTone(currentSubject.value))

const subjectArticles = computed(() => props.blogData.filter(blog => blog.series === currentSubject.value))

const filters = computed(() => {
    const articles = subjectArticles.value
    const authorized = articles.filter(blog => currentPermissions.value.includes(Number(blog.id))).length
    return [
        { value: 'unauthorized', label: '未授权', count: articles.length - authorized },
        { value: 'authorized', label: '已授权', count: authorized },
        { value: 'all', label: '全部', count: articles.length }
    ]
})

const visibleSubjects = computed(() => (onlyActive.value
    ? props.allSubjects.filter(subject => subjectStatusMap.value.get(subject) !== 'inactive')
    : props.allSubjects))

// 学科卡片：开启「只看在学科目」后只显示学员在学的科目
const visibleSubjectCards = computed(() => (onlyActive.value
    ? subjectStats.value.filter(item => item.stage !== 'inactive')
    : subjectStats.value))

const filteredArticles = computed(() => {
    let articles = subjectArticles.value

    if (activeFilter.value === 'authorized') {
        articles = articles.filter(blog => currentPermissions.value.includes(Number(blog.id)))
    } else if (activeFilter.value === 'unauthorized') {
        articles = articles.filter(blog => !currentPermissions.value.includes(Number(blog.id)))
    }

    return articles
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
}

/** 整科开通 / 补全 / 取消：只改本地勾选，仍需点「保存授权」写入后端。 */
function toggleSubject(subject) {
    const { ids, checked } = subjectToggleIds({
        articles: props.blogData,
        permissions: currentPermissions.value,
        subject
    })
    if (ids.length === 0) return
    handlePermissionToggleMany(ids, checked)
    currentSubject.value = subject
}

function handlePermissionToggle(blogId, checked) {
    adminStore.updatePermissions(blogId, checked)
    emit('permission-change')
}

function handlePermissionToggleMany(blogIds, checked) {
    blogIds.forEach(blogId => adminStore.updatePermissions(blogId, checked))
    emit('permission-change')
}

// 初始化默认科目：优先选中第一个确实有文章的学科，首屏直接渲染文章树
watch(() => [props.allSubjects, props.blogData], ([subjects, articles]) => {
    if (!subjects.length) return
    const hasArticles = subject => articles.some(blog => blog.series === subject)
    if (currentSubject.value && hasArticles(currentSubject.value)) return
    currentSubject.value = subjects.find(hasArticles) || subjects[0]
}, { immediate: true })

// 切换学员时回到「全部」，避免上一名学员的筛选状态影响新学员的判断
watch(() => props.selectedStudentId, () => {
    activeFilter.value = 'all'
    onlyActive.value = false
})

// 「只看在学科目」过滤后，当前学科可能不在列表里，需要回退到第一个可见学科
watch([visibleSubjects, () => props.selectedStudentId], ([subjects]) => {
    if (!subjects.length) return
    if (subjects.includes(currentSubject.value)) return
    currentSubject.value = subjects.find(subject => props.blogData.some(blog => blog.series === subject)) || subjects[0]
}, { immediate: true })

// 当前学科被整科取消后，若开启了「只看在学科目」，保持在原学科但仍展示全部文章以便回退
watch(scope, (items) => {
    if (!onlyActive.value) return
    if (activeSubjects(items).length === 0 && currentSubject.value) {
        onlyActive.value = false
    }
})
</script>

<style scoped>
.perm-panel {
    overflow: hidden;
}

.perm-head {
    align-items: flex-start;
    gap: 18px;
}

.perm-head__copy {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
}

.perm-head__copy .panel-title i {
    font-size: 0.98em;
}

/* 环形授权指示 */
.perm-ring {
    position: relative;
    width: 108px;
    height: 108px;
    flex-shrink: 0;
}

.perm-ring svg {
    width: 100%;
    height: 100%;
    transform: rotate(-90deg);
}

.perm-ring__track {
    fill: none;
    stroke: rgba(255, 255, 255, 0.08);
    stroke-width: 9;
}

.perm-ring__bar {
    fill: none;
    stroke: var(--cyan);
    stroke-width: 9;
    stroke-linecap: round;
    filter: drop-shadow(0 0 6px rgba(34, 211, 238, 0.55));
    transition: stroke-dashoffset var(--dur-slow) var(--ease-out);
}

.perm-ring__center {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    line-height: 1;
}

.perm-ring__center strong {
    font-family: var(--font-display);
    font-size: 1.45rem;
    font-weight: 800;
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
}

.perm-ring__center small {
    font-family: var(--font-mono);
    font-size: 0.66rem;
    color: var(--text-3);
    margin-top: -2px;
}

.perm-ring__center em {
    font-style: normal;
    font-size: 0.58rem;
    letter-spacing: 0.16em;
    color: var(--text-4);
    margin-top: 5px;
}

/* 学科统计卡 */
.perm-skeletons {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.perm-skeleton {
    height: 62px;
    border-radius: var(--r-md);
}

.perm-subjects {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 12px;
    margin-bottom: 18px;
}

.perm-subject {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 10px 12px 12px;
    border-radius: var(--r-md);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.03);
    text-align: left;
    position: relative;
    overflow: hidden;
    transition: border-color var(--dur), background var(--dur), transform var(--dur) var(--ease-out);
}

.perm-subject::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: var(--tone-color, var(--violet-2));
    opacity: 0.55;
}

/* 未开通的学科弱化显示，方便一眼看出学员不属于这一科 */
.perm-subject.is-stage-inactive {
    opacity: 0.62;
}

.perm-subject:hover {
    transform: translateY(-2px);
    border-color: var(--tone-line, var(--line-strong));
    background: rgba(255, 255, 255, 0.05);
    opacity: 1;
}

.perm-subject.is-active {
    border-color: var(--tone-line, rgba(139, 92, 246, 0.4));
    background: linear-gradient(140deg, var(--tone-soft, rgba(139, 92, 246, 0.14)), rgba(255, 255, 255, 0.02) 70%);
    box-shadow: 0 18px 40px -30px var(--tone-color, var(--violet));
    opacity: 1;
}

.perm-subject__open {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
    padding: 0;
    border: none;
    background: none;
    text-align: left;
    color: inherit;
}

.perm-subject__name {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    font-size: 0.86rem;
    font-weight: 700;
    color: var(--text-1);
}

.perm-subject__stage {
    display: inline-flex;
    align-items: center;
    height: 20px;
    padding: 0 8px;
    border-radius: var(--r-full);
    border: 1px solid var(--line-strong);
    background: rgba(255, 255, 255, 0.04);
    color: var(--text-3);
    font-size: 0.62rem;
    font-weight: 700;
    white-space: nowrap;
}

.perm-subject.is-stage-active .perm-subject__stage {
    border-color: var(--tone-line, var(--line-strong));
    background: var(--tone-soft, rgba(163, 230, 53, 0.16));
    color: var(--tone-color, var(--text-1));
}

.perm-subject.is-stage-partial .perm-subject__stage {
    color: var(--amber);
    border-color: var(--amber);
}

.perm-subject__toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    height: 28px;
    padding: 0 10px;
    border-radius: var(--r-full);
    border: 1px solid var(--line-strong);
    background: rgba(255, 255, 255, 0.04);
    color: var(--text-2);
    font-size: 0.7rem;
    font-weight: 700;
    transition: background var(--dur), color var(--dur), border-color var(--dur);
}

.perm-subject__toggle:hover:not(:disabled) {
    color: var(--text-1);
    border-color: var(--tone-line, var(--line-strong));
    background: var(--tone-soft, rgba(255, 255, 255, 0.08));
}

.perm-subject__toggle:disabled {
    opacity: 0.45;
    cursor: not-allowed;
}

.perm-subject.is-stage-active .perm-subject__toggle {
    color: var(--text-3);
}

.perm-scope-hint {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    color: var(--text-3);
    font-size: 0.78rem;
}

.perm-only-active {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--text-2);
    font-size: 0.78rem;
    cursor: pointer;
    user-select: none;
}

.perm-only-active.is-disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.perm-only-active__input {
    accent-color: var(--vermilion);
}

.perm-subject__count {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--tone-color, var(--text-2));
    font-variant-numeric: tabular-nums;
}

.perm-subject__count em {
    font-style: normal;
    font-weight: 500;
    color: var(--text-4);
}

.perm-subject__bar {
    display: block;
    height: 5px;
}

.perm-subject__bar .progress__bar {
    background: var(--tone-color, var(--violet-2));
}

.perm-subject__bar .progress__bar::after {
    display: none;
}

/* 工具栏 */
.perm-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 14px;
}

/* 树 */
.perm-tree {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

/* 底部保存条 */
.perm-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    flex-wrap: wrap;
    padding: 16px 24px;
    border-top: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.015);
}

.perm-footer__info {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
}

.perm-footer__num {
    color: var(--text-1);
    font-family: var(--font-mono);
}

/* 学科色调变量 */
.is-tone-cyan { --tone-color: var(--cyan); --tone-soft: rgba(34, 211, 238, 0.13); --tone-line: rgba(34, 211, 238, 0.34); }
.is-tone-violet { --tone-color: var(--violet-2); --tone-soft: rgba(139, 92, 246, 0.15); --tone-line: rgba(139, 92, 246, 0.36); }
.is-tone-amber { --tone-color: var(--amber); --tone-soft: rgba(251, 191, 36, 0.13); --tone-line: rgba(251, 191, 36, 0.34); }
.is-tone-pink { --tone-color: var(--pink); --tone-soft: rgba(244, 114, 182, 0.13); --tone-line: rgba(244, 114, 182, 0.34); }

@media (max-width: 760px) {
    .perm-head {
        align-items: center;
    }

    .perm-ring {
        width: 86px;
        height: 86px;
    }

    .perm-ring__center strong {
        font-size: 1.2rem;
    }

    .perm-subjects {
        grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    }

    .perm-body {
        padding: 18px;
    }

    .perm-footer {
        padding: 14px 18px;
    }

    .perm-footer .btn {
        width: 100%;
    }
}

@media (max-width: 420px) {
    .perm-subjects {
        grid-template-columns: minmax(0, 1fr);
    }
}
</style>

<style scoped>
/* ==========================================================================
   Riso Campus：墨线卡片 + 学科纯色块 + 印章式状态。
   ========================================================================== */
.panel-title i {
    color: var(--vermilion);
}

.perm-ring__track {
    stroke: var(--paper-3);
}

.perm-ring__bar {
    stroke: var(--vermilion);
    filter: none;
    transition: stroke-dashoffset var(--dur-slow) var(--ease-out);
}

.perm-ring__center strong {
    color: var(--ink);
}

.perm-ring__center small,
.perm-ring__center em {
    color: var(--text-3);
}

.perm-subject {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
}

.perm-subject__toggle,
.perm-subject__stage {
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
    color: var(--ink);
}

.perm-subject::before {
    width: 5px;
    opacity: 1;
    background: var(--tone-color);
}

.perm-subject:hover {
    transform: translate(-1px, -1px);
    border-color: var(--ink);
    background: var(--paper-2);
    box-shadow: 4px 4px 0 var(--ink);
}

.perm-subject.is-active {
    border-color: var(--ink);
    background: var(--tone-color);
    color: var(--tone-text);
    box-shadow: 4px 4px 0 var(--ink);
    opacity: 1;
}

.perm-subject.is-active .perm-subject__name,
.perm-subject.is-active .perm-subject__count {
    color: var(--tone-text);
}

.perm-subject.is-active .perm-subject__stage {
    border-color: var(--ink);
    background: var(--paper-1);
    color: var(--ink);
}

.perm-subject.is-active .perm-subject__toggle {
    border-color: var(--ink);
    background: var(--paper-1);
    color: var(--ink);
}

.perm-subject.is-active .perm-subject__count em {
    color: var(--tone-text);
    opacity: 0.72;
}

.perm-subject.is-active .progress {
    background: var(--paper-1);
}

.perm-subject__bar .progress__bar {
    background: var(--tone-color);
}

.perm-footer {
    border-top: 1.5px solid var(--ink);
    background: var(--paper-2);
}

.perm-footer .chip {
    box-shadow: 2px 2px 0 var(--ink);
}

.perm-footer__num {
    color: var(--ink);
}

/* 学科色：化学=青绿，英语=钴蓝，初中英语=奶油黄，其他=粉 */
.is-tone-cyan { --tone-color: var(--teal); --tone-text: var(--paper-1); }
.is-tone-violet { --tone-color: var(--cobalt); --tone-text: var(--paper-1); }
.is-tone-amber { --tone-color: var(--butter); --tone-text: var(--ink); }
.is-tone-pink { --tone-color: var(--pink); --tone-text: var(--ink); }

/* 宽屏双列：目录树按两列铺排，减少右半屏大片空白 */
.perm-tree {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
    align-items: start;
}

@media (min-width: 1200px) {
    .perm-tree {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 12px;
    }
}
</style>
