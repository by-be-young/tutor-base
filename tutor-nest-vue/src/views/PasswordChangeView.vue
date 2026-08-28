<template>
  <div class="password-page">
    <div class="password-card">
      <h1 class="password-title">修改密码</h1>
      <p class="password-subtitle">修改后其他设备登录状态将失效，当前设备保持登录。</p>

      <form class="password-form" @submit.prevent="handleSubmit">
        <label for="current-password">当前密码</label>
        <input id="current-password" v-model="currentPassword" type="password" autocomplete="current-password"
          placeholder="输入当前密码" required maxlength="128" :disabled="saving" autofocus />

        <label for="new-password">新密码</label>
        <input id="new-password" v-model="newPassword" type="password" autocomplete="new-password"
          placeholder="新密码（6～128 个字符）" required minlength="6" maxlength="128" :disabled="saving" />

        <label for="confirm-password">确认新密码</label>
        <input id="confirm-password" v-model="confirmPassword" type="password" autocomplete="new-password"
          placeholder="再次输入新密码" required minlength="6" maxlength="128" :disabled="saving" />

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <p v-if="success" class="form-success" role="status">{{ success }}</p>

        <div class="form-actions">
          <button type="button" class="btn-cancel" :disabled="saving" @click="goBack">返回</button>
          <button type="submit" class="btn-submit" :disabled="saving">
            {{ saving ? '保存中…' : '确认修改' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { identityGateway, IdentityGatewayError } from '@/gateways/identityGateway'

const router = useRouter()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const success = ref('')
const saving = ref(false)

function failureMessage(err) {
  if (!(err instanceof IdentityGatewayError)) return '修改密码失败，请稍后重试'
  if (err.status === 401) return '当前密码不正确'
  if (err.status === 400) return '新密码不符合要求（6～128 个字符）'
  return err.message || '修改密码失败，请稍后重试'
}

async function handleSubmit() {
  error.value = ''
  success.value = ''

  if (!currentPassword.value) {
    error.value = '请输入当前密码'
    return
  }
  if (newPassword.value.length < 6 || newPassword.value.length > 128) {
    error.value = '新密码长度须为 6～128 个字符'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致'
    return
  }

  saving.value = true
  try {
    await identityGateway.changePassword(currentPassword.value, newPassword.value)
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    success.value = '密码已修改，其他设备已退出登录。'
  } catch (err) {
    error.value = failureMessage(err)
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.password-page {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 24px;
}

.password-card {
  width: 100%;
  max-width: 420px;
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 24px;
  padding: 32px 36px;
  box-shadow: 0 12px 40px rgba(80, 130, 120, 0.1);
}

.password-title {
  font-size: 1.6rem;
  font-weight: 700;
  color: #2d4a3a;
  text-align: center;
}

.password-subtitle {
  font-size: 0.85rem;
  color: var(--gray, #5a7a6a);
  text-align: center;
  margin-top: 6px;
  margin-bottom: 24px;
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.password-form label {
  font-size: 0.9rem;
  color: #2d4a3a;
  margin-top: 10px;
}

.password-form input {
  padding: 11px 16px;
  border: 1px solid rgba(120, 170, 155, 0.25);
  border-radius: 12px;
  font-size: 0.95rem;
  background: rgba(255, 255, 255, 0.65);
  color: #2d4a3a;
  transition: border-color 0.2s, box-shadow 0.2s;
  font-family: inherit;
}

.password-form input:focus {
  outline: none;
  border-color: var(--teal-dark, #5BA8A4);
  box-shadow: 0 0 0 3px rgba(91, 168, 164, 0.15);
}

.password-form input:disabled {
  opacity: 0.6;
}

.form-error {
  color: #d57587;
  font-size: 0.85rem;
  margin-top: 10px;
  min-height: 1.2em;
}

.form-success {
  color: #4c8a5e;
  font-size: 0.85rem;
  margin-top: 10px;
  min-height: 1.2em;
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 22px;
}

.form-actions button {
  flex: 1;
  padding: 11px 0;
  border-radius: 12px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
  font-family: inherit;
}

.btn-cancel {
  background: rgba(255, 255, 255, 0.4);
  border: 1px solid rgba(120, 170, 155, 0.25);
  color: var(--gray, #5a7a6a);
}

.btn-cancel:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.7);
}

.btn-submit {
  background: rgba(91, 168, 164, 0.3);
  border: none;
  color: #2d4a3a;
}

.btn-submit:hover:not(:disabled) {
  background: rgba(91, 168, 164, 0.5);
  transform: translateY(-1px);
}

.form-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .password-page {
    padding: 24px 12px;
  }

  .password-card {
    padding: 24px 20px;
    border-radius: 18px;
  }
}
</style>
