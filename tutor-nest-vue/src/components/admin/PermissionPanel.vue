<!-- src/components/admin/PermissionPanel.vue -->
<template>
    <div>
        <h2 class="admin-title">权限管理</h2>

        <div class="admin-controls">
            <div class="control-group">
                <label><i class="fas fa-book"></i> 选择科目：</label>
                <div class="subject-btn-group">
                    <button v-for="subject in allSubjects" :key="subject" class="subject-btn"
                        :class="{ 'is-active': currentSubject === subject }" @click="selectSubject(subject)">
                        {{ subject }}
                    </button>
                </div>
            </div>

            <div class="control-group">
                <label><i class="fas fa-filter"></i> 显示：</label>
                <div class="subject-btn-group">
                    <button v-for="filter in filters" :key="filter.value" class="subject-btn"
                        :class="{ 'is-active': activeFilter === filter.value }" @click="activeFilter = filter.value">
                        {{ filter.label }}
                    </button>
                </div>
            </div>
        </div>

        <div class="admin-tree">
            <p v-if="!selectedStudentId" class="tree-placeholder">请先选择学生</p>
            <p v-else-if="!currentSubject" class="tree-placeholder">请选择科目</p>
            <p v-else-if="filteredArticles.length === 0" class="tree-placeholder">
                该筛选条件下暂无文章
            </p>
            <template v-else>
                <PermissionTreeNode v-for="node in treeData" :key="node.name + (node.isFile ? node.blogId : '')"
                    :node="node" :depth="0" :permissions="currentPermissions"
                    :initially-expanded="activeFilter === 'unauthorized'"
                    @toggle="handlePermissionToggle" />
            </template>
        </div>

        <div class="admin-tip">
            <i class="fas fa-info-circle"></i>
            勾选文章即授权该学生访问，取消勾选则禁止。修改后请点击「保存」。
        </div>
    </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import PermissionTreeNode from './PermissionTreeNode.vue'

const props = defineProps({
    students: { type: Array, default: () => [] },
    selectedStudentId: { type: [String, Number], default: null },
    blogData: { type: Array, default: () => [] },
    allSubjects: { type: Array, default: () => [] }
})

const emit = defineEmits(['permission-change'])

const adminStore = useAdminStore()

const currentSubject = ref('')
const activeFilter = ref('unauthorized')

const filters = [
    { value: 'unauthorized', label: '未授权' },
    { value: 'authorized', label: '已授权' },
    { value: 'all', label: '全部' }
]

const currentPermissions = computed(() => {
    const student = props.students.find(s => String(s.id) === String(props.selectedStudentId))
    return student?.permissions || []
})

const subjectArticles = computed(() => {
    return props.blogData.filter(b => b.series === currentSubject.value)
})

const filteredArticles = computed(() => {
    let articles = subjectArticles.value

    if (activeFilter.value === 'authorized') {
        articles = articles.filter(b => currentPermissions.value.includes(Number(b.id)))
    } else if (activeFilter.value === 'unauthorized') {
        articles = articles.filter(b => !currentPermissions.value.includes(Number(b.id)))
    }

    return articles
})

const treeData = computed(() => {
    return buildTree(filteredArticles.value)
})

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
                if (!current.children.find(c => c.name === title && c.isFile)) {
                    current.children.push({
                        name: title,
                        isFile: true,
                        blogId: blog.id,
                        date: blog.date,
                        series: blog.series
                    })
                }
            } else {
                let dirNode = current.children.find(c => c.name === part && !c.isFile)
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
        node.children.sort((a, b) => {
            if (a.isFile !== b.isFile) return a.isFile ? 1 : -1
            return a.name.localeCompare(b.name)
        })
        node.children.forEach(c => { if (!c.isFile) sortNode(c) })
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

// 初始化默认科目
watch(() => props.allSubjects, (subjects) => {
    if (subjects.length > 0 && !currentSubject.value) {
        currentSubject.value = subjects[0]
    }
}, { immediate: true })
</script>

<style scoped>
.admin-title {
    font-size: 1.6rem;
    font-weight: 600;
    color: #2d4a3a;
    margin-bottom: 20px;
}

.admin-controls {
    display: flex;
    flex-wrap: wrap;
    gap: 24px 40px;
    margin-bottom: 18px;
    padding: 14px 18px;
    background: rgba(255, 255, 255, 0.5);
    border-radius: 16px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    align-items: flex-end;
}

.control-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
    flex: 0 1 auto;
    min-width: 140px;
}

.control-group label {
    font-size: 0.85rem;
    font-weight: 600;
    color: #4c6b5b;
    display: flex;
    align-items: center;
    gap: 4px;
}

.subject-btn-group {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding: 4px 0;
    align-items: center;
}

.subject-btn {
    padding: 6px 18px;
    border-radius: 20px;
    border: 1.5px solid rgba(120, 170, 155, 0.3);
    background: rgba(255, 255, 255, 0.8);
    color: #4c6b5b;
    font-size: 0.85rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s ease;
    white-space: nowrap;
    font-family: inherit;
}

.subject-btn:hover {
    background: rgba(120, 170, 155, 0.15);
    border-color: rgba(120, 170, 155, 0.6);
    transform: translateY(-1px);
}

.subject-btn.is-active {
    background: linear-gradient(135deg, rgba(255, 248, 214, 0.95), rgba(243, 227, 162, 0.85));
    border-color: #d9ba4b;
    color: #705d13;
    box-shadow: 0 2px 10px rgba(217, 186, 75, 0.2);
}

.admin-tree {
    margin-top: 4px;
    min-height: 200px;
    background: rgba(255, 255, 255, 0.7);
    border-radius: 12px;
    border: 1px solid rgba(120, 170, 155, 0.12);
    padding: 8px 0;
}

.tree-placeholder {
    color: #adb5bd;
    text-align: center;
    padding: 40px 20px;
    font-size: 15px;
}

.admin-tip {
    margin-top: 24px;
    padding: 14px 20px;
    background: rgba(255, 248, 214, 0.55);
    border-radius: 16px;
    border: 1px solid rgba(224, 199, 106, 0.15);
    color: #3d5a4a;
    font-size: 0.95rem;
    display: flex;
    align-items: center;
    gap: 10px;
}

.admin-tip i {
    color: #d0a93d;
    font-size: 1.2rem;
}

@media (max-width: 820px) {
    .admin-controls {
        gap: 16px;
        padding: 12px 14px;
    }

    .control-group {
        min-width: 100%;
        flex-basis: 100%;
    }
}

@media (max-width: 600px) {
    .admin-title {
        font-size: 1.3rem;
    }

    .admin-controls {
        flex-direction: column;
        gap: 12px;
        padding: 12px 14px;
    }

    .subject-btn {
        padding: 5px 12px;
        font-size: 0.8rem;
    }

    .admin-tip {
        font-size: 0.85rem;
        padding: 10px 14px;
        flex-wrap: wrap;
    }
}
</style>