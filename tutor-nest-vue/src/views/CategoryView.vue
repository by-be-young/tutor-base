<!--
  CategoryView.vue - 文章分类目录视图
  ----------------------------------------------------------------------------
  功能说明：
    1. 根据用户登录状态展示不同内容（未登录提示 / 空分类提示 / 目录列表）
    2. 从路由 query 参数中获取当前科目（subject），结合用户权限过滤博客数据
    3. 将过滤后的博客数据构建为层级目录树（文件夹 + 文件）
    4. 加载并展示每个博客文件的提交状态
    5. 支持点击文件节点导航到博客详情页
  
  依赖关系：
    - 全局状态：authStore（认证信息）、blogStore（博客数据）
    - 子组件：FolderNode（目录文件夹节点）、FileNode（目录文件节点）
    - 路由参数：query.subject（当前选中的科目）
  
  数据流：
    route.query.subject  →  currentSubject  →  filteredArticles  →  treeData  →  渲染目录树
    authStore.permissionIds ──────────────────┘
    
    blogStore.blogData ──────────────────────────────────────────────────┘
-->
<template>
    <!-- 根容器：文章分类目录的整体布局 -->
    <div class="category-container">
        <!--
          状态一：用户未登录
          展示登录引导提示，包含跳转至首页的链接
        -->
        <div v-if="!authStore.isLoggedIn" class="empty-tip">
            请先 <router-link to="/" class="tip-link">登录</router-link> 后查看。
        </div>

        <!--
          状态二：用户已登录但无有效数据
          展示空状态提示，引导用户从首页选择科目
          触发条件：未选择科目(currentSubject为空) 或 过滤后博客列表为空
        -->
        <div v-else-if="!currentSubject || filteredArticles.length === 0" class="empty-tip">
            请从首页选择科目。<br>
            <router-link to="/" class="tip-link">返回首页</router-link>
        </div>

        <!--
          状态三：正常展示博客目录列表
          遍历根级节点，根据节点类型渲染文件夹或文件组件
          
          节点类型判断：
            - node.isFile === false : 文件夹节点，递归渲染子节点
            - node.isFile === true  : 文件节点，点击可跳转至详情
          
          组件传参说明：
            - node：当前目录节点对象（文件夹含 children，文件含 blogId 等）
            - depth：当前层级深度（根级为0，每嵌套一层 +1）
            - status-map：提交状态映射表 { blogId → 提交状态 }
          
          事件监听：
            - @file-click：文件点击事件，触发导航
        -->
        <div v-else class="blog-list">
            <template v-for="node in treeData" :key="node.name">
                <!-- 渲染文件夹节点（非文件类型） -->
                <FolderNode v-if="!node.isFile" :node="node" :depth="0" :status-map="submissionStatusMap"
                    @file-click="navigateToDetail" />
                <!-- 渲染文件节点（叶子节点） -->
                <FileNode v-else :node="node" :depth="0" :status-map="submissionStatusMap"
                    @click="navigateToDetail(node.blogId)" />
            </template>
        </div>
    </div>
</template>

<script setup>
/**
 * 组件逻辑层
 * 
 * 核心职责：
 * 1. 管理当前科目状态（currentSubject）
 * 2. 计算属性生成过滤后博客列表（filteredArticles）
 * 3. 将博客列表转换为目录树结构（treeData）
 * 4. 加载博客提交状态（submissionStatusMap）
 * 5. 处理文件节点的导航跳转
 */

// ==================== 外部依赖导入 ====================

import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { supabase } from '@/utils/supabase'

// 子组件：目录文件夹节点
import FolderNode from '@/components/articles/FolderNode.vue'
// 子组件：目录文件节点
import FileNode from '@/components/articles/FileNode.vue'

// ==================== 路由与状态管理 ====================

/** Vue Router 路由实例，用于获取当前路由参数 */
const route = useRoute()
/** Vue Router 路由导航实例，用于编程式跳转 */
const router = useRouter()
/** 认证状态管理 store，提供登录状态和用户权限信息 */
const authStore = useAuthStore()
/** 博客数据管理 store，提供博客数据加载功能 */
const blogStore = useArticleStore()

// ==================== 响应式状态 ====================

/**
 * 当前选中的科目
 * 初始值从路由 query 参数 `subject` 获取
 * @type {import('vue').Ref<string>}
 */
const currentSubject = ref(route.query.subject || '')

/**
 * 博客提交状态映射表
 * 键：博客 ID（number）
 * 值：提交状态对象（包含完成状态等）
 * @type {import('vue').Ref<Map<number, object>>}
 */
const submissionStatusMap = ref(new Map())

// ==================== 计算属性 ====================

/**
 * 过滤后的博客列表
 * 
 * 过滤逻辑：
 * 1. 当前科目为空时返回空数组
 * 2. 从 authStore 获取用户的权限 ID 列表，转换为有效数字
 * 3. 筛选条件：
 *    a. 博客所属系列（series）等于当前科目
 *    b. 博客 ID 在用户权限范围内
 * 
 * @type {import('vue').ComputedRef<Array>}
 */
