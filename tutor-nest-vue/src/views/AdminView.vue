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
                <StudentSwitcher class="admin-switcher" @select="handleSelectStudent" />
                <span v-if="currentStudent" class="chip chip--cyan admin-current"
                    :title="`${currentStudentName} · ${currentActivityLabel} · ${currentClaimLabel}`">
                    <i aria-hidden="true" class="fas fa-user-graduate"></i>
                    <span class="admin-current__text">当前学员 · {{ currentStudentName }} · {{ currentActivityLabel }} · {{ currentClaimLabel }}</span>
                </span>
                <span v-else class="chip admin-current">
                    <i aria-hidden="true" class="fas fa-user-graduate"></i>
                    <span class="admin-current__text">当前学员 · 未选择</span>
                </span>
                <button v-if="currentStudent && currentClaimState === 'unclaimed'" type="button"
                    class="btn btn--soft btn--sm admin-claim-btn" :disabled="claimBusy" @click="claimCurrentStudent">
                    <i aria-hidden="true" class="fas" :class="claimBusy ? 'fa-circle-notch fa-spin' : 'fa-hand-pointer'"></i>
                    {{ claimBusy ? '认领中…' : '认领' }}
                </button>
                <button v-else-if="currentStudent && currentClaimState === 'mine'" type="button"
                    class="btn btn--ghost btn--sm admin-claim-btn" :disabled="claimBusy" @click="releaseCurrentStudent">
                    <i aria-hidden="true" class="fas fa-arrow-up-from-bracket"></i> 释放
                </button>
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

        <!-- 概览：只保留当前学员上下文，不放统计数字 -->
        <section class="admin-overview">
            <div class="admin-overview__intro">
                <span class="eyebrow">管理概览</span>
                <h1 class="page-title">一屏掌握每个学员的<span class="text-gradient">学习路径</span></h1>
                <p class="page-sub">配置内容授权、管理学员账户、批阅作业、校准标准答案——所有管理动作在同一工作台完成。</p>
                <div v-if="currentStudent" class="row row--wrap admin-overview__chips">
                    <span class="chip chip--violet"><i aria-hidden="true" class="fas fa-user-graduate"></i> {{ currentStudentName }}</span>
                    <span class="chip"
                        :class="currentClaimState === 'mine' ? 'chip--lime' : currentClaimState === 'unclaimed' ? 'chip--amber' : ''">
                        <i aria-hidden="true" class="fas fa-hand-holding-heart"></i> {{ currentClaimLabel }}
                    </span>
                    <span class="chip"><i aria-hidden="true" class="far fa-clock"></i> 最近动态 · {{ currentActivityLabel }}</span>
                    <span class="chip chip--cyan"><i aria-hidden="true" class="fas fa-book-open"></i> 在学 {{ activeSubjectCount }} 门学科</span>
                </div>
                <div v-else class="row row--wrap admin-overview__chips">
                    <span class="chip"><i aria-hidden="true" class="fas fa-user-graduate"></i> 还没有学员，点右上角「新增」创建第一位</span>
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
                    </button>
                </nav>

                <div class="admin-sidebar__footer">
                    <span class="eyebrow">CURRENT LEARNER</span>
                    <p class="admin-sidebar__student">{{ currentStudentName }}</p>
                    <span v-if="currentStudent" class="admin-sidebar__line">
                        <i aria-hidden="true" class="fas fa-hand-holding-heart"></i> {{ currentClaimLabel }}
                    </span>
                    <span v-if="currentStudent" class="admin-sidebar__line">
                        <i aria-hidden="true" class="far fa-clock"></i> 最近动态 · {{ currentActivityLabel }}
                    </span>
                    <span class="chip" :class="permissionDirty ? 'chip--amber' : 'chip--lime'">
                        <span class="status-dot"></span>
                        {{ saveStateText }}
                    </span>
                    <span class="admin-sidebar__stamp" aria-hidden="true">RISO · CAMPUS</span>
                </div>
            </aside>

            <main class="admin-stage">
                <section v-show="activeTab === 'subjects'" class="admin-panel">
                    <LearnerSubjectPanel :students="students" :selected-student-id="selectedStudentId"
                        :blog-data="blogData" :all-subjects="allSubjects" :dirty="permissionDirty"
                        :saving="savingPermissions" @permission-change="onPermissionChange"
                        @save-permissions="savePermissions" />
                </section>

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
                    <AnswerPanel :blog-data="blogData" :all-subjects="allSubjects" :students="students"
                        :selected-student-id="selectedStudentId" />
                </section>
            </main>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { useAdminStore } from '@/stores/adminStore'
import { claimState, formatRelativeActivity } from '@/utils/adminStudents'
import { activeSubjects, buildSubjectScope } from '@/utils/adminSubjects'
import { showToast } from '@/utils/toast'
import PermissionPanel from '@/components/admin/PermissionPanel.vue'
import LearnerSubjectPanel from '@/components/admin/LearnerSubjectPanel.vue'
import ReviewPanel from '@/components/admin/ReviewPanel.vue'
import AnswerPanel from '@/components/admin/AnswerPanel.vue'
import StudentAccountsPanel from '@/components/admin/StudentAccountsPanel.vue'
import StudentSwitcher from '@/components/admin/StudentSwitcher.vue'

const router = useRouter()
const authStore = useAuthStore()
const blogStore = useArticleStore()
const adminStore = useAdminStore()

const activeTab = ref('subjects')
const savingPermissions = ref(false)
const claimBusy = ref(false)

