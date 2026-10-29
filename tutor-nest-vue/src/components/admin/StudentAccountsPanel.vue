<!-- src/components/admin/StudentAccountsPanel.vue -->
<template>
    <div class="student-accounts">
        <div class="panel student-panel">
            <header class="panel-head student-head">
                <div class="student-head__copy">
                    <h2 class="panel-title">
                        <i class="fas fa-users-gear" aria-hidden="true"></i>
                        学生账户
                    </h2>
                    <p class="text-sm text-muted">为学员设置或重置登录密码，也可以免密进入学员账号查看学习现场。</p>
                    <p v-if="passwordNotice" class="student-notice" role="status">
                        <i class="fas fa-circle-check"></i>
                        {{ passwordNotice }}
                    </p>
                    <p v-if="actionError" class="student-error" role="alert">
                        <i class="fas fa-triangle-exclamation"></i>
                        {{ actionError }}
                    </p>
                </div>
            </header>

            <div class="panel-body student-body">
                <div class="student-toolbar">
                    <div class="input-wrap student-search">
                        <i class="fas fa-magnifying-glass" aria-hidden="true"></i>
                        <input v-model="searchQuery" class="input" type="search" autocomplete="off"
                            placeholder="搜索学员用户名" aria-label="搜索学员" />
                    </div>
                    <div class="seg" role="group" aria-label="认领状态筛选">
                        <button v-for="filter in filters" :key="filter.value" type="button" class="seg__btn"
                            :class="{ 'is-active': statusFilter === filter.value }"
                            :aria-pressed="statusFilter === filter.value" @click="statusFilter = filter.value">
                            {{ filter.label }}
                            <span class="seg__count">{{ filter.count }}</span>
                        </button>
                    </div>
                </div>

                <EmptyState v-if="students.length === 0" icon="fas fa-user-plus" title="还没有学生账户"
                    text="点击顶部的「新增」按钮创建第一位学员，然后为他配置内容授权。">
                </EmptyState>

                <EmptyState v-else-if="visibleStudents.length === 0" icon="fas fa-magnifying-glass"
                    :title="statusFilter === 'mine' && !searchQuery.trim() ? '还没有由你认领的学员' : '没有匹配的学员'"
                    :text="statusFilter === 'mine' && !searchQuery.trim()
                        ? '在「未认领」或「全部」里找到学员后点「认领」，他就会默认出现在这里。'
                        : '换一个用户名关键词，或切换认领状态筛选试试。'">
                    <button type="button" class="btn btn--soft btn--sm" @click="resetFilters">查看全部学员</button>
                </EmptyState>

                <ul v-else class="student-list" role="list" aria-label="学生账户列表">
                    <li v-for="student in visibleStudents" :key="student.id" class="student-row" role="listitem"
                        :class="{ 'is-current': String(student.id) === String(selectedStudentId) }">
                        <span class="avatar student-avatar" aria-hidden="true">{{ initialOf(student.username) }}</span>

                        <span class="student-id">
                            <span class="student-name-row">
                                <span class="student-name">{{ student.username }}</span>
                                <span v-if="isTestStudent(student)" class="chip student-test-chip"
                                    :class="testBadgeClass(student)" :title="testBadgeTitle(student)"
                                    :aria-label="testBadgeTitle(student)">
                                    {{ testBadgeLabel(student) }}
                                </span>
                            </span>
                            <span class="student-sub">学员 ID · {{ student.id }}</span>
                            <span class="student-activity">
                                <i class="far fa-clock" aria-hidden="true"></i>
                                最近动态 · {{ formatRelativeActivity(student.lastActivityAt) }}
                            </span>
                            <span class="student-claim" :class="`is-${claimState(student, accountId)}`">
                                <i class="fas fa-hand-holding-heart" aria-hidden="true"></i>
                                {{ claimLabel(student) }}
                            </span>
                        </span>

                        <span class="student-grants">
                            <i class="fas fa-shield-halved"></i>
                            {{ student.permissions.length }} 篇授权
                        </span>

                        <span class="student-status" :class="student.permissions.length ? 'is-active' : 'is-idle'">
                            <span class="status-dot"></span>
                            {{ student.permissions.length ? '已开通' : '待授权' }}
                        </span>

                        <span class="student-actions">
                            <button type="button" class="btn btn--sm"
                                :aria-label="`查看 ${student.username} 的学习详情`"
                                @click="viewDetail(student)">
                                <i class="fas fa-chart-line" aria-hidden="true"></i> 查看详情
                            </button>
                            <button type="button" class="btn btn--soft btn--sm" @click="openPasswordDialog(student)">
                                <i class="fas fa-key" aria-hidden="true"></i> 设置/重置密码
                            </button>
                            <button type="button" class="btn btn--cyan btn--sm"
                                :disabled="enteringStudentId === student.id" @click="enterStudentAccount(student)">
                                <i class="fas fa-right-to-bracket" aria-hidden="true"></i>
                                {{ enteringStudentId === student.id ? '进入中…' : '进入账号' }}
                            </button>
                            <button v-if="claimState(student, accountId) === 'unclaimed'" type="button"
                                class="btn btn--sm" :disabled="claimingStudentId !== null"
                                @click="claimStudentAccount(student)">
                                <i class="fas fa-hand-pointer" aria-hidden="true"></i>
                                {{ claimingStudentId === student.id ? '认领中…' : '认领' }}
                            </button>
                            <button v-else-if="claimState(student, accountId) === 'mine'" type="button"
                                class="btn btn--sm" :disabled="claimingStudentId !== null"
                                @click="releaseStudentAccount(student)">
                                <i class="fas fa-arrow-up-from-bracket" aria-hidden="true"></i>
                                {{ claimingStudentId === student.id ? '释放中…' : '释放' }}
                            </button>
                            <button type="button" class="btn btn--danger btn--sm" :disabled="deleteSaving"
                                @click="openDeleteDialog(student)">
                                <i class="fas fa-trash-can" aria-hidden="true"></i> 删除学员
                            </button>
                        </span>
                    </li>
                </ul>
            </div>
        </div>

        <Teleport to="body">
            <div v-if="passwordDialogOpen" class="backdrop" @click.self="closePasswordDialog">
                <form class="modal password-modal" role="dialog" aria-modal="true"
                    aria-labelledby="password-dialog-title" @submit.prevent="submitPassword"
                    @keydown.esc="closePasswordDialog">
                    <header class="modal__head">
                        <h2 id="password-dialog-title" class="modal__title">
                            <i class="fas fa-key text-gradient"></i>
                            设置学生密码
                        </h2>
                        <button type="button" class="modal__close" aria-label="关闭密码设置"
                            :disabled="passwordSaving" @click="closePasswordDialog">
                            <i class="fas fa-xmark"></i>
                        </button>
                    </header>

                    <div class="modal__body password-modal__body">
                        <div class="password-student">
                            <span class="avatar" aria-hidden="true">{{ initialOf(passwordTarget?.username) }}</span>
                            <span class="password-student__text">
                                <strong>{{ passwordTarget?.username }}</strong>
                                <small class="text-xs text-muted">保存后立即生效，旧密码不可再用。</small>
                            </span>
                        </div>

                        <div class="field">
                            <label class="field-label" for="student-new-password">新密码</label>
                            <input id="student-new-password" v-model="newPassword" class="input" type="password"
                                minlength="6" maxlength="128" autocomplete="new-password"
                                placeholder="6～128 个字符" :disabled="passwordSaving" autofocus />
                        </div>

                        <div class="field">
                            <label class="field-label" for="student-confirm-password">确认新密码</label>
                            <input id="student-confirm-password" v-model="confirmPassword" class="input" type="password"
                                minlength="6" maxlength="128" autocomplete="new-password" placeholder="再次输入新密码"
                                :disabled="passwordSaving" />
                        </div>

                        <p class="field-hint">密码长度须为 6～128 个字符，两次输入必须一致。</p>
                        <p v-if="passwordError" class="password-error" role="alert">
                            <i class="fas fa-circle-exclamation"></i>
                            {{ passwordError }}
                        </p>
                    </div>

                    <footer class="modal__foot">
                        <button type="button" class="btn btn--ghost" :disabled="passwordSaving"
                            @click="closePasswordDialog">取消</button>
                        <button type="submit" class="btn btn--primary" :disabled="passwordSaving">
                            <i class="fas" :class="passwordSaving ? 'fa-circle-notch fa-spin' : 'fa-key'"
                                aria-hidden="true"></i>
                            {{ passwordSaving ? '保存中…' : '确认设置' }}
                        </button>
                    </footer>
                </form>
            </div>
        </Teleport>

        <Teleport to="body">
            <div v-if="deleteDialogOpen" class="backdrop" @click.self="closeDeleteDialog">
                <div class="modal delete-modal" role="dialog" aria-modal="true" aria-labelledby="delete-learner-title"
                    @keydown.esc="closeDeleteDialog">
                    <header class="modal__head">
                        <h2 id="delete-learner-title" class="modal__title">
                            <i class="fas fa-triangle-exclamation delete-modal__icon"></i>
                            删除学员
                        </h2>
                        <button type="button" class="modal__close" aria-label="关闭删除确认"
                            :disabled="deleteSaving" @click="closeDeleteDialog">
                            <i class="fas fa-xmark"></i>
                        </button>
                    </header>

                    <div class="modal__body delete-modal__body">
                        <p class="delete-modal__lead">
                            即将删除学员 <strong class="delete-modal__name">{{ deleteTarget?.username }}</strong>
                            <span class="mono text-xs text-muted">（学员 ID · {{ deleteTarget?.id }}）</span>
                        </p>
                        <p class="delete-modal__warning">
                            <i class="fas fa-circle-exclamation" aria-hidden="true"></i>
                            删除后该学员的登录账户与学习数据将一并移除，<strong>操作不可恢复</strong>。
                        </p>
                        <p v-if="deleteError" class="student-error delete-modal__error" role="alert">
                            <i class="fas fa-triangle-exclamation"></i>
                            {{ deleteError }}
                        </p>
                    </div>

                    <footer class="modal__foot">
                        <button type="button" class="btn btn--ghost" :disabled="deleteSaving"
                            @click="closeDeleteDialog">取消</button>
                        <button type="button" class="btn btn--danger" :disabled="deleteSaving"
                            @click="confirmDeleteStudent">
                            <i class="fas" :class="deleteSaving ? 'fa-circle-notch fa-spin' : 'fa-trash-can'"
                                aria-hidden="true"></i>
                            {{ deleteSaving ? '删除中…' : '确认删除' }}
                        </button>
                    </footer>
                </div>
            </div>
        </Teleport>
    </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useAdminStore } from '@/stores/adminStore'
