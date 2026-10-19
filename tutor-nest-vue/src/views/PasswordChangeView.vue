<!--
  PasswordChangeView.vue - 修改密码（Riso Campus 纸面表单）
  ----------------------------------------------------------------------------
  视觉：纸白表单卡 + 墨线边框 + 错位硬阴影 + 奶油黄信息栏 + 白边照片贴纸。
  业务契约保持：identityGateway.changePassword(current, new)、IdentityGatewayError 映射、
  校验顺序与文案语义、成功后清空输入并提示其他设备退出登录。
-->
<template>
  <div class="page page--narrow password-page">
    <section class="panel password-card" aria-labelledby="password-title">
      <!-- 左侧：安全提示 + 站内插画（纯装饰） -->
      <aside class="password-aside">
        <span class="eyebrow">NEST / 安全中心</span>
        <p class="password-aside__title">守住你的<br />学习档案</p>
        <p class="password-aside__text">密码是学习档案的第一道舱门，定期更换更安心。</p>

        <ul class="password-aside__list">
          <li><i class="fas fa-check" aria-hidden="true"></i> 新密码 6～128 个字符</li>
          <li><i class="fas fa-check" aria-hidden="true"></i> 建议混合大小写与数字</li>
          <li><i class="fas fa-check" aria-hidden="true"></i> 不要与其他网站共用</li>
        </ul>

        <figure class="password-aside__photo">
          <img v-if="!asideImageBroken" class="password-aside__img" :src="asideImage"
            alt="安全中心插画：空间站在星云中巡视" loading="lazy" decoding="async"
            @error="asideImageBroken = true" />
          <div v-else class="password-aside__img-fallback" aria-hidden="true">
            <i class="fas fa-shield-halved"></i>
          </div>
        </figure>
        <span class="password-aside__tape" aria-hidden="true"></span>
        <span class="password-aside__stamp mono" aria-hidden="true">SECURE / 01</span>
      </aside>

      <!-- 右侧：表单 -->
      <div class="password-main">
        <header class="password-main__head">
          <h1 id="password-title" class="password-title">修改密码</h1>
          <p class="password-subtitle">修改后其他设备登录状态将失效，当前设备保持登录。</p>
        </header>

        <form class="password-form" @submit.prevent="handleSubmit">
          <!-- 当前密码 -->
          <div class="field">
            <label class="field-label" for="current-password">当前密码</label>
            <div class="input-wrap">
              <i class="fas fa-lock" aria-hidden="true"></i>
              <input id="current-password" v-model="currentPassword" class="input password-input"
                :type="showCurrent ? 'text' : 'password'" autocomplete="current-password" placeholder="输入当前密码" required
                maxlength="128" :disabled="saving" autofocus />
              <button type="button" class="input-wrap__action" :aria-label="showCurrent ? '隐藏当前密码' : '显示当前密码'"
                :aria-pressed="showCurrent" :disabled="saving" @click="showCurrent = !showCurrent">
                <i :class="['fas', showCurrent ? 'fa-eye-slash' : 'fa-eye']" aria-hidden="true"></i>
              </button>
            </div>
          </div>

          <!-- 新密码 -->
          <div class="field">
            <label class="field-label" for="new-password">新密码</label>
            <div class="input-wrap">
              <i class="fas fa-key" aria-hidden="true"></i>
              <input id="new-password" v-model="newPassword" class="input password-input"
                :type="showNew ? 'text' : 'password'" autocomplete="new-password" placeholder="新密码（6～128 个字符）" required
                minlength="6" maxlength="128" :disabled="saving" />
              <button type="button" class="input-wrap__action" :aria-label="showNew ? '隐藏新密码' : '显示新密码'"
                :aria-pressed="showNew" :disabled="saving" @click="showNew = !showNew">
                <i :class="['fas', showNew ? 'fa-eye-slash' : 'fa-eye']" aria-hidden="true"></i>
              </button>
            </div>

            <!-- 本地强度提示 -->
            <div v-if="newPassword" class="password-strength" :class="`is-${strength.level}`">
              <div class="password-strength__head">
                <span>密码强度</span>
                <strong>{{ strength.label }}</strong>
              </div>
              <div class="progress"
                :class="strength.level === 'medium' ? 'progress--warm' : (strength.level === 'strong' ? 'progress--cyan' : '')">
                <span class="progress__bar" :style="{ width: strength.percent + '%' }"></span>
              </div>
              <p class="field-hint">{{ strength.hint }}</p>
            </div>
            <p v-else class="field-hint">密码长度须为 6～128 个字符。</p>
          </div>

          <!-- 确认新密码 -->
          <div class="field">
            <label class="field-label" for="confirm-password">确认新密码</label>
            <div class="input-wrap">
              <i class="fas fa-key" aria-hidden="true"></i>
              <input id="confirm-password" v-model="confirmPassword" class="input password-input"
                :type="showConfirm ? 'text' : 'password'" autocomplete="new-password" placeholder="再次输入新密码" required
                minlength="6" maxlength="128" :disabled="saving" />
              <button type="button" class="input-wrap__action" :aria-label="showConfirm ? '隐藏确认密码' : '显示确认密码'"
                :aria-pressed="showConfirm" :disabled="saving" @click="showConfirm = !showConfirm">
                <i :class="['fas', showConfirm ? 'fa-eye-slash' : 'fa-eye']" aria-hidden="true"></i>
              </button>
            </div>
            <p v-if="confirmPassword && newPassword !== confirmPassword" class="field-hint field-error password-mismatch">
              <i class="fas fa-circle-exclamation" aria-hidden="true"></i> 两次输入的新密码不一致
            </p>
          </div>

          <!-- 内联反馈 -->
          <p v-if="error" class="field-error password-alert password-alert--error" role="alert">
            <i class="fas fa-circle-exclamation" aria-hidden="true"></i>{{ error }}
          </p>
          <p v-if="success" class="password-alert password-alert--ok" role="status">
            <i class="fas fa-circle-check" aria-hidden="true"></i>{{ success }}
          </p>

          <div class="form-actions">
            <button type="button" class="btn btn--ghost" :disabled="saving" @click="goBack">返回</button>
            <button type="submit" class="btn btn--primary password-submit" :disabled="saving">
              <span v-if="saving" class="spinner" aria-hidden="true"></span>
              {{ saving ? '保存中…' : '确认修改' }}
            </button>
          </div>
        </form>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { identityGateway, IdentityGatewayError } from '@/gateways/identityGateway'