const students = computed(() => adminStore.students)
const selectedStudentId = computed(() => adminStore.currentStudentId)
const currentStudent = computed(() => adminStore.currentStudent)
const currentPermissions = computed(() => adminStore.currentPermissions)
const permissionDirty = computed(() => adminStore.permissionDirty)
const blogData = computed(() => blogStore.blogData)

const accountId = computed(() => authStore.currentUser?.accountId ?? null)
const currentStudentName = computed(() => currentStudent.value?.username || '未选择')
const currentClaimState = computed(() => claimState(currentStudent.value, accountId.value))
const currentActivityLabel = computed(() => formatRelativeActivity(currentStudent.value?.lastActivityAt))
const currentClaimLabel = computed(() => {
    if (!currentStudent.value) return '未选择'
    if (currentClaimState.value === 'mine') return '我的学员'
    if (currentClaimState.value === 'unclaimed') return '未认领'
    return `由 ${currentStudent.value.claimedByUsername || '其他管理员'} 认领`
})

const allSubjects = computed(() => {
    const subjects = new Set()
    blogData.value.forEach(blog => {
        if (blog.series) subjects.add(blog.series)
    })
    return Array.from(subjects).sort()
})

// 当前学员实际在学的学科数（有任意已授权文章），用于概览上下文提示
const activeSubjectCount = computed(() => activeSubjects(buildSubjectScope({
    subjects: allSubjects.value,
    articles: blogData.value,
    permissions: currentPermissions.value
})).length)

// 侧栏只保留导航，不再用数字角标做统计
const tabs = computed(() => [
    { id: 'subjects', label: '学员科目', icon: 'fas fa-book-open', count: null },
    { id: 'permission', label: '权限管理', icon: 'fas fa-shield-halved', count: null },
    { id: 'students', label: '学生账户', icon: 'fas fa-users-gear', count: null },
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

function handleSelectStudent(studentId) {
    adminStore.selectStudent(studentId)
}

function onPermissionChange() {
    // 脏标记由 adminStore 统一维护，这里只用于保留 PermissionPanel 的事件契约。
}

async function savePermissions() {
    if (savingPermissions.value || !permissionDirty.value) return
    savingPermissions.value = true
    try {
        const success = await adminStore.savePermissions()
        if (success) showToast('权限已保存', 'success')
    } catch (error) {
        showToast(error.message || '权限保存失败，请稍后重试', 'error')
    } finally {
        savingPermissions.value = false
    }
}

async function claimCurrentStudent() {
    if (claimBusy.value || !adminStore.currentStudentId) return
    const username = currentStudentName.value
    claimBusy.value = true
    try {
        await adminStore.claimStudent(adminStore.currentStudentId)
        showToast(`已认领学员“${username}”`, 'success')
    } catch (error) {
        showToast(error.message || '认领学员失败，请稍后重试', 'error')
    } finally {
        claimBusy.value = false
    }
}

async function releaseCurrentStudent() {
    if (claimBusy.value || !adminStore.currentStudentId) return
    const username = currentStudentName.value
    claimBusy.value = true
    try {
        await adminStore.releaseStudent(adminStore.currentStudentId)
        showToast(`已释放学员“${username}”的认领`, 'success')
    } catch (error) {
        showToast(error.message || '释放认领失败，请稍后重试', 'error')
    } finally {
        claimBusy.value = false
    }
}

async function addStudent() {
    // e2e 依赖 window.prompt 的 dialog 流程，请勿改为自定义弹窗。
    const username = prompt('请输入新学生的姓名（用户名）:')
    if (!username || username.trim() === '') return

    try {
        await adminStore.addStudent(username.trim())
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

function handleBeforeUnload(event) {
    if (!adminStore.hasAnyUnsavedChanges) return
    event.preventDefault()
    event.returnValue = ''
}

onBeforeRouteLeave(() => {
    if (!adminStore.hasAnyUnsavedChanges) return true
    return window.confirm('还有学员权限改动尚未保存，确定离开吗？未保存的改动会丢失。')
})

onMounted(async () => {
    window.addEventListener('beforeunload', handleBeforeUnload)
    await blogStore.loadArticleData()
    try {
        await adminStore.loadStudents()
    } catch (error) {
        showToast(error.message || '加载学生列表失败，请刷新后重试', 'error')
        return
    }

    // 刷新/首次进入：优先「我认领且最近有动态」的学员
    adminStore.selectInitialStudent(accountId.value)
})

onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', handleBeforeUnload)
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
    flex: 1 1 380px;
    min-width: 0;
    flex-wrap: wrap;
}

.admin-switcher {
    flex: 1 1 240px;
    min-width: 200px;
    max-width: 340px;
}

.admin-current {
    max-width: 300px;
    overflow: hidden;
}

.admin-current__text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.admin-claim-btn {
    flex-shrink: 0;
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

.admin-sidebar__line {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    color: var(--text-3);
    font-size: 0.72rem;
    line-height: 1.35;
}

.admin-sidebar__line i {
    color: var(--vermilion);
    font-size: 0.7rem;
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

    .admin-switcher {
        flex: 1 1 100%;
        max-width: none;
    }

    .admin-current {
        max-width: 100%;
    }

    .admin-topbar__actions {
        width: 100%;
        margin-left: 0;
        justify-content: flex-end;
    }

    .admin-save-state {
        margin-right: auto;
    }
}

@media (max-width: 420px) {
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