import { claimState, formatRelativeActivity, isSpecialTestStudent, isTestStudent, sortStudentsByRecentActivity, studentMatchesQuery } from '@/utils/adminStudents'
import { identityGateway, IdentityGatewayError } from '@/gateways/identityGateway'
import { showToast } from '@/utils/toast'
import EmptyState from '@/components/ui/EmptyState.vue'

const props = defineProps({
    students: { type: Array, default: () => [] },
    selectedStudentId: { type: [String, Number], default: null }
})

const emit = defineEmits(['view-detail'])

const router = useRouter()
const authStore = useAuthStore()
const adminStore = useAdminStore()

const accountId = computed(() => authStore.currentUser?.accountId ?? null)

const searchQuery = ref('')
// 默认只看「我的学员」：日常管理对象通常是自己认领的学员
const statusFilter = ref('mine')
const passwordDialogOpen = ref(false)
const passwordTarget = ref(null)
const newPassword = ref('')
const confirmPassword = ref('')
const passwordError = ref('')
const passwordNotice = ref('')
const passwordSaving = ref(false)

const enteringStudentId = ref(null)
const actionError = ref('')
const claimingStudentId = ref(null)

const deleteDialogOpen = ref(false)
const deleteTarget = ref(null)
const deleteError = ref('')
const deleteSaving = ref(false)