const filteredArticles = computed(() => {
    // 未选择科目时直接返回空
    if (!currentSubject.value) return []

    // 获取用户权限 ID 列表：转为 Number 并过滤非法数值（NaN、Infinity）
    const permissionIds = authStore.getPermissionIds()
        .map(Number)
        .filter(Number.isFinite)

    // 双条件过滤：科目匹配 + 权限校验
    return blogStore.blogData.filter(b =>
        b.series === currentSubject.value &&
        permissionIds.includes(Number(b.id))
    )
})

/**
 * 博客目录树结构
 * 
 * 构建时机：filteredArticles 变化时自动重新计算
 * 构建结果：根级节点数组（文件夹和文件混排，文件夹在前）
 * 
 * @type {import('vue').ComputedRef<Array>}
 */
const treeData = computed(() => {
    if (!filteredArticles.value.length) return []
    return buildTree(filteredArticles.value)
})

// ==================== 工具函数 ====================

/**
 * 构建博客目录树
 * 
 * 算法说明：
 * 1. 遍历所有博客对象，解析每条博客的路径（path）
 * 2. 将路径按 "/" 分割，去掉第一个空段，得到目录层级数组
 * 3. 逐层查找或创建文件夹节点
 * 4. 在最终层级创建文件节点，携带博客元数据（ID、日期、系列等）
 * 5. 递归排序：文件夹优先，同级按名称字母序排列
 * 
 * 示例转换：
 *   输入：{ path: "/基础/语法/变量", title: "let声明", id: 1 }
 *   输出：{ name: "基础" → children: [{ name: "语法" → children: [{ name: "let声明", isFile: true, blogId: 1 }] }] }
 * 
 * @param {Array} articles - 待构建的博客数据数组
 * @returns {Array} 构建完成的根级节点数组
 */
function buildTree(articles) {
    // 虚拟根节点，用于统一处理逻辑
    const root = { children: [] }

    articles.forEach(blog => {
        // 分割路径并去掉第一个空段（路径以 "/" 开头时第一个元素为空字符串）
        const parts = blog.path.split('/')
        const dirParts = parts.slice(1)
        const title = blog.title

        // 当前指针，从根节点开始
        let current = root

        // 逐层处理目录部分
        for (let i = 0; i < dirParts.length; i++) {
            const part = dirParts[i]

            // 判断是否为最后一层（文件层）
            if (i === dirParts.length - 1) {
                // 在文件层：创建文件节点（去重处理）
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
                // 在文件夹层：查找或创建文件夹节点
                let dirNode = current.children.find(child => child.name === part && !child.isFile)
                if (!dirNode) {
                    dirNode = { name: part, isFile: false, children: [] }
                    current.children.push(dirNode)
                }
                // 进入下一层级
                current = dirNode
            }
        }
    })

    /**
     * 递归排序节点
     * 排序规则：文件夹在前（isFile: false），文件在后（isFile: true），同级按名称升序
     * 
     * @param {Object} node - 待排序的节点对象
     */
    function sortNode(node) {
        if (!node.children) return
        node.children.sort((a, b) => {
            // 类型不同时：文件夹优先级更高（排在前面）
            if (a.isFile !== b.isFile) return a.isFile ? 1 : -1
            // 类型相同时：按名称字母序排列
            return a.name.localeCompare(b.name)
        })
        // 递归处理子节点的排序
        node.children.forEach(child => {
            if (!child.isFile) sortNode(child)
        })
    }
    sortNode(root)

    // 返回根节点的 children，即顶层节点数组
    return root.children
}

// ==================== 导航处理 ====================

/**
 * 导航到博客详情页
 * 
 * @param {number|string} blogId - 目标博客的唯一标识符
 */
function navigateToDetail(blogId) {
    router.push(`/blog/${blogId}`)
}

// ==================== 生命周期与监听 ====================

/**
 * 组件挂载后执行：
 * 1. 加载博客数据（从 blogStore 获取全量数据）
 * 2. 根据过滤后的博客 ID 列表，异步加载提交状态
 * 
 * 前置条件：用户已登录
 */
onMounted(async () => {
    if (authStore.isLoggedIn) {
        // 触发博客数据加载（异步操作）
        await blogStore.loadArticleData()

        // 提取当前可见博客的 ID 列表
        const blogIds = filteredArticles.value.map(b => b.id)
        if (blogIds.length > 0) {
            // 批量查询提交状态
            submissionStatusMap.value = await loadSubmissionStatus(blogIds)
        }
    }
})

// 自动登录（initFromStorage 异步恢复会话）完成后同步加载数据
watch(() => authStore.isLoggedIn, async (loggedIn) => {
    if (loggedIn) {
        await blogStore.loadArticleData()
        const blogIds = filteredArticles.value.map(b => b.id)
        if (blogIds.length > 0) {
            submissionStatusMap.value = await loadSubmissionStatus(blogIds)
        }
    }
})

