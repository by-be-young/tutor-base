<!-- src/views/AdminView.vue -->
<template>
    <div class="page page--wide admin-console">
        <!-- 指挥中心顶栏：紧凑标题 + 管理员印章 + 当前学员 + 切换 + 操作 -->
        <header class="panel admin-topbar">
            <div class="admin-topbar__brand">
                <span class="admin-topbar__title">指挥中心</span>
                <span class="admin-role">管理员</span>
            </div>

            <div class="admin-topbar__meta">
                <span class="chip chip--cyan admin-current">
                    <i aria-hidden="true" class="fas fa-user-graduate"></i>
                    当前学员 · {{ currentStudentName }}
                </span>
                <label class="admin-switch">
                    <span class="sr-only">切换学生</span>
                    <select v-model="selectedStudentId" class="select admin-select" aria-label="切换学生"
                        @change="handleStudentChange">
                        <option value="">-- 切换学生 --</option>
                        <option v-for="student in students" :key="student.id" :value="student.id">
                            {{ student.username }}
                        </option>
                    </select>
                </label>
            </div>

            <div class="admin-topbar__actions">
                <span class="admin-save-state" :class="{ 'is-dirty': permissionDirty }">{{ saveStateText }}</span>
                <button type="button" class="btn btn--soft" @click="addStudent">
                    <i aria-hidden="true" class="fas fa-user-plus"></i> 新增
                </button>
                <button type="button" class="btn btn--primary admin-save-btn"
                    :disabled="!permissionDirty || savingPermissions" @click="savePermissions">
                    <i aria-hidden="true" class="fas"
                        :class="savingPermissions ? 'fa-circle-notch fa-spin' : permissionDirty ? 'fa-cloud-arrow-up' : 'fa-circle-check'"></i>
                    {{ saveButtonLabel }}
                </button>
                <button type="button" class="btn btn--ghost" @click="handleLogout">
                    <i aria-hidden="true" class="fas fa-right-from-bracket"></i> 退出
                </button>
            </div>
        </header>

        <!-- 概览 -->
        <section class="admin-overview">
            <div class="admin-overview__intro">
                <span class="eyebrow">管理概览</span>
                <h1 class="page-title">一屏掌握每个学员的<span class="text-gradient">学习路径</span></h1>
                <p class="page-sub">配置内容授权、管理学员账户、批阅作业、校准标准答案——所有管理动作在同一工作台完成。</p>
                <div class="row row--wrap admin-overview__chips">
                    <span class="chip chip--violet"><i aria-hidden="true" class="fas fa-user-graduate"></i> {{ currentStudentName }}</span>
                    <span class="chip chip--cyan"><i aria-hidden="true" class="fas fa-shield-halved"></i> 已授权 {{ authorizedCount }} 篇</span>
                    <span class="chip"><i aria-hidden="true" class="fas fa-layer-group"></i> 内容库 {{ blogData.length }} 篇</span>
                </div>
            </div>

            <div class="stat-grid admin-overview__stats">
                <div class="stat-card">
                    <div class="stat-value">{{ students.length }}</div>
                    <div class="stat-label">在册学员</div>
                    <div class="stat-sub">可切换身份统一管理</div>
                </div>
                <div class="stat-card stat-card--cyan">
                    <div class="stat-value">{{ authorizedCount }}</div>
                    <div class="stat-label">当前授权文章</div>
                    <div class="stat-sub">含尚未保存的勾选</div>
                </div>
                <div class="stat-card stat-card--amber">
                    <div class="stat-value">{{ blogData.length }}</div>
                    <div class="stat-label">内容库文章</div>
                    <div class="stat-sub">{{ allSubjects.length }} 个学科分组</div>
                </div>
                <div class="stat-card stat-card--coral">
                    <div class="stat-value">{{ allSubjects.length }}</div>
                    <div class="stat-label">学科数量</div>
                    <div class="stat-sub">授权按学科色归档</div>
                </div>
            </div>
        </section>

        <!-- 主体：玻璃胶囊导航 + 工作区 -->
        <div class="admin-body">
            <aside class="panel admin-sidebar">
                <nav class="admin-nav" aria-label="管理后台导航">
                    <button v-for="tab in tabs" :key="tab.id" type="button" class="admin-nav__btn"
                        :class="{ 'is-active': activeTab === tab.id }" :aria-pressed="activeTab === tab.id"
                        @click="activeTab = tab.id">
                        <span class="admin-nav__icon"><i aria-hidden="true" :class="tab.icon"></i></span>
                        <span class="admin-nav__label">{{ tab.label }}</span>
                        <span v-if="tab.count !== null" class="admin-nav__count">{{ tab.count }}</span>
                    </button>
                </nav>

                <div class="admin-sidebar__footer">
                    <span class="eyebrow">CURRENT LEARNER</span>
                    <p class="admin-sidebar__student">{{ currentStudentName }}</p>
                    <span class="chip" :class="permissionDirty ? 'chip--amber' : 'chip--lime'">
                        <span class="status-dot"></span>
                        {{ saveStateText }}
                    </span>
                    <span class="admin-sidebar__stamp" aria-hidden="true">RISO · CAMPUS</span>
                </div>
            </aside>

            <main class="admin-stage">
                <section v-show="activeTab === 'permission'" class="admin-panel">
                    <PermissionPanel :students="students" :selected-student-id="selectedStudentId"
                        :blog-data="blogData" :all-subjects="allSubjects" :dirty="permissionDirty"
                        :saving="savingPermissions" @permission-change="onPermissionChange"
                        @save-permissions="savePermissions" />
                </section>

                <section v-show="activeTab === 'students'" class="admin-panel">
                    <StudentAccountsPanel :students="students" :selected-student-id="selectedStudentId" />
                </section>

                <section v-show="activeTab === 'review'" class="admin-panel">
                    <ReviewPanel :students="students" :selected-student-id="selectedStudentId" :blog-data="blogData"
                        :all-subjects="allSubjects" />
                </section>

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
import { useArticleStore } from '@/stores/blogStore'
import { useAdminStore } from '@/stores/adminStore'
import { showToast } from '@/utils/toast'
import PermissionPanel from '@/components/admin/PermissionPanel.vue'
import ReviewPanel from '@/components/admin/ReviewPanel.vue'
import AnswerPanel from '@/components/admin/AnswerPanel.vue'
import StudentAccountsPanel from '@/components/admin/StudentAccountsPanel.vue'

