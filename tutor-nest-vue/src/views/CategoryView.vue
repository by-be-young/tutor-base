<!-- src/views/CategoryView.vue -->
<template>
    <div class="category-container">
        <div v-if="!authStore.isLoggedIn" class="empty-tip">
            请先 <router-link to="/" class="tip-link">登录</router-link> 后查看。
        </div>

        <div v-else-if="!currentSubject || filteredBlogs.length === 0" class="empty-tip">
            请从首页选择科目。<br>
            <router-link to="/" class="tip-link">返回首页</router-link>
        </div>

        <div v-else class="blog-list">
            <template v-for="node in treeData" :key="node.name">
                <FolderNode v-if="!node.isFile" :node="node" :depth="0" :status-map="submissionStatusMap"
                    @file-click="navigateToDetail" />
                <FileNode v-else :node="node" :depth="0" :status-map="submissionStatusMap"
                    @click="navigateToDetail(node.blogId)" />
            </template>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useBlogStore } from '@/stores/blogStore'
import FolderNode from '@/components/blogs/FolderNode.vue'
import FileNode from '@/components/blogs/FileNode.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const blogStore = useBlogStore()

const currentSubject = ref(route.query.subject || '')
const submissionStatusMap = ref(new Map())

const filteredBlogs = computed(() => {
    if (!currentSubject.value) return []

    const permissionIds = authStore.getPermissionIds()
        .map(Number)
        .filter(Number.isFinite)

    return blogStore.blogData.filter(b =>
        b.series === currentSubject.value &&
        permissionIds.includes(Number(b.id))
    )
})

const treeData = computed(() => {
    if (!filteredBlogs.value.length) return []
    return buildTree(filteredBlogs.value)
})

// 构建目录树
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

    // 排序：文件夹在前，文件在后，均按名称排序
    function sortNode(node) {
        if (!node.children) return
        node.children.sort((a, b) => {
            if (a.isFile !== b.isFile) return a.isFile ? 1 : -1
            return a.name.localeCompare(b.name)
        })
        node.children.forEach(child => {
            if (!child.isFile) sortNode(child)
        })
    }
    sortNode(root)

    return root.children
}

function navigateToDetail(blogId) {
    router.push(`/blog/${blogId}`)
}

// 加载博客数据和提交状态
onMounted(async () => {
    if (authStore.isLoggedIn) {
        await blogStore.loadBlogData()

        const blogIds = filteredBlogs.value.map(b => b.id)
        if (blogIds.length > 0) {
            submissionStatusMap.value = await loadSubmissionStatus(blogIds)
        }
    }
})

// 监听科目参数变化
watch(() => route.query.subject, (newSubject) => {
    if (newSubject) {
        currentSubject.value = newSubject
    }
})

// 加载提交状态（保留原有逻辑）
async function loadSubmissionStatus(blogIds) {
    // 这里需要实现原有的 loadSubmissionStatusForUsers 逻辑
    // 暂时返回空Map
    return new Map()
}
</script>

<style scoped>
.category-container {
    max-width: 1300px;
    min-width: 800px;
    margin: 0 auto;
    padding: 30px 32px 80px;
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

.blog-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

@media (min-width: 1024px) {
    .category-container {
        width: 100%;
        padding: 50px 60px 120px;
    }
}

@media (min-width: 641px) and (max-width: 1023px) {
    .category-container {
        width: 100%;
        padding: 30px 28px 70px;
    }
}

@media (max-width: 640px) {
    .category-container {
        width: 100%;
        padding: 16px 14px 40px;
        min-width: auto;
    }
}
</style>