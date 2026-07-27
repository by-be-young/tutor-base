<!-- src/views/AdminView.vue -->
<template>
    <div class="admin-app">
        <!-- 顶部导航栏 -->
        <header class="admin-header">
            <div class="admin-header-left">
                <span class="admin-brand">
                    <i class="fas fa-shield-alt"></i> 管理员
                </span>
                <span class="admin-current-student">{{ currentStudentName }}</span>
            </div>
            <div class="admin-header-right">
                <select v-model="selectedStudentId" @change="handleStudentChange" aria-label="切换学生">
                    <option value="">-- 切换学生 --</option>
                    <option v-for="student in students" :key="student.id" :value="student.id">
                        {{ student.username }}
                    </option>
                </select>
                <button class="admin-btn admin-btn-add" @click="addStudent">
                    <i class="fas fa-user-plus"></i> 新增
                </button>
                <button class="admin-btn admin-btn-save" :disabled="!permissionDirty" @click="savePermissions">
                    <i :class="permissionDirty ? 'fas fa-save' : 'fas fa-check'"></i>
                    {{ permissionDirty ? '保存权限' : '已保存' }}
                </button>
                <button class="admin-btn admin-btn-logout" @click="handleLogout">
                    <i class="fas fa-sign-out-alt"></i> 退出
                </button>
            </div>
        </header>

        <!-- 主体：侧边栏 + 内容 -->
        <div class="admin-body">
            <!-- 侧边栏 -->
            <aside class="admin-sidebar">
                <nav class="admin-sidebar-nav">
                    <button v-for="tab in tabs" :key="tab.id" class="admin-sidebar-btn"
                        :class="{ 'is-active': activeTab === tab.id }" @click="activeTab = tab.id">
                        <i :class="tab.icon"></i> {{ tab.label }}
                    </button>
                </nav>
                <div class="admin-sidebar-footer">
                    <span>学生：{{ currentStudentName }}</span>
                </div>
            </aside>

            <!-- 主内容区 -->
            <main class="admin-content">
                <!-- 权限管理面板 -->
                <section v-show="activeTab === 'permission'" class="admin-panel">
                    <PermissionPanel :students="students" :selected-student-id="selectedStudentId" :blog-data="blogData"
                        :all-subjects="allSubjects" @permission-change="onPermissionChange" />
                </section>

                <!-- 批阅中心面板 -->
                <section v-show="activeTab === 'review'" class="admin-panel">
                    <ReviewPanel :students="students" :selected-student-id="selectedStudentId" :blog-data="blogData"
                        :all-subjects="allSubjects" />
                </section>

                <!-- 答案设置面板 -->
                <section v-show="activeTab === 'answer'" class="admin-panel">
                    <AnswerPanel :blog-data="blogData" :all-subjects="allSubjects" />
                </section>
            </main>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useBlogStore } from '@/stores/blogStore'
import { useAdminStore } from '@/stores/adminStore'
import PermissionPanel from '@/components/admin/PermissionPanel.vue'
import ReviewPanel from '@/components/admin/ReviewPanel.vue'
import AnswerPanel from '@/components/admin/AnswerPanel.vue'

const router = useRouter()
const authStore = useAuthStore()
const blogStore = useBlogStore()
const adminStore = useAdminStore()

// 标签页
const tabs = [
    { id: 'permission', label: '权限管理', icon: 'fas fa-shield-alt' },
    { id: 'review', label: '批阅中心', icon: 'fas fa-pen-nib' },
    { id: 'answer', label: '设置答案', icon: 'fas fa-key' }
]

const activeTab = ref('permission')
const selectedStudentId = ref('')
const permissionDirty = ref(false)

// 计算属性
const students = computed(() => adminStore.students)

const currentStudentName = computed(() => {
    if (!selectedStudentId.value) return '未选择'
    const student = students.value.find(s => String(s.id) === String(selectedStudentId.value))
    return student?.username || '未选择'
})

const blogData = computed(() => blogStore.blogData)

const allSubjects = computed(() => {
    const subjects = new Set()
    blogData.value.forEach(blog => {
        if (blog.series) subjects.add(blog.series)
    })
    return Array.from(subjects).sort()
})

// 方法
function handleStudentChange() {
    adminStore.setCurrentStudent(selectedStudentId.value)
    permissionDirty.value = false
}

function onPermissionChange() {
    permissionDirty.value = true
}

async function savePermissions() {
    const success = await adminStore.savePermissions()
    if (success) {
        permissionDirty.value = false
    }
}

async function addStudent() {
    const username = prompt('请输入新学生的姓名（用户名）:')
    if (!username || username.trim() === '') return

    try {
        await adminStore.addStudent(username.trim())
        selectedStudentId.value = adminStore.currentStudentId
    } catch (error) {
        alert(error.message || '新增失败，请重试')
    }
}

function handleLogout() {
    authStore.logout()
    router.push('/')
}

// 初始化
onMounted(async () => {
    await blogStore.loadBlogData()
    await adminStore.loadStudents()

    if (students.value.length > 0) {
        selectedStudentId.value = students.value[0].id
        adminStore.setCurrentStudent(selectedStudentId.value)
    }
})

// 监听学生切换
watch(selectedStudentId, (newId) => {
    if (newId) {
        adminStore.setCurrentStudent(newId)
    }
})
</script>

<style scoped>
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

.admin-app {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
    max-width: 1440px;
    min-width: 1220px;
    margin: 0 auto;
    padding: 16px 20px 20px;
}