const router = useRouter()
const authStore = useAuthStore()
const blogStore = useArticleStore()
const adminStore = useAdminStore()

const activeTab = ref('permission')
const selectedStudentId = ref('')
const permissionDirty = ref(false)
const savingPermissions = ref(false)

const students = computed(() => adminStore.students)
const blogData = computed(() => blogStore.blogData)

const currentStudent = computed(() => {
    if (!selectedStudentId.value) return null
    return students.value.find(student => String(student.id) === String(selectedStudentId.value)) || null
})

const currentStudentName = computed(() => currentStudent.value?.username || '未选择')
const currentPermissions = computed(() => currentStudent.value?.permissions || [])

// 顶栏概览、统计卡、侧栏计数共用同一授权数据源，避免数字不一致。
const authorizedCount = computed(() => currentPermissions.value.length)

const allSubjects = computed(() => {
    const subjects = new Set()
    blogData.value.forEach(blog => {
        if (blog.series) subjects.add(blog.series)
    })
    return Array.from(subjects).sort()
})

const tabs = computed(() => [
    { id: 'permission', label: '权限管理', icon: 'fas fa-shield-halved', count: authorizedCount.value },
    { id: 'students', label: '学生账户', icon: 'fas fa-users-gear', count: students.value.length },
    { id: 'review', label: '批阅中心', icon: 'fas fa-pen-nib', count: null },
    { id: 'answer', label: '设置答案', icon: 'fas fa-key', count: null }
])

const saveButtonLabel = computed(() => {
    if (savingPermissions.value) return '保存中…'
    return permissionDirty.value ? '保存权限' : '已保存'
})

const saveStateText = computed(() => {
    if (savingPermissions.value) return '保存中'
    return permissionDirty.value ? '待保存' : '未改动'
})

function handleStudentChange() {
    adminStore.setCurrentStudent(selectedStudentId.value)
    permissionDirty.value = false
}

function onPermissionChange() {
    permissionDirty.value = true
}

async function savePermissions() {
    if (savingPermissions.value || !permissionDirty.value) return
    savingPermissions.value = true
    try {
        const success = await adminStore.savePermissions()
        if (success) {
            permissionDirty.value = false
            showToast('权限已保存', 'success')
        }
    } catch (error) {
        showToast(error.message || '权限保存失败，请稍后重试', 'error')
    } finally {
        savingPermissions.value = false
    }
}

async function addStudent() {
    // e2e 依赖 window.prompt 的 dialog 流程，请勿改为自定义弹窗。
    const username = prompt('请输入新学生的姓名（用户名）:')
    if (!username || username.trim() === '') return

    try {
        await adminStore.addStudent(username.trim())
        selectedStudentId.value = adminStore.currentStudentId
        permissionDirty.value = false
    } catch (error) {
        showToast(error.message || '新增学生失败，请稍后重试', 'error')
    }
}

async function handleLogout() {
    try {
        await authStore.logout()
    } finally {
        await router.push('/')
    }
}

onMounted(async () => {
    await blogStore.loadArticleData()
    try {
        await adminStore.loadStudents()
    } catch (error) {
        showToast(error.message || '加载学生列表失败，请刷新后重试', 'error')
        return
    }

    if (students.value.length > 0) {
        selectedStudentId.value = students.value[0].id
        adminStore.setCurrentStudent(selectedStudentId.value)
    }
})

