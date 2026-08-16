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
                    <div class="student-password-panel">
                        <div class="student-password-heading">
                            <div>
                                <h2>学生账户</h2>
                                <p>管理员可以直接为学生设置或重置登录密码。</p>
                            </div>
                            <p v-if="passwordNotice" class="password-notice" role="status">{{ passwordNotice }}</p>
                        </div>

                        <p v-if="students.length === 0" class="student-password-empty">暂无学生</p>
                        <ul v-else class="student-password-list" aria-label="学生账户列表">
                            <li v-for="student in students" :key="student.id" class="student-password-row">
                                <span class="student-password-name">{{ student.username }}</span>
                                <button type="button" class="student-password-action"
                                    @click="openPasswordDialog(student)">
                                    <i class="fas fa-key"></i> 设置/重置密码
                                </button>
                            </li>
                        </ul>
                    </div>

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

        <div v-if="passwordDialogOpen" class="password-dialog-backdrop" @click.self="closePasswordDialog">
            <form class="password-dialog" role="dialog" aria-modal="true" aria-labelledby="password-dialog-title"
                @submit.prevent="submitPassword">
                <h2 id="password-dialog-title">设置学生密码</h2>
                <p class="password-dialog-student">学生：{{ passwordTarget?.username }}</p>

                <label for="student-new-password">新密码</label>
                <input id="student-new-password" v-model="newPassword" type="password" minlength="12"
                    maxlength="128" autocomplete="new-password" :disabled="passwordSaving" autofocus />

                <label for="student-confirm-password">确认新密码</label>
                <input id="student-confirm-password" v-model="confirmPassword" type="password" minlength="12"
                    maxlength="128" autocomplete="new-password" :disabled="passwordSaving" />

                <p class="password-dialog-hint">密码长度须为 12～128 个字符，两次输入必须一致。</p>
                <p v-if="passwordError" class="password-error" role="alert">{{ passwordError }}</p>

                <div class="password-dialog-actions">
                    <button type="button" class="password-dialog-cancel" :disabled="passwordSaving"
                        @click="closePasswordDialog">取消</button>
                    <button type="submit" class="password-dialog-submit" :disabled="passwordSaving">
                        {{ passwordSaving ? '保存中…' : '确认设置' }}
                    </button>
                </div>
            </form>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { useAdminStore } from '@/stores/adminStore'
import { identityGateway, IdentityGatewayError } from '@/gateways/identityGateway'
import PermissionPanel from '@/components/admin/PermissionPanel.vue'
import ReviewPanel from '@/components/admin/ReviewPanel.vue'
import AnswerPanel from '@/components/admin/AnswerPanel.vue'

const router = useRouter()
const authStore = useAuthStore()
const blogStore = useArticleStore()
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
const passwordDialogOpen = ref(false)
const passwordTarget = ref(null)
const newPassword = ref('')
const confirmPassword = ref('')
const passwordError = ref('')
const passwordNotice = ref('')
const passwordSaving = ref(false)

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

function clearPasswordFields() {
    newPassword.value = ''
    confirmPassword.value = ''
}

function openPasswordDialog(student) {
    clearPasswordFields()
    passwordError.value = ''
    passwordNotice.value = ''
    passwordTarget.value = student
    passwordDialogOpen.value = true
}

function closePasswordDialog() {
    if (passwordSaving.value) return
    clearPasswordFields()
    passwordError.value = ''
    passwordTarget.value = null
    passwordDialogOpen.value = false
}

function passwordFailureMessage(error) {
    if (!(error instanceof IdentityGatewayError)) return '密码设置失败，请稍后重试。'
    if (error.status === 400) return '密码不符合要求，请确认长度为 12～128 个字符。'
    if (error.status === 401) return '登录状态已失效，请重新登录后再试。'
    if (error.status === 403) return '当前账户无权设置学生密码。'
    if (error.status === 404) return '未找到该学生，请刷新页面后重试。'
    if (error.status === 409) return '该学生当前无法设置密码，请刷新页面后重试。'
    if (error.status === 429) return '操作过于频繁，请稍后再试。'
    return '密码设置失败，请稍后重试。'
}

