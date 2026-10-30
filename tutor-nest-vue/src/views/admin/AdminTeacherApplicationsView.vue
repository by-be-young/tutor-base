<!-- src/views/admin/AdminTeacherApplicationsView.vue -->
<template>
  <div class="admin-page">
    <section class="panel applications-head">
      <div class="applications-head__copy">
        <span class="eyebrow">REGISTRATION / 教师注册</span>
        <h2 class="applications-head__title">教师注册审核</h2>
        <p class="applications-head__sub">
          只有超级管理员可以审批。通过后申请人立即成为管理员，原密码可直接登录管理台；驳回不影响其重新申请。
        </p>
        <p class="applications-head__who">
          <i class="fas fa-user-shield" aria-hidden="true"></i>
          当前超级管理员 · {{ authStore.username }}
        </p>
      </div>

      <div class="applications-stat" aria-label="待审核申请数量">
        <span class="applications-stat__value">{{ pendingCount }}</span>
        <span class="applications-stat__label">待审核</span>
      </div>
    </section>

    <div class="seg applications-tabs" role="tablist" aria-label="教师申请状态">
      <button v-for="tab in tabs" :key="tab.id" type="button" role="tab" class="seg__btn"
        :class="{ 'is-active': activeStatus === tab.id }" :aria-selected="activeStatus === tab.id"
        @click="changeTab(tab.id)">
        <i :class="tab.icon" aria-hidden="true"></i>
        <span>{{ tab.label }}</span>
      </button>
      <button type="button" class="btn btn--ghost btn--sm applications-refresh" :disabled="loading"
        @click="load(activeStatus)">
        <i class="fas" :class="loading ? 'fa-circle-notch fa-spin' : 'fa-rotate-right'" aria-hidden="true"></i>
        刷新
      </button>
    </div>

    <section v-if="approveTarget" class="panel review-confirm" aria-live="polite">
      <div>
        <strong>确认通过 {{ approveTarget.username }} 的申请？</strong>
        <p>通过后系统会立即创建管理员账户，申请人可用原密码登录管理台。此操作不可撤销。</p>
      </div>
      <div class="review-confirm__actions">
        <button type="button" class="btn btn--ghost btn--sm" :disabled="actionBusy"
          @click="approveTarget = null">取消</button>
        <button type="button" class="btn btn--primary btn--sm" :disabled="actionBusy"
          @click="approve(approveTarget)">
          <i class="fas" :class="actionBusy ? 'fa-circle-notch fa-spin' : 'fa-check'" aria-hidden="true"></i>
          确认通过
        </button>
      </div>
    </section>

    <section v-if="rejectTarget" class="panel review-confirm review-confirm--reject" aria-live="polite">
      <div class="review-confirm__form">
        <strong>驳回 {{ rejectTarget.username }} 的申请</strong>
        <p>请填写驳回理由，申请人可据此修改后重新提交。</p>
        <textarea v-model="rejectReason" class="textarea" rows="3" maxlength="500"
          placeholder="例如：用户名不符合规范，请使用真实姓名拼音重新申请"></textarea>
        <p v-if="reasonError" class="review-confirm__error" role="alert">{{ reasonError }}</p>
      </div>
      <div class="review-confirm__actions">
        <button type="button" class="btn btn--ghost btn--sm" :disabled="actionBusy"
          @click="closeReject">取消</button>
        <button type="button" class="btn btn--danger btn--sm" :disabled="actionBusy"
          @click="reject(rejectTarget)">
          <i class="fas" :class="actionBusy ? 'fa-circle-notch fa-spin' : 'fa-xmark'" aria-hidden="true"></i>
          确认驳回
        </button>
      </div>
    </section>

    <p v-if="error" class="applications-error" role="alert">
      <i class="fas fa-triangle-exclamation" aria-hidden="true"></i>
      <span>{{ error }}</span>
      <button type="button" class="btn btn--ghost btn--sm" @click="load(activeStatus)">重试</button>
    </p>

    <section class="admin-panel" aria-live="polite">
      <div v-if="loading && items.length === 0" class="application-skeleton" aria-hidden="true">
        <div v-for="index in 3" :key="index" class="panel application-skeleton__row">
          <span class="skeleton application-skeleton__avatar"></span>
          <span class="application-skeleton__lines">
            <span class="skeleton application-skeleton__title"></span>
            <span class="skeleton application-skeleton__meta"></span>
          </span>
        </div>
      </div>

      <EmptyState v-else-if="items.length === 0" icon="fas fa-user-check"
        :title="emptyTitle"
        :text="emptyText">
        <button v-if="activeStatus !== 'pending'" type="button" class="btn btn--soft btn--sm"
          @click="changeTab('pending')">查看待审核申请</button>
      </EmptyState>

      <ul v-else class="application-list" role="list" aria-label="教师注册申请列表">
        <li v-for="item in items" :key="item.id" class="panel application-row">
          <span class="avatar application-avatar" aria-hidden="true">{{ initialOf(item.username) }}</span>

          <div class="application-main">
            <div class="application-title">
              <strong class="application-name">{{ item.username }}</strong>
              <span class="chip application-status" :class="statusClass(item.status)">
                {{ statusLabel(item.status) }}
              </span>
            </div>
            <p class="application-meta">
              <i class="far fa-clock" aria-hidden="true"></i>
              提交于 {{ formatTime(item.createdAt) }}
              <span class="application-id">#{{ item.id }}</span>
            </p>
            <p v-if="item.reviewedAt" class="application-meta">
              <i class="fas fa-clipboard-check" aria-hidden="true"></i>
              审核于 {{ formatTime(item.reviewedAt) }}
            </p>
            <p v-if="item.status === 'REJECTED'" class="application-reason">
              <i class="fas fa-circle-info" aria-hidden="true"></i>
              驳回原因：{{ item.rejectionReason }}
            </p>
            <p v-else-if="item.status === 'APPROVED'" class="application-approved">
              <i class="fas fa-shield-halved" aria-hidden="true"></i>
              已创建管理员账户 #{{ item.approvedAccountId }}
            </p>
          </div>

          <div v-if="item.status === 'PENDING'" class="application-actions">
            <button type="button" class="btn btn--primary btn--sm" :disabled="actionBusy"
              @click="openApprove(item)">
              <i class="fas fa-check" aria-hidden="true"></i> 通过
            </button>
            <button type="button" class="btn btn--ghost btn--sm" :disabled="actionBusy"
              @click="openReject(item)">
              <i class="fas fa-xmark" aria-hidden="true"></i> 驳回
            </button>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { teacherApplicationGateway } from '@/gateways/teacherApplicationGateway'