/**
 * 监听路由 query 参数 subject 的变化
 * 当用户从首页切换科目时，更新当前科目并触发数据重新计算
 *
 * @param {string} newSubject - 新的科目值
 */
watch(() => route.query.subject, async (newSubject) => {
    if (newSubject) {
        currentSubject.value = newSubject
        // 重新加载提交状态
        if (authStore.isLoggedIn) {
            await blogStore.loadArticleData()
            const blogIds = filteredArticles.value.map(b => b.id)
            if (blogIds.length > 0) {
                submissionStatusMap.value = await loadSubmissionStatus(blogIds)
            }
        }
    }
})

// ==================== 数据加载函数 ====================

/**
 * 批量加载博客提交状态
 *
 * 加载该学生有答案但未提交的题目数（待提交数量）
 *
 * @param {Array<number|string>} blogIds - 需要查询的博客 ID 列表
 * @returns {Promise<Map<number, number>>} 返回 blogId → 待提交题数 的映射
 */
async function loadSubmissionStatus(blogIds) {
    if (!blogIds.length) return new Map()

    // 1. 从答案表获取有题目的文章
    const { data: keys, error: keyErr } = await supabase
        .from('article_answer_keys')
        .select('blog_id, question_id')
        .in('blog_id', blogIds)

    if (keyErr) {
        console.error('加载答案数据失败:', keyErr)
        return new Map()
    }

    // 按 blog_id 统计题目数量
    const totalQuestionMap = new Map()
    ;(keys || []).forEach(item => {
        const bid = Number(item.blog_id)
        totalQuestionMap.set(bid, (totalQuestionMap.get(bid) || 0) + 1)
    })

    // 2. 从提交表获取该学生已提交的题目数
    if (!authStore.currentUser?.id) return new Map()

    const studentId = Number(authStore.currentUser.id)
    if (!Number.isFinite(studentId)) return new Map()

    const { data: subs, error: subErr } = await supabase
        .from('article_question_submissions')
        .select('blog_id')
        .eq('student_id', studentId)
        .in('blog_id', blogIds)

    if (subErr) {
        console.error('加载提交记录失败:', subErr)
        return new Map()
    }

    // 按 blog_id 统计已提交题数
    const submittedCountMap = new Map()
    ;(subs || []).forEach(item => {
        const bid = Number(item.blog_id)
        submittedCountMap.set(bid, (submittedCountMap.get(bid) || 0) + 1)
    })

    // 3. 结果：待提交 = 总题目数 - 已提交题数（仅当有题目且待提交 > 0）
    const result = new Map()
    for (const [bid, total] of totalQuestionMap) {
        const submitted = submittedCountMap.get(bid) || 0
        const pending = total - submitted
        if (pending > 0) {
            result.set(bid, pending)
        }
    }

    return result
}
</script>

<style scoped>
/* ===================================================================
   组件样式 - CategoryView 文章分类目录
   =================================================================== */

/**
 * 根容器
 * 定宽居中布局，带内边距，底部预留足够空间
 */
.category-container {
    max-width: 1300px;
    min-width: 800px;
    margin: 0 auto;
    padding: 30px 32px 80px;
}

/**
 * 空状态提示文本
 * 居中显示，圆角卡片背景，灰色文字
 */
.empty-tip {
    text-align: center;
    padding: 60px 20px;
    color: var(--gray);
    background: rgba(255, 255, 255, 0.3);
    border-radius: 24px;
}

/**
 * 提示文本中的链接样式
 * 使用深青色（teal-dark）突出显示可点击链接
 */
.tip-link {
    color: var(--teal-dark);
}

/**
 * 博客列表容器
 * 纵向弹性布局，节点间距 6px
 */
.blog-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

/* ===================================================================
   响应式布局 - 大屏幕设备（≥1024px）
   增大水平内边距，提供更宽敞的阅读空间
   =================================================================== */
@media (min-width: 1024px) {
    .category-container {
        width: 100%;
        padding: 50px 60px 120px;
    }
}

/* ===================================================================
   响应式布局 - 中等屏幕设备（641px - 1023px）
   适度内边距，平衡内容展示与屏幕利用率
   =================================================================== */
@media (min-width: 641px) and (max-width: 1023px) {
    .category-container {
        width: 100%;
        padding: 30px 28px 70px;
    }
}

/* ===================================================================
   响应式布局 - 小屏幕设备（≤640px）
   紧凑内边距，移除最小宽度限制，适应窄屏
   =================================================================== */
@media (max-width: 640px) {
    .category-container {
        width: 100%;
        padding: 16px 14px 40px;
        min-width: auto;
        /* 覆盖默认的 min-width，允许更窄的屏幕适配 */
    }
}
</style>