async function submitPassword() {
    passwordError.value = ''
    const password = newPassword.value

    if (password.length < 12 || password.length > 128) {
        passwordError.value = '密码长度须为 12～128 个字符。'
        return
    }
    if (password !== confirmPassword.value) {
        passwordError.value = '两次输入的密码不一致。'
        return
    }
    if (!passwordTarget.value) {
        passwordError.value = '未选择学生，请关闭窗口后重试。'
        return
    }

    passwordSaving.value = true
    try {
        const username = passwordTarget.value.username
        await identityGateway.setLearnerPassword(passwordTarget.value.id, password)
        clearPasswordFields()
        passwordTarget.value = null
        passwordDialogOpen.value = false
        passwordNotice.value = `已成功为“${username}”设置新密码。`
    } catch (error) {
        passwordError.value = passwordFailureMessage(error)
    } finally {
        passwordSaving.value = false
    }
}

async function handleLogout() {
    try {
        await authStore.logout()
    } finally {
        await router.push('/')
    }
}

// 初始化
onMounted(async () => {
    await blogStore.loadArticleData()
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

.student-password-panel {
    margin-bottom: 24px;
    padding: 18px;
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid rgba(120, 170, 155, 0.15);
    border-radius: 16px;
}

.student-password-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 14px;
}

.student-password-heading h2 {
    color: #2d4a3a;
    font-size: 1.15rem;
}

.student-password-heading p,
.student-password-empty {
    color: var(--gray);
    font-size: 0.9rem;
}

.student-password-heading .password-notice {
    color: #28745e;
    font-weight: 600;
}

.student-password-list {
    list-style: none;
    display: grid;
    gap: 8px;
}

.student-password-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 10px 12px;
    background: rgba(245, 250, 248, 0.9);
    border-radius: 10px;
}

.student-password-name {
    color: #2d4a3a;
    font-weight: 600;
}

.student-password-action,
.password-dialog-cancel,
.password-dialog-submit {
    border: 0;
    border-radius: 20px;
    padding: 7px 14px;
    cursor: pointer;
    font: inherit;
}

.student-password-action {
    color: #2d4a3a;
    background: rgba(123, 200, 196, 0.22);
}

.password-dialog-backdrop {
    position: fixed;
    inset: 0;
    z-index: 1000;
    display: grid;
    place-items: center;
    padding: 20px;
    background: rgba(25, 45, 37, 0.38);
}

.password-dialog {
    width: min(440px, 100%);
    padding: 24px;
    display: grid;
    gap: 10px;
    background: #f8fbfa;
    border-radius: 18px;
    box-shadow: 0 20px 60px rgba(30, 60, 50, 0.25);
}

.password-dialog h2 {
    color: #2d4a3a;
}

.password-dialog-student,
.password-dialog-hint {
    color: var(--gray);
}

.password-dialog label {
    margin-top: 6px;
    color: #345143;
    font-weight: 600;
}

.password-dialog input {
    width: 100%;
    padding: 10px 12px;
    border: 1px solid rgba(120, 170, 155, 0.35);
    border-radius: 10px;
    font: inherit;
}

.password-dialog input:focus {
    outline: 2px solid rgba(91, 168, 164, 0.35);
    border-color: #5BA8A4;
}

.password-dialog-hint,
.password-error {
    font-size: 0.88rem;
}

.password-error {
    color: #a33f4b;
}

.password-dialog-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 10px;
}

.password-dialog-cancel {
    color: #4e6658;
    background: rgba(120, 170, 155, 0.12);
}

.password-dialog-submit {
    color: white;
    background: #5BA8A4;
}

.password-dialog button:disabled {
    cursor: not-allowed;
    opacity: 0.6;
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

    .student-password-heading,
    .student-password-row {
        align-items: stretch;
        flex-direction: column;
    }

    .student-password-action {
        width: 100%;
    }
}
</style>
