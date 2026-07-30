<!-- src/components/admin/ReviewPanel.vue -->
<template>
    <div>
        <h2 class="admin-title">批阅中心</h2>

        <div class="admin-controls">
            <div class="control-group">
                <label><i class="fas fa-book"></i> 选择科目：</label>
                <div class="subject-btn-group">
                    <button v-for="subject in allSubjects" :key="subject" class="subject-btn"
                        :class="{ 'is-active': currentSubject === subject }" @click="currentSubject = subject">
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
            <p v-else-if="!currentSubject" class="tree-placeholder">请先选择科目</p>
            <p v-else-if="filteredBlogs.length === 0" class="tree-placeholder">
                该筛选条件下暂无文章
            </p>
            <template v-else>
                <ClickableTreeNode v-for="node in treeData" :key="node.name + (node.isFile ? node.blogId : '')"
                    :node="node" :depth="0" :badge-map="pendingCountMap"
                    :initially-expanded="activeFilter === 'pending' || activeFilter === 'unsubmitted'"
                    @file-click="handleFileClick" />
            </template>
        </div>

        <div class="admin-tip">
            <i class="fas fa-info-circle"></i>
            选择学生后点击文章，进入批阅页。题框会显示学生答案。
        </div>
    </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { supabase } from '@/utils/supabase'
import ClickableTreeNode from './ClickableTreeNode.vue'

const props = defineProps({
    students: { type: Array, default: () => [] },
    selectedStudentId: { type: [String, Number], default: null },
    blogData: { type: Array, default: () => [] },
    allSubjects: { type: Array, default: () => [] }
})

const router = useRouter()

const currentSubject = ref('')
const activeFilter = ref('pending')
const pendingCountMap = ref(new Map())

const filters = [
    { value: 'pending', label: '待批阅' },
    { value: 'reviewed', label: '已批阅' },
    { value: 'unsubmitted', label: '未提交' },
    { value: 'noneed', label: '无需批阅' }
]

const subjectBlogs = computed(() => {
    return props.blogData.filter(b => b.series === currentSubject.value)
})

const filteredBlogs = computed(() => {
    let blogs = subjectBlogs.value

    if (activeFilter.value === 'pending') {
        blogs = blogs.filter(b => {
            const status = studentSubmissionStatus.value.get(Number(b.id))
            return status === 'pending'
        })
    } else if (activeFilter.value === 'reviewed') {
        blogs = blogs.filter(b => {
            const status = studentSubmissionStatus.value.get(Number(b.id))
            return status === 'reviewed'
        })
    } else if (activeFilter.value === 'noneed') {
        blogs = blogs.filter(b => !hasQuestions(Number(b.id)))
    } else if (activeFilter.value === 'unsubmitted') {
        blogs = blogs.filter(b => {
            const hasKey = answerKeysCache.value.has(Number(b.id))
            const hasSubmission = studentSubmissionStatus.value.has(Number(b.id))
            return hasKey && !hasSubmission
        })
    }

    return blogs
})

const treeData = computed(() => {
    return buildTree(filteredBlogs.value)
})

const studentSubmissionStatus = ref(new Map())
const articleHasQuestionsCache = ref(new Map())
const answerKeysCache = ref(new Map())

function hasQuestions(blogId) {
    return articleHasQuestionsCache.value.get(Number(blogId)) || false
}

async function loadAnswerKeysCache() {
    const { data, error } = await supabase
        .from('article_answer_keys')
        .select('blog_id')

    if (error) {
        console.error('加载答案数据失败:', error)
        return
    }

    const cache = new Map()
    const questionsCache = new Map()
    ;(data || []).forEach(item => {
        cache.set(Number(item.blog_id), true)
        questionsCache.set(Number(item.blog_id), true)
    })
    answerKeysCache.value = cache
    articleHasQuestionsCache.value = questionsCache
}

function buildTree(blogs) {
    const root = { children: [] }

    blogs.forEach(blog => {
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

function handleFileClick(blogId) {
    if (props.selectedStudentId) {
        router.push(`/blog/${blogId}?mode=review&studentId=${props.selectedStudentId}`)
    }
}

async function loadSubmissionStatus() {
    if (!props.selectedStudentId) return

    const { data, error } = await supabase
        .from('article_question_submissions')
        .select('blog_id, review_status')
        .eq('student_id', props.selectedStudentId)

    if (error) {
        console.error('加载提交记录失败:', error)
        return
    }

    const statusMap = new Map()
        ; (data || []).forEach(item => {
            const blogId = Number(item.blog_id)
            if (!statusMap.has(blogId) || item.review_status === 'pending') {
                statusMap.set(blogId, item.review_status)
            }
        })

    studentSubmissionStatus.value = statusMap
}

// 监听学生切换
watch(() => props.selectedStudentId, () => {
    loadSubmissionStatus()
})

// 初始化默认科目
watch(() => props.allSubjects, (subjects) => {
    if (subjects.length > 0 && !currentSubject.value) {
        currentSubject.value = subjects[0]
    }
}, { immediate: true })

onMounted(() => {
    loadSubmissionStatus()
    loadAnswerKeysCache()
})
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