const sortedStudents = computed(() => sortStudentsByRecentActivity(props.students))
const mineCount = computed(() => props.students.filter(student => claimState(student, accountId.value) === 'mine').length)
const unclaimedCount = computed(() => props.students.filter(student => claimState(student, accountId.value) === 'unclaimed').length)
const testCount = computed(() => props.students.filter(student => isTestStudent(student)).length)

// 筛选标签右侧保留数量（全部/我的学员/未认领/测试学员）
const filters = computed(() => ([
    { value: 'all', label: '全部', count: props.students.length },
    { value: 'mine', label: '我的学员', count: mineCount.value },
    { value: 'unclaimed', label: '未认领', count: unclaimedCount.value },
    { value: 'test', label: '测试学员', count: testCount.value }
]))

const visibleStudents = computed(() => sortedStudents.value.filter(student => {
    if (!studentMatchesQuery(student, searchQuery.value)) return false
    if (statusFilter.value === 'test') return isTestStudent(student)
    if (statusFilter.value === 'mine') return claimState(student, accountId.value) === 'mine'
    if (statusFilter.value === 'unclaimed') return claimState(student, accountId.value) === 'unclaimed'
    return true
}))

function initialOf(username) {
    return Array.from(String(username || '?'))[0]?.toUpperCase() || '?'
}