const router = useRouter()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showCurrent = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)
const error = ref('')
const success = ref('')
const saving = ref(false)
const asideImageBroken = ref(false)
const asideImage = `${import.meta.env.BASE_URL}cards/cosmos-c4.png`

/** 本地密码强度评估（纯展示，不参与提交校验） */
const strength = computed(() => {
  const value = newPassword.value || ''
  let score = 0
  if (value.length >= 6) score += 1
  if (value.length >= 10) score += 1
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score += 1
  if (/\d/.test(value)) score += 1
  if (/[^A-Za-z0-9]/.test(value)) score += 1

  const level = score <= 2 ? 'weak' : score === 3 ? 'medium' : 'strong'
  const map = {
    weak: { label: '偏弱', hint: '再加点长度，或混合大小写字母与数字。' },
    medium: { label: '中等', hint: '再补一个数字或符号会更稳。' },
    strong: { label: '强', hint: '强度不错，记得不要与其他网站共用。' }
  }
  return {
    level,
    label: map[level].label,
    hint: map[level].hint,
    percent: Math.min(100, 30 + score * 14)
  }
})

// 输入变化时清掉旧错误，避免错误提示滞留
watch([currentPassword, newPassword, confirmPassword], () => {
  if (error.value) error.value = ''
})

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
    showCurrent.value = false
    showNew.value = false
    showConfirm.value = false
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
  padding-top: 40px;
}

.password-card {
  display: grid;
  grid-template-columns: minmax(0, 0.88fr) minmax(0, 1.12fr);
  overflow: hidden;
}

/* ---------- 左侧信息面板：奶油黄色块 ---------- */
.password-aside {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 30px 26px 34px;
  background: var(--butter);
  border-right: 1.5px solid var(--ink);
  overflow: hidden;
}