import { showToast } from '@/utils/toast'
import EmptyState from '@/components/ui/EmptyState.vue'

const authStore = useAuthStore()

const activeStatus = ref('pending')
const items = ref([])
const pendingCount = ref(0)
const loading = ref(false)
const error = ref('')
const actionBusy = ref(false)
const approveTarget = ref(null)
const rejectTarget = ref(null)
const rejectReason = ref('')
const reasonError = ref('')

const tabs = [
  { id: 'pending', label: '待审核', icon: 'fas fa-hourglass-half' },
  { id: 'approved', label: '已通过', icon: 'fas fa-circle-check' },
  { id: 'rejected', label: '已驳回', icon: 'fas fa-circle-xmark' },
  { id: 'all', label: '全部', icon: 'fas fa-list' }
]

const emptyTitle = computed(() => {
  if (activeStatus.value === 'pending') return '暂无待审核的教师申请'
  if (activeStatus.value === 'approved') return '还没有已通过的申请'
  if (activeStatus.value === 'rejected') return '还没有已驳回的申请'
  return '还没有教师注册申请'
})

const emptyText = computed(() => {
  if (activeStatus.value === 'pending') return '学习者在首页勾选“我是教师”并提交后，申请会出现在这里。'
  return '切换上方标签可以查看其他状态的申请记录。'
})

async function load(status = activeStatus.value) {
  loading.value = true
  error.value = ''
  try {
    const response = await teacherApplicationGateway.list(status)
    items.value = Array.isArray(response?.items) ? response.items : []
    pendingCount.value = Number(response?.pendingCount) || 0
  } catch (err) {
    error.value = err?.message || '教师申请加载失败，请稍后重试'
    items.value = []
  } finally {
    loading.value = false
  }
}

function changeTab(status) {
  if (activeStatus.value === status && !loading.value) {
    load(status)
    return
  }
  activeStatus.value = status
  closeApprove()
  closeReject()
  load(status)
}

function openApprove(item) {
  closeReject()
  approveTarget.value = item
}

function closeApprove() {
  approveTarget.value = null
}

function openReject(item) {
  closeApprove()
  rejectTarget.value = item
  rejectReason.value = ''
  reasonError.value = ''
}

function closeReject() {
  rejectTarget.value = null
  rejectReason.value = ''
  reasonError.value = ''
}

async function approve(item) {
  if (!item || actionBusy.value) return
  actionBusy.value = true
  try {
    await teacherApplicationGateway.approve(item.id)
    showToast(`已通过 ${item.username} 的教师申请`, 'success')
    approveTarget.value = null
    await load(activeStatus.value)
  } catch (err) {
    showToast(err?.message || '审核通过失败，请稍后重试', 'error')
  } finally {
    actionBusy.value = false
  }
}

async function reject(item) {
  if (!item || actionBusy.value) return
  const reason = rejectReason.value.trim()
  if (!reason) {
    reasonError.value = '请填写驳回理由'
    return
  }
  actionBusy.value = true
  try {
    await teacherApplicationGateway.reject(item.id, reason)
    showToast(`已驳回 ${item.username} 的教师申请`, 'success')
    closeReject()
    await load(activeStatus.value)
  } catch (err) {
    reasonError.value = err?.message || '驳回失败，请稍后重试'
  } finally {
    actionBusy.value = false
  }
}