function claimLabel(student) {
    const state = claimState(student, accountId.value)
    if (state === 'mine') return '我的'
    if (state === 'unclaimed') return '未认领'
    return `由 ${student.claimedByUsername || '其他管理员'} 认领`
}

function viewDetail(student) {
    emit('view-detail', student)
}

function testBadgeLabel(student) {
    return isSpecialTestStudent(student) ? '特殊测试' : '测试'
}

function testBadgeTitle(student) {
    return isSpecialTestStudent(student)
        ? '特殊测试学员：拥有全部课件权限，积分固定为 999999999 且不会减少'
        : '测试学员：用于功能验证，不计入活跃度统计与学员动态'
}

function testBadgeClass(student) {
    return isSpecialTestStudent(student) ? 'chip--coral' : 'chip--amber'
}

function resetFilters() {
    searchQuery.value = ''
    statusFilter.value = 'all'
}

function clearPasswordFields() {
    newPassword.value = ''
    confirmPassword.value = ''
}

function openPasswordDialog(student) {
    clearPasswordFields()
    passwordError.value = ''
    passwordNotice.value = ''
    actionError.value = ''
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
    if (['csrf_invalid', 'invalid_csrf'].includes(error.code)) {
        return '页面安全令牌已失效，请刷新页面后重试。'
    }
    if (error.status === 400) return '密码不符合要求，请确认长度为 6～128 个字符。'
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

    if (password.length < 6 || password.length > 128) {
        passwordError.value = '密码长度须为 6～128 个字符。'
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

function impersonationFailureMessage(error) {
    if (!(error instanceof IdentityGatewayError)) return '进入学生账号失败，请稍后重试。'
    if (['csrf_invalid', 'invalid_csrf'].includes(error.code)) {
        return '页面安全令牌已失效，请刷新页面后重试。'
    }
    if (error.status === 401) return '登录状态已失效，请重新登录后再试。'
    if (error.status === 403) return '当前账户无权进入学生账号。'
    if (error.status === 404) return '未找到该学生，请刷新页面后重试。'
    if (error.status === 409) return '该学生当前不可进入，可能未激活或已禁用。'
    if (error.status === 429) return '操作过于频繁，请稍后再试。'
    return '进入学生账号失败，请稍后重试。'
}

async function enterStudentAccount(student) {
    actionError.value = ''
    if (!window.confirm(`确认要进入“${student.username}”的账号吗？当前浏览器将切换为该学生身份。`)) {
        return
    }
    enteringStudentId.value = student.id
    try {
        await authStore.impersonateLearner(student.id)
        showToast(`已进入“${student.username}”的账号`, 'success')
        router.push('/')
    } catch (error) {
        actionError.value = impersonationFailureMessage(error)
    } finally {
        enteringStudentId.value = null
    }
}

async function claimStudentAccount(student) {
    if (claimingStudentId.value !== null) return
    actionError.value = ''
    claimingStudentId.value = student.id
    try {
        await adminStore.claimStudent(student.id)
        showToast(`已认领学员“${student.username}”`, 'success')
    } catch (error) {
        actionError.value = error.message || '认领学员失败，请稍后重试'
        showToast(actionError.value, 'error')
    } finally {
        claimingStudentId.value = null
    }
}

async function releaseStudentAccount(student) {
    if (claimingStudentId.value !== null) return
    actionError.value = ''
    claimingStudentId.value = student.id
    try {
        await adminStore.releaseStudent(student.id)
        showToast(`已释放学员“${student.username}”的认领`, 'success')
    } catch (error) {
        actionError.value = error.message || '释放认领失败，请稍后重试'
        showToast(actionError.value, 'error')
    } finally {
        claimingStudentId.value = null
    }
}

function openDeleteDialog(student) {
    deleteTarget.value = student
    deleteError.value = ''
    deleteDialogOpen.value = true
}

function closeDeleteDialog() {
    if (deleteSaving.value) return
    deleteDialogOpen.value = false
    deleteTarget.value = null
    deleteError.value = ''
}

async function confirmDeleteStudent() {
    if (!deleteTarget.value || deleteSaving.value) return
    const target = deleteTarget.value
    deleteSaving.value = true
    deleteError.value = ''
    try {
        await adminStore.deleteStudent(target.id, accountId.value)
        deleteDialogOpen.value = false
        deleteTarget.value = null
        showToast(`已删除学员“${target.username}”`, 'success')
    } catch (error) {
        deleteError.value = error.message || '删除学员失败，请稍后重试'
    } finally {
        deleteSaving.value = false
    }
}
</script>

<style scoped>
.student-accounts {
    min-width: 0;
}

.student-panel {
    overflow: hidden;
}

.student-head {
    align-items: flex-start;
    gap: 16px;
}

.student-head__copy {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
}

.student-notice {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
    padding: 8px 14px;
    border-radius: var(--r-full);
    border: 1px solid rgba(163, 230, 53, 0.32);
    background: rgba(163, 230, 53, 0.1);
    color: #d9f99d;
    font-size: 0.82rem;
    font-weight: 600;
    width: fit-content;
}

.student-error {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
    padding: 8px 14px;
    border-radius: var(--r-full);
    border: 1px solid rgba(251, 113, 133, 0.34);
    background: rgba(251, 113, 133, 0.1);
    color: #fecdd3;
    font-size: 0.82rem;
    font-weight: 600;
    width: fit-content;
}

.student-body {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.student-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
}

.student-search {
    flex: 1 1 240px;
    min-width: 200px;
    max-width: 340px;
}

.student-search .input {
    height: 42px;
}

.student-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.student-row {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
    padding: 12px 14px;
    border-radius: var(--r-sm);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.03);
    transition: border-color var(--dur), background var(--dur), transform var(--dur) var(--ease-out);
}

.student-row:nth-child(even) {
    background: rgba(255, 255, 255, 0.022);
}

.student-row:hover {
    border-color: var(--line-strong);
    background: rgba(255, 255, 255, 0.055);
    transform: translateY(-1px);
}

.student-row.is-current {
    border-color: rgba(34, 211, 238, 0.36);
    box-shadow: inset 3px 0 0 var(--cyan);
}

.student-avatar {
    background: var(--grad-ocean);
}

.student-id {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1 1 200px;
    min-width: 0;
}

.student-name-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    min-width: 0;
}