watch(selectedStudentId, (newId) => {
    if (newId) {
        adminStore.setCurrentStudent(newId)
    }
})
</script>

<style scoped>
.admin-console {
    display: flex;
    flex-direction: column;
    gap: 24px;
    padding-top: 28px;
}

/* ------------------------------------------------------------------ */
/* 顶栏                                                                */
/* ------------------------------------------------------------------ */
.admin-topbar {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;
    padding: 14px 20px;
    border-radius: var(--r-lg);
}

.admin-topbar__brand {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
}

.admin-topbar__title {
    font-family: var(--font-display);
    font-size: 1.02rem;
    font-weight: 800;
    letter-spacing: -0.01em;
    white-space: nowrap;
}

.admin-role {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 26px;
    padding: 0 12px;
    border-radius: var(--r-full);
    border: 1.5px solid var(--ink);
    background: var(--coral);
    color: var(--ink);
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.14em;
    white-space: nowrap;
    flex-shrink: 0;
}

.admin-topbar__meta {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 1 1 320px;
    min-width: 0;
}

.admin-current {
    max-width: 220px;
    overflow: hidden;
    text-overflow: ellipsis;
}

.admin-switch {
    flex: 1 1 170px;
    min-width: 150px;
    max-width: 240px;
}

.admin-select {
    height: 40px;
    border-radius: var(--r-full);
    font-size: 0.84rem;
    padding: 0 36px 0 15px;
    background-color: var(--paper-1);
}

.admin-topbar__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    margin-left: auto;
}

.admin-save-state {
    font-family: var(--font-mono);
    font-size: 0.66rem;
    letter-spacing: 0.14em;
    color: var(--text-4);
    white-space: nowrap;
}

.admin-save-state.is-dirty {
    color: var(--amber);
}

.admin-save-btn {
    min-width: 118px;
}

/* ------------------------------------------------------------------ */
/* 概览                                                                */
/* ------------------------------------------------------------------ */
.admin-overview {
    display: flex;
    flex-direction: column;
    gap: 20px;
    padding: 4px 2px 0;
}