function statusLabel(status) {
  return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[status] || status
}

function statusClass(status) {
  return {
    PENDING: 'application-status--pending',
    APPROVED: 'application-status--approved',
    REJECTED: 'application-status--rejected'
  }[status] || ''
}

function initialOf(username) {
  return (username || 'T').slice(0, 1).toUpperCase()
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => load('pending'))
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.applications-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px;
  border-radius: var(--r-lg);
  flex-wrap: wrap;
}

.applications-head__copy {
  min-width: 0;
  flex: 1 1 420px;
}

.applications-head__title {
  margin: 6px 0 8px;
  font-family: var(--font-display);
  font-size: clamp(1.25rem, 2.2vw, 1.6rem);
  font-weight: 800;
  color: var(--ink);
}

.applications-head__sub {
  max-width: 720px;
  margin: 0;
  color: var(--text-2);
  font-size: 0.86rem;
  line-height: 1.65;
}

.applications-head__who {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 10px 0 0;
  padding: 4px 10px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  background: var(--butter);
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  letter-spacing: 0.04em;
}

.applications-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 112px;
  padding: 14px 20px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-lg);
  background: var(--paper-0);
  box-shadow: 4px 4px 0 var(--ink);
}

.applications-stat__value {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 800;
  line-height: 1;
  color: var(--vermilion);
}

.applications-stat__label {
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.1em;
  color: var(--text-3);
}

.applications-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.applications-refresh {
  margin-left: auto;
}

.review-confirm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 20px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-lg);
  background: var(--paper-0);
  box-shadow: 4px 4px 0 var(--ink);
  flex-wrap: wrap;
}

.review-confirm strong {
  color: var(--ink);
  font-size: 0.94rem;
}

.review-confirm p {
  margin: 4px 0 0;
  color: var(--text-2);
  font-size: 0.8rem;
  line-height: 1.6;
}

.review-confirm__form {
  flex: 1 1 420px;
  min-width: 0;
}

.review-confirm__form .textarea {
  width: 100%;
  margin-top: 10px;
}

.review-confirm__error {
  color: var(--vermilion) !important;
  font-weight: 700;
}

.review-confirm__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.review-confirm--reject {
  align-items: flex-end;
  border-color: var(--vermilion);
  box-shadow: 4px 4px 0 var(--vermilion);
}

.applications-error {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border: 1.5px solid var(--vermilion);
  border-radius: var(--r-sm);
  background: rgba(237, 75, 42, 0.08);
  color: #a82b12;
  font-size: 0.82rem;
  font-weight: 600;
}

.application-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.application-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
  border-radius: var(--r-lg);
  transition: transform var(--dur-fast), box-shadow var(--dur-fast);
}

.application-row:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 var(--ink);
}

.application-avatar {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  font-size: 0.95rem;
}

.application-main {
  flex: 1 1 auto;
  min-width: 0;
}

.application-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.application-name {
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 800;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.application-status {
  font-size: 0.66rem;
  letter-spacing: 0.06em;
}

.application-status--pending {
  background: var(--butter);
  color: var(--ink);
}

.application-status--approved {
  background: var(--lime);
  color: var(--ink);
}

.application-status--rejected {
  background: rgba(237, 75, 42, 0.14);
  color: var(--vermilion);
}

.application-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin: 5px 0 0;
  color: var(--text-3);
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.02em;
}

.application-id {
  padding: 1px 6px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-full);
}

.application-reason,
.application-approved {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 8px 0 0;
  padding: 7px 10px;
  border-radius: var(--r-sm);
  font-size: 0.76rem;
  line-height: 1.55;
}

.application-reason {
  background: rgba(237, 75, 42, 0.08);
  color: #a82b12;
}

.application-approved {
  background: rgba(154, 203, 62, 0.16);
  color: #3f5a12;
}

.application-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.application-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.application-skeleton__row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
}

.application-skeleton__avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  flex-shrink: 0;
}

.application-skeleton__lines {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.application-skeleton__title {
  width: 180px;
  height: 18px;
}

.application-skeleton__meta {
  width: 260px;
  height: 12px;
}

@media (max-width: 768px) {
  .applications-head {
    padding: 18px 16px;
    gap: 14px;
  }

  .applications-stat {
    min-width: 88px;
    padding: 10px 14px;
    box-shadow: 3px 3px 0 var(--ink);
  }

  .applications-stat__value {
    font-size: 1.5rem;
  }

  .applications-refresh {
    margin-left: 0;
  }

  .application-row {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 12px;
    padding: 14px;
  }

  .application-avatar {
    width: 38px;
    height: 38px;
  }

  .application-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .review-confirm {
    align-items: stretch;
    padding: 14px;
  }

  .review-confirm__actions {
    justify-content: flex-end;
  }
}
</style>