/* 顶部导航栏 */
.admin-header {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding: 12px 24px;
    background: rgba(255, 255, 255, 0.6);
    backdrop-filter: blur(12px);
    border-radius: 20px;
    border: 1px solid rgba(120, 170, 155, 0.15);
    box-shadow: 0 4px 20px rgba(80, 130, 120, 0.06);
    margin-bottom: 20px;
    flex-shrink: 0;
}

.admin-header-left {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;
}

.admin-brand {
    font-weight: 700;
    font-size: 1.2rem;
    color: #2d4a3a;
    display: flex;
    align-items: center;
    gap: 8px;
}

.admin-current-student {
    background: rgba(123, 200, 196, 0.2);
    padding: 4px 16px;
    border-radius: 30px;
    font-weight: 500;
    color: #2d4a3a;
    font-size: 0.95rem;
}

.admin-header-right {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
}

.admin-header-right select {
    padding: 8px 16px;
    border-radius: 30px;
    border: 1px solid rgba(120, 170, 155, 0.25);
    background: #f8fbfa;
    font-size: 0.95rem;
    cursor: pointer;
    outline: none;
}

.admin-header-right select:focus {
    border-color: #7BC8C4;
    box-shadow: 0 0 0 3px rgba(123, 200, 196, 0.2);
}

/* 通用按钮 */
.admin-btn {
    padding: 8px 18px;
    border: none;
    border-radius: 30px;
    font-size: 0.95rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: rgba(255, 255, 255, 0.7);
    color: #4e6658;
    border: 1px solid rgba(120, 170, 155, 0.15);
}

.admin-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(80, 130, 120, 0.12);
}

.admin-btn-add {
    background: rgba(91, 168, 164, 0.15);
    color: var(--teal-dark);
}

.admin-btn-add:hover {
    background: rgba(91, 168, 164, 0.25);
}

.admin-btn-save {
    background: #7BC8C4;
    color: white;
    border-color: #7BC8C4;
}

.admin-btn-save:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}

.admin-btn-save:not(:disabled):hover {
    background: #5BA8A4;
    border-color: #5BA8A4;
}

.admin-btn-logout {
    background: #f0e6e6;
    color: #7a5a5a;
    border-color: #f0e6e6;
}

.admin-btn-logout:hover {
    background: #e6d4d4;
}

/* 主体：侧边栏 + 内容 */
.admin-body {
    display: flex;
    flex: 1;
    gap: 20px;
    min-height: 500px;
}

/* 侧边栏 */
.admin-sidebar {
    flex: 0 0 200px;
    background: rgba(255, 255, 255, 0.5);
    backdrop-filter: blur(12px);
    border-radius: 20px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    box-shadow: 0 4px 20px rgba(80, 130, 120, 0.04);
    padding: 20px 0 16px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
}

.admin-sidebar-nav {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 0 12px;
}

.admin-sidebar-btn {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    border: none;
    border-radius: 14px;
    background: transparent;
    color: #4e6658;
    font-size: 1rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.25s ease;
    width: 100%;
    text-align: left;
    font-family: inherit;
}

.admin-sidebar-btn i {
    width: 22px;
    text-align: center;
    font-size: 1.1rem;
}

.admin-sidebar-btn:hover {
    background: rgba(255, 255, 255, 0.5);
    color: #2d4a3a;
}

.admin-sidebar-btn.is-active {
    background: linear-gradient(135deg, rgba(255, 248, 214, 0.95), rgba(243, 227, 162, 0.85));
    color: #705d13;
    box-shadow: 0 2px 10px rgba(217, 186, 75, 0.15);
}

.admin-sidebar-footer {
    padding: 12px 16px;
    border-top: 1px solid rgba(120, 170, 155, 0.12);
    font-size: 0.85rem;
    color: var(--gray);
    text-align: center;
}

/* 主内容区 */
.admin-content {
    flex: 1;
    background: rgba(255, 255, 255, 0.4);
    backdrop-filter: blur(8px);
    border-radius: 20px;
    border: 1px solid rgba(255, 255, 255, 0.4);
    padding: 24px 28px 32px;
    box-shadow: 0 4px 20px rgba(80, 130, 120, 0.04);
    overflow-x: auto;
}

/* 响应式 */
@media (max-width: 1024px) {
    .admin-app {
        min-width: auto;
    }
}

@media (max-width: 820px) {
    .admin-body {
        flex-direction: column;
    }

    .admin-sidebar {
        flex: 0 0 auto;
        flex-direction: row;
        padding: 12px 16px;
        align-items: center;
    }

    .admin-sidebar-nav {
        flex-direction: row;
        flex-wrap: wrap;
        padding: 0;
        gap: 4px;
    }

    .admin-sidebar-btn {
        padding: 8px 14px;
        font-size: 0.9rem;
        width: auto;
        border-radius: 30px;
    }

    .admin-sidebar-footer {
        display: none;
    }

    .admin-content {
        padding: 18px 16px 24px;
    }
}

@media (max-width: 600px) {
    .admin-app {
        padding: 8px 10px 10px;
    }

    .admin-header {
        flex-direction: column;
        align-items: stretch;
        padding: 12px 16px;
    }

    .admin-header-right {
        flex-wrap: wrap;
        gap: 8px;
    }

    .admin-header-right select {
        flex: 1;
        min-width: 120px;
    }

    .admin-btn {
        font-size: 0.85rem;
        padding: 6px 14px;
    }
}
</style>