.admin-overview__intro {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.admin-overview__intro .page-title {
    font-size: clamp(1.7rem, 2.8vw, 2.4rem);
}

.admin-overview__chips {
    margin-top: 4px;
}

.admin-overview__stats {
    gap: 14px;
}

/* ------------------------------------------------------------------ */
/* 主体                                                                */
/* ------------------------------------------------------------------ */
.admin-body {
    display: grid;
    grid-template-columns: 246px minmax(0, 1fr);
    gap: 20px;
    align-items: start;
}

.admin-sidebar {
    padding: 14px;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.admin-nav {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.admin-nav__btn {
    display: flex;
    align-items: center;
    gap: 11px;
    width: 100%;
    padding: 9px 11px;
    border-radius: var(--r-sm);
    border: 1px solid transparent;
    color: var(--text-2);
    font-size: 0.88rem;
    font-weight: 600;
    text-align: left;
    transition: background var(--dur), border-color var(--dur), color var(--dur), box-shadow var(--dur);
}

.admin-nav__btn:hover {
    background: var(--glass);
    border-color: var(--line);
    color: var(--text-1);
}

.admin-nav__btn.is-active {
    color: var(--ink);
    background: var(--lime);
    border-color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

.admin-nav__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 10px;
    background: var(--paper-2);
    color: var(--text-2);
    font-size: 0.86rem;
    flex-shrink: 0;
    transition: background var(--dur), color var(--dur), box-shadow var(--dur);
}

.admin-nav__btn.is-active .admin-nav__icon {
    background: var(--paper-1);
    color: var(--ink);
    box-shadow: none;
}

.admin-nav__label {
    flex: 1;
    min-width: 0;
}

.admin-nav__count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 24px;
    height: 22px;
    padding: 0 7px;
    border-radius: var(--r-full);
    background: var(--paper-2);
    color: var(--text-2);
    font-size: 0.68rem;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
}

.admin-nav__btn.is-active .admin-nav__count {
    background: var(--paper-1);
    color: var(--ink);
}

.admin-sidebar__footer {
    margin-top: auto;
    padding-top: 14px;
    border-top: 1px solid var(--line);
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
}

.admin-sidebar__footer .eyebrow {
    font-size: 0.58rem;
    letter-spacing: 0.2em;
}

.admin-sidebar__student {
    font-family: var(--font-display);
    font-size: 0.98rem;
    font-weight: 700;
    color: var(--text-1);
    word-break: break-all;
}

.admin-stage {
    min-width: 0;
}

.admin-panel {
    min-width: 0;
    animation: fade-up 0.32s var(--ease-out);
}

/* ------------------------------------------------------------------ */
/* 响应式                                                              */
/* ------------------------------------------------------------------ */
@media (max-width: 1024px) {
    .admin-body {
        grid-template-columns: minmax(0, 1fr);
    }

    .admin-sidebar {
        flex-direction: row;
        align-items: center;
        gap: 10px;
        padding: 10px;
        overflow-x: auto;
        scrollbar-width: thin;
    }

    .admin-nav {
        flex-direction: row;
        gap: 8px;
        min-width: 0;
    }

    .admin-nav__btn {
        width: auto;
        flex: 0 0 auto;
        white-space: nowrap;
        padding: 8px 14px;
        min-height: 42px;
    }

    .admin-nav__count {
        display: none;
    }

    .admin-sidebar__footer {
        display: none;
    }
}

@media (max-width: 760px) {
    .admin-console {
        padding-top: 16px;
        gap: 18px;
    }

    .admin-topbar {
        padding: 14px;
        gap: 12px;
    }

    .admin-topbar__meta {
        order: 3;
        flex-basis: 100%;
        flex-wrap: wrap;
    }

    .admin-current {
        max-width: 100%;
    }

    .admin-switch {
        flex: 1 1 100%;
        max-width: none;
    }

    .admin-topbar__actions {
        width: 100%;
        margin-left: 0;
        justify-content: flex-end;
    }

    .admin-save-state {
        margin-right: auto;
    }

    .admin-overview__stats {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}

@media (max-width: 420px) {
    .admin-overview__stats {
        grid-template-columns: minmax(0, 1fr);
    }

    .admin-topbar__actions .btn {
        flex: 1 1 auto;
        padding: 0 14px;
    }
}
</style>

<style scoped>
/* ==========================================================================
   Riso Campus 印刷学院风覆盖：奶油纸底、墨线卡片、纯色块、错位硬阴影。
   仅覆盖本页特有形状，通用视觉仍由 theme.css 提供。
   ========================================================================== */
.admin-topbar {
    background: var(--paper-1);
}

.admin-role {
    height: 27px;
    padding: 0 12px;
    border-radius: 0;
    border: 1.5px solid var(--ink);
    background: var(--vermilion);
    color: var(--paper-1);
    letter-spacing: 0.2em;
    box-shadow: 2px 2px 0 var(--ink);
    transform: rotate(-2deg);
}

.admin-select {
    background-color: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.admin-current,
.admin-overview__chips .chip,
.admin-sidebar__footer .chip {
    box-shadow: 2px 2px 0 var(--ink);
}

.admin-save-state {
    padding: 3px 10px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-2);
    color: var(--ink);
    letter-spacing: 0.1em;
}

.admin-save-state.is-dirty {
    background: var(--butter);
    color: var(--ink);
}

.admin-nav__btn {
    border: 1.5px solid transparent;
}

.admin-nav__btn:hover {
    border-color: var(--ink);
    background: var(--paper-2);
    color: var(--ink);
}

.admin-nav__btn.is-active {
    border-color: var(--ink);
    background: var(--ink);
    color: var(--paper-1);
    box-shadow: 3px 3px 0 rgba(28, 26, 20, 0.28);
}

.admin-nav__icon {
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
    color: var(--ink);
}

.admin-nav__btn.is-active .admin-nav__icon {
    background: var(--butter);
    color: var(--ink);
    box-shadow: none;
}

.admin-nav__count {
    border: 1px solid var(--ink);
    background: var(--paper-2);
    color: var(--ink);
}

.admin-nav__btn.is-active .admin-nav__count {
    background: var(--lime);
    color: var(--ink);
}

.admin-sidebar__footer {
    border-top: 1.5px solid var(--ink);
}

.admin-save-btn {
    min-width: 126px;
}

.admin-sidebar__stamp {
    margin-top: 2px;
    padding: 2px 7px;
    border: 1.5px dashed var(--ink);
    color: var(--vermilion);
    font-family: var(--font-mono);
    font-size: 0.54rem;
    font-weight: 700;
    letter-spacing: 0.16em;
    transform: rotate(-2deg);
}

/* 紧凑顶栏：全局 AppHeader 已有品牌，这里只保留工作台标题与身份印章 */
.admin-topbar__brand {
    flex-shrink: 0;
    gap: 10px;
}

/* 统计数字固定高度，避免数字变化造成布局抖动 */
.admin-overview__stats .stat-value {
    display: flex;
    align-items: center;
    min-height: 2.3rem;
}

/* 1024 档：顶栏折成「标题+操作」「学员切换」两行，避免挤压 */
@media (max-width: 1180px) {
    .admin-topbar__meta {
        order: 3;
        flex: 1 1 100%;
    }

    .admin-topbar__actions {
        margin-left: auto;
    }
}
</style>