.password-aside::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 10px;
  background: var(--vermilion);
  border-top: 1.5px solid var(--ink);
}

.password-aside__title {
  margin-top: 2px;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 2.4vw, 1.85rem);
  font-weight: 800;
  line-height: 1.22;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.password-aside__text {
  color: var(--ink-soft);
  font-size: 0.84rem;
}

.password-aside__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--ink-soft);
}

.password-aside__list li {
  display: flex;
  align-items: center;
  gap: 9px;
}

.password-aside__list i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 5px;
  font-size: 0.54rem;
  color: var(--ink);
  background: var(--lime);
  border: 1.5px solid var(--ink);
  flex-shrink: 0;
}

.password-aside__photo {
  position: relative;
  width: min(78%, 210px);
  margin: auto auto 10px;
  padding: 7px;
  background: var(--paper-1);
  border: 1.5px solid var(--ink);
  box-shadow: 4px 4px 0 var(--ink);
  transform: rotate(-2.5deg);
  transition: transform var(--dur-slow) var(--ease-out);
}

.password-aside__photo:hover {
  transform: rotate(0deg) translateY(-3px);
}

.password-aside__img {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border: 1px solid var(--line-strong);
}

.password-aside__img-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 4 / 3;
  border: 1px solid var(--line-strong);
  background: var(--sky);
  color: var(--ink);
  font-size: 1.8rem;
}

.password-aside__tape {
  position: absolute;
  top: 22px;
  right: -16px;
  width: 88px;
  height: 24px;
  background: var(--paper-1);
  border: 1.5px solid var(--ink);
  transform: rotate(24deg);
  pointer-events: none;
}

.password-aside__stamp {
  position: absolute;
  left: 26px;
  bottom: 20px;
  font-size: 0.6rem;
  font-weight: 600;
  letter-spacing: 0.2em;
  color: var(--ink-soft);
  opacity: 0.72;
}

/* ---------- 右侧表单 ---------- */
.password-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 32px 32px 30px;
  background: var(--paper-1);
}

.password-main__head {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.password-title {
  font-size: clamp(1.5rem, 2.6vw, 1.9rem);
}

.password-subtitle {
  color: var(--text-3);
  font-size: 0.82rem;
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-wrap .password-input {
  padding-right: 50px;
}

/* 强度提示 */
.password-strength {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 2px;
}

.password-strength__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.74rem;
  color: var(--text-3);
}

.password-strength__head strong {
  font-size: 0.76rem;
  font-weight: 800;
}

.password-strength.is-weak .password-strength__head strong { color: var(--vermilion); }
.password-strength.is-medium .password-strength__head strong { color: var(--ink-soft); }
.password-strength.is-strong .password-strength__head strong { color: var(--teal); }

.password-mismatch {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 内联反馈：印刷色块 */
.password-alert {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 10px 14px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  font-size: 0.82rem;
  font-weight: 600;
  line-height: 1.5;
  box-shadow: 2px 2px 0 var(--ink);
}

.password-alert i {
  margin-top: 0.2em;
  flex-shrink: 0;
}

.password-alert--error {
  background: var(--paper-1);
  border-color: var(--vermilion);
}

.password-alert--ok {
  color: var(--ink);
  background: var(--lime);
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 6px;
}

.password-submit {
  flex: 1;
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .password-card {
    grid-template-columns: minmax(0, 1fr);
  }

  .password-aside {
    padding: 22px 22px 28px;
    border-right: 0;
    border-bottom: 1.5px solid var(--ink);
  }

  .password-aside__photo {
    display: none;
  }

  .password-aside__tape {
    display: none;
  }

  .password-main {
    padding: 24px 22px 26px;
  }
}

@media (max-width: 760px) {
  .password-page {
    padding-top: 18px;
  }

  .password-aside {
    padding: 18px 16px 24px;
  }

  .password-aside__list {
    display: none;
  }

  .password-aside__stamp {
    display: none;
  }

  .password-main {
    padding: 20px 16px 22px;
  }

  .password-form {
    gap: 14px;
  }

  .form-actions {
    flex-direction: column-reverse;
  }

  .form-actions .btn {
    width: 100%;
  }
}
</style>