.student-name {
    font-size: 0.9rem;
    font-weight: 700;
    color: var(--text-1);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

/* 测试/特殊测试标记：沿用全局 chip 配色，只在学员行内收小尺寸 */
.student-test-chip {
    height: 20px;
    padding: 0 7px;
    font-size: 0.6rem;
    flex-shrink: 0;
}

.student-sub {
    font-family: var(--font-mono);
    font-size: 0.64rem;
    color: var(--text-4);
    letter-spacing: 0.04em;
}

.student-activity {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.7rem;
    color: var(--text-3);
}

.student-activity i {
    color: var(--text-4);
    font-size: 0.66rem;
}

.student-claim {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    width: fit-content;
    margin-top: 2px;
    padding: 2px 9px;
    border: 1px solid var(--line-strong);
    border-radius: var(--r-full);
    background: rgba(255, 255, 255, 0.03);
    color: var(--text-3);
    font-size: 0.66rem;
    font-weight: 700;
    white-space: nowrap;
}

.student-claim i {
    font-size: 0.62rem;
    color: var(--text-4);
}

.student-grants {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.76rem;
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
}

.student-grants i {
    color: var(--violet-2);
    font-size: 0.72rem;
}

.student-status {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    height: 26px;
    padding: 0 11px;
    border-radius: var(--r-full);
    font-size: 0.7rem;
    font-weight: 700;
    white-space: nowrap;
    border: 1px solid var(--line);
}

.student-status.is-active {
    color: #d9f99d;
    border-color: rgba(163, 230, 53, 0.3);
    background: rgba(163, 230, 53, 0.1);
}

.student-status.is-active .status-dot {
    background: var(--lime);
    box-shadow: 0 0 0 3px rgba(163, 230, 53, 0.16);
}

.student-status.is-idle {
    color: var(--text-3);
    background: rgba(255, 255, 255, 0.03);
}

.student-status.is-idle .status-dot {
    background: var(--text-4);
    box-shadow: 0 0 0 3px rgba(126, 136, 168, 0.14);
}

.student-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    margin-left: auto;
}

/* 密码弹层 */
.password-modal {
    width: min(460px, 100%);
}

.password-modal__body {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.password-student {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    border-radius: var(--r-sm);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.03);
}

.password-student__text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
}

.password-student__text strong {
    font-family: var(--font-display);
    color: var(--text-1);
    word-break: break-all;
}

