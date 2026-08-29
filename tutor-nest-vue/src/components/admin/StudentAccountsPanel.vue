<!-- src/components/admin/StudentAccountsPanel.vue -->
<template>
    <div>
        <h2 class="admin-title">学生账户</h2>

        <div class="student-accounts-card">
            <div class="student-accounts-heading">
                <p>管理员可以直接为学生设置或重置登录密码，也可以免密进入学生账号。</p>
                <p v-if="passwordNotice" class="student-accounts-notice" role="status">{{ passwordNotice }}</p>
                <p v-if="actionError" class="student-accounts-error" role="alert">{{ actionError }}</p>
            </div>

            <p v-if="students.length === 0" class="student-accounts-empty">暂无学生</p>
            <ul v-else class="student-accounts-list" aria-label="学生账户列表">
                <li v-for="student in students" :key="student.id" class="student-accounts-row">
                    <span class="student-accounts-name">{{ student.username }}</span>
                    <div class="student-accounts-actions">
                        <button type="button" class="student-accounts-action"
                            @click="openPasswordDialog(student)">
                            <i class="fas fa-key"></i> 设置/重置密码
                        </button>
                        <button type="button" class="student-accounts-action is-enter"
                            :disabled="enteringStudentId === student.id" @click="enterStudentAccount(student)">
                            <i class="fas fa-sign-in-alt"></i>
                            {{ enteringStudentId === student.id ? '进入中…' : '进入账号' }}
                        </button>
                    </div>
                </li>
            </ul>
        </div>

        <div v-if="passwordDialogOpen" class="password-dialog-backdrop" @click.self="closePasswordDialog">
            <form class="password-dialog" role="dialog" aria-modal="true" aria-labelledby="password-dialog-title"
                @submit.prevent="submitPassword">
                <h2 id="password-dialog-title">设置学生密码</h2>
                <p class="password-dialog-student">学生：{{ passwordTarget?.username }}</p>

                <label for="student-new-password">新密码</label>
                <input id="student-new-password" v-model="newPassword" type="password" minlength="6"
                    maxlength="128" autocomplete="new-password" :disabled="passwordSaving" autofocus />

                <label for="student-confirm-password">确认新密码</label>
                <input id="student-confirm-password" v-model="confirmPassword" type="password" minlength="6"
                    maxlength="128" autocomplete="new-password" :disabled="passwordSaving" />

                <p class="password-dialog-hint">密码长度须为 6～128 个字符，两次输入必须一致。</p>
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
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { identityGateway, IdentityGatewayError } from '@/gateways/identityGateway'
import { showToast } from '@/utils/toast'

defineProps({
    students: { type: Array, default: () => [] }
})

const router = useRouter()
const authStore = useAuthStore()

const passwordDialogOpen = ref(false)
const passwordTarget = ref(null)
const newPassword = ref('')
const confirmPassword = ref('')
const passwordError = ref('')
const passwordNotice = ref('')
const passwordSaving = ref(false)

const enteringStudentId = ref(null)
const actionError = ref('')

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
</script>

<style scoped>
.admin-title {
    font-size: 1.6rem;
    font-weight: 600;
    color: #2d4a3a;
    margin-bottom: 20px;
}

.student-accounts-card {
    padding: 18px;
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid rgba(120, 170, 155, 0.15);
    border-radius: 16px;
}

.student-accounts-heading {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-bottom: 14px;
}

.student-accounts-heading p,
.student-accounts-empty {
    color: var(--gray);
    font-size: 0.9rem;
}

.student-accounts-heading .student-accounts-notice {
    color: #28745e;
    font-weight: 600;
}

.student-accounts-heading .student-accounts-error {
    color: #a33f4b;
    font-weight: 600;
}

.student-accounts-list {
    list-style: none;
    display: grid;
    gap: 8px;
}

.student-accounts-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 10px 12px;
    background: rgba(245, 250, 248, 0.9);
    border-radius: 10px;
}

.student-accounts-name {
    color: #2d4a3a;
    font-weight: 600;
}

.student-accounts-actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}

.student-accounts-action,
.password-dialog-cancel,
.password-dialog-submit {
    border: 0;
    border-radius: 20px;
    padding: 7px 14px;
    cursor: pointer;
    font: inherit;
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.student-accounts-action {
    color: #2d4a3a;
    background: rgba(123, 200, 196, 0.22);
}

.student-accounts-action.is-enter {
    color: white;
    background: #5BA8A4;
}

.student-accounts-action:disabled {
    cursor: wait;
    opacity: 0.6;
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

/* ========== Toast（与任务中心保持一致） ========== */
:global(.custom-toast) {
    position: fixed;
    top: 30px;
    left: 50%;
    transform: translateX(-50%) translateY(-20px);
    padding: 8px 25px;
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(12px);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
    font-size: 1.05rem;
    font-weight: 500;
    color: #2d4a3a;
    z-index: 9999;
    opacity: 0;
    transition: opacity 0.3s ease, transform 0.3s ease;
    border: 1px solid rgba(255, 255, 255, 0.6);
}

:global(.custom-toast.toast-visible) {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
}

:global(.custom-toast.toast-success) {
    border: 4px solid #4caf50;
    color: #1e4a2a;
}

:global(.custom-toast.toast-error) {
    border: 4px solid #ef5350;
    color: #7a2a2a;
}

:global(.custom-toast.toast-info) {
    border: 4px solid #42a5f5;
    color: #1a3a5a;
}

@media (max-width: 600px) {
    .admin-title {
        font-size: 1.3rem;
    }

    .student-accounts-heading,
    .student-accounts-row {
        align-items: stretch;
        flex-direction: column;
    }

    .student-accounts-actions {
        flex-direction: column;
    }

    .student-accounts-action {
        width: 100%;
        justify-content: center;
    }
}
</style>
