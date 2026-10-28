<!-- src/components/admin/PermissionPanel.vue -->
<template>
    <div class="panel perm-panel">
        <header class="panel-head perm-head">
            <div class="perm-head__copy">
                <h2 class="panel-title">
                    <i class="fas fa-shield-halved" aria-hidden="true"></i>
                    权限管理
                </h2>
                <p class="text-sm text-muted">
                    按文章微调授权：勾选即授权当前学员访问，保存后立即生效。整科学科请在「学员科目」里设定。
                </p>
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

            <EmptyState v-else-if="scopedSubjects.length === 0" icon="fas fa-shield-halved"
                title="该学员还没有在学科目"
                text="先到「学员科目」为学员整科开通在学科目，再回到这里逐篇微调授权。" />

            <template v-else>
                <div class="perm-subjects" role="group" aria-label="学科切换">
                    <button v-for="subject in scopedSubjects" :key="subject" type="button" class="perm-subject"
                        :class="[`is-tone-${subjectTone(subject)}`, { 'is-active': currentSubject === subject }]"
                        :aria-pressed="currentSubject === subject" @click="selectSubject(subject)">
                        {{ subject }}
                    </button>
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
                    <span class="text-xs text-dim">按目录归档，逐篇勾选</span>
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
import { openedSubjects } from '@/utils/adminSubjects'
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

const currentTone = computed(() => subjectTone(currentSubject.value))

// 只显示学员已开通（至少授权过一篇文章）的学科，避免看到未开通科目。
// 保留当前正在编辑的学科：管理员把某科最后一篇取消勾选时，目录树不会突然跳走。
const scopedSubjects = computed(() => {
    const opened = new Set(openedSubjects({
        subjects: props.allSubjects,
        articles: props.blogData,
        permissions: currentPermissions.value
    }))
    return props.allSubjects.filter(subject => {
        if (subject === currentSubject.value) {
            return props.blogData.some(blog => blog.series === subject)
        }
        return opened.has(subject)
    })
})

const subjectArticles = computed(() => props.blogData.filter(blog => blog.series === currentSubject.value))

// 筛选标签右侧保留数量（未授权/已授权/全部），只针对当前学科
const filters = computed(() => {
    const articles = subjectArticles.value
    const authorized = articles.filter(blog => currentPermissions.value.includes(Number(blog.id))).length
    return [
        { value: 'unauthorized', label: '未授权', count: articles.length - authorized },
        { value: 'authorized', label: '已授权', count: authorized },
        { value: 'all', label: '全部', count: articles.length }
    ]
})

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

function handlePermissionToggle(blogId, checked) {
    adminStore.updatePermissions(blogId, checked)
    emit('permission-change')
}

function handlePermissionToggleMany(blogIds, checked) {
    blogIds.forEach(blogId => adminStore.updatePermissions(blogId, checked))
    emit('permission-change')
}

// 学科范围跟着学员授权变化：当前学科被整科取消后回退到第一个仍在学的学科
watch(scopedSubjects, (subjects) => {
    if (subjects.length === 0) {
        currentSubject.value = ''
        return
    }
    if (!subjects.includes(currentSubject.value)) {
        currentSubject.value = subjects[0]
    }
}, { immediate: true })

// 切换学员时回到「全部」，避免上一名学员的筛选状态影响新学员的判断
watch(() => props.selectedStudentId, () => {
    activeFilter.value = 'all'
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

.panel-title i {
    color: var(--vermilion);
}

.perm-skeletons {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.perm-skeleton {
    height: 62px;
    border-radius: var(--r-md);
}

/* 学科切换：只做导航，不展示任何统计 */
.perm-subjects {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    margin-bottom: 16px;
}

.perm-subject {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 36px;
    padding: 0 14px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-1);
    color: var(--text-2);
    font-size: 0.8rem;
    font-weight: 700;
    white-space: nowrap;
    box-shadow: 2px 2px 0 var(--ink);
    transition: transform var(--dur) var(--ease-out), background var(--dur);
}

.perm-subject:hover {
    transform: translate(-1px, -1px);
    background: var(--paper-2);
    color: var(--ink);
}

.perm-subject.is-active {
    background: var(--tone-color, var(--cobalt));
    border-color: var(--ink);
    color: var(--tone-text, var(--paper-1));
}

.perm-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 14px;
}

.perm-tree {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
    align-items: start;
    /* 展开多个文件夹时限制高度，内部滚动，避免整页被撑高 */
    max-height: 640px;
    overflow-y: auto;
    overscroll-behavior: contain;
    padding-right: 4px;
    scrollbar-width: thin;
}

.perm-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    flex-wrap: wrap;
    padding: 16px 24px;
    border-top: 1.5px solid var(--ink);
    background: var(--paper-2);
}

.perm-footer__info {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
}

.perm-footer .chip {
    box-shadow: 2px 2px 0 var(--ink);
}

.is-tone-cyan { --tone-color: var(--teal); --tone-text: var(--paper-1); }
.is-tone-violet { --tone-color: var(--cobalt); --tone-text: var(--paper-1); }
.is-tone-amber { --tone-color: var(--butter); --tone-text: var(--ink); }
.is-tone-pink { --tone-color: var(--pink); --tone-text: var(--ink); }

@media (min-width: 1200px) {
    .perm-tree {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 12px;
    }
}

@media (max-width: 1200px) {
    /* 窄屏下改为页面整体滚动，避免嵌套滚动难以操作 */
    .perm-tree {
        max-height: none;
        overflow: visible;
        padding-right: 0;
    }
}

@media (max-width: 760px) {
    .perm-head {
        align-items: center;
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
    .perm-subject {
        flex: 1 1 100%;
        justify-content: center;
    }
}
</style>