.password-error {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 9px 13px;
    border-radius: var(--r-sm);
    border: 1px solid rgba(251, 113, 133, 0.34);
    background: rgba(251, 113, 133, 0.1);
    color: #fecdd3;
    font-size: 0.8rem;
    font-weight: 600;
}

/* 删除学员确认弹层 */
.delete-modal {
    width: min(480px, 100%);
}

.delete-modal__icon {
    color: var(--vermilion);
}

.delete-modal__body {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.delete-modal__lead {
    font-size: 0.92rem;
    color: var(--text-2);
    word-break: break-all;
}

.delete-modal__name {
    font-family: var(--font-display);
    color: var(--text-1);
}

.delete-modal__warning {
    display: flex;
    align-items: flex-start;
    gap: 9px;
    padding: 12px 14px;
    border-radius: var(--r-sm);
    border: 1px solid rgba(251, 113, 133, 0.34);
    background: rgba(251, 113, 133, 0.1);
    color: #fecdd3;
    font-size: 0.82rem;
    font-weight: 600;
}

.delete-modal__warning i {
    margin-top: 3px;
}

.delete-modal__error {
    margin-top: 0;
}

@media (max-width: 760px) {
    .student-body {
        padding: 18px;
    }

    .student-row {
        align-items: flex-start;
    }

    .student-actions {
        width: 100%;
        margin-left: 0;
    }

    .student-actions .btn {
        flex: 1 1 auto;
    }

    .student-toolbar,
    .student-search {
        width: 100%;
        max-width: none;
    }

    .student-toolbar .seg {
        width: 100%;
        justify-content: space-between;
    }
}
</style>

<style scoped>
/* ==========================================================================
   Riso Campus：纸白名单卡、墨线、印章式状态与反馈。
   ========================================================================== */
.panel-title i {
    color: var(--vermilion);
}

.student-head .chip {
    box-shadow: 2px 2px 0 var(--ink);
}

.student-notice {
    border: 1.5px solid var(--ink);
    border-radius: 6px;
    background: var(--lime);
    color: var(--ink);
    box-shadow: 3px 3px 0 var(--ink);
    transform: rotate(-1deg);
}

.student-error {
    border: 1.5px solid var(--ink);
    border-radius: 6px;
    background: var(--coral);
    color: var(--ink);
    box-shadow: 3px 3px 0 var(--ink);
}

.student-row {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.student-row:nth-child(even) {
    background: var(--paper-0);
}

.student-row:hover {
    border-color: var(--ink);
    background: var(--paper-2);
    transform: translate(-1px, -1px);
    box-shadow: 3px 3px 0 var(--ink);
}

.student-row.is-current {
    border-color: var(--ink);
    box-shadow: inset 5px 0 0 var(--vermilion), 2px 2px 0 var(--ink);
}

.student-avatar {
    border: 1.5px solid var(--ink);
    background: var(--butter);
    color: var(--ink);
}

.student-name {
    color: var(--ink);
}

.student-grants i {
    color: var(--cobalt);
}

.student-status {
    border: 1.5px solid var(--ink);
    box-shadow: 1px 1px 0 var(--ink);
}

.student-status.is-active {
    color: var(--ink);
    background: var(--lime);
    border-color: var(--ink);
}

.student-status.is-idle {
    color: var(--text-3);
    background: var(--paper-2);
    border-color: var(--ink);
}

.student-status.is-active .status-dot {
    background: var(--ink);
    box-shadow: none;
}

.student-status.is-idle .status-dot {
    background: var(--text-3);
    box-shadow: none;
}

.student-search .input {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
}

.student-search .input:focus {
    box-shadow: 2px 2px 0 var(--ink);
}

.student-toolbar .seg {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.student-toolbar .seg__btn.is-active {
    background: var(--ink);
    color: var(--paper-1);
}

.student-claim {
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
    color: var(--ink);
}

.student-claim.is-mine {
    background: var(--lime);
}

.student-claim.is-unclaimed {
    background: var(--butter);
}

.student-claim i {
    color: var(--ink);
}

.student-actions .btn {
    border: 1.5px solid var(--ink);
}

.password-student {
    border: 1.5px solid var(--ink);
    background: var(--paper-0);
}

.password-student__text strong {
    color: var(--ink);
}

.password-error {
    border: 1.5px solid var(--ink);
    background: var(--coral);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

.password-modal .modal__body {
    background: var(--paper-1);
}

.delete-modal__warning {
    border: 1.5px solid var(--ink);
    background: var(--coral);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

.delete-modal .modal__body {
    background: var(--paper-1);
}
</style>
