<!-- src/views/admin/AdminProfileView.vue -->
<template>
  <div class="admin-page">
    <section class="panel profile-card">
      <span class="avatar profile-card__avatar" aria-hidden="true">{{ userInitial }}</span>
      <div class="profile-card__main">
        <span class="eyebrow">ADMINISTRATOR / 管理员</span>
        <h2 class="profile-card__name">{{ authStore.username }}</h2>
        <div class="profile-card__chips">
          <span class="chip chip--coral"><i class="fas fa-shield-halved" aria-hidden="true"></i> 管理员</span>
          <span class="chip"><i class="fas fa-hashtag" aria-hidden="true"></i> 账户 ID · {{ accountId ?? '—' }}</span>
        </div>
      </div>
    </section>

    <section class="panel panel--pad claim-panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">CLAIMS / 认领概览</span>
          <h2 class="section-title">我负责的内容</h2>
        </div>
        <router-link to="/admin/content" class="btn btn--soft btn--sm">
          <i class="fas fa-book-open" aria-hidden="true"></i> 管理课件
        </router-link>
      </div>

      <div class="claim-grid">
        <article class="claim-item claim-item--lime">
          <p class="claim-item__value">{{ subjectCount }}</p>
          <p class="claim-item__label">认领学科</p>
        </article>
        <article class="claim-item claim-item--sky">
          <p class="claim-item__value">{{ articleCount }}</p>
          <p class="claim-item__label">认领课件</p>
        </article>
        <article class="claim-item claim-item--amber">
          <p class="claim-item__value">{{ unclaimedCount }}</p>
          <p class="claim-item__label">待认领课件</p>
        </article>
      </div>

      <div v-if="subjects.length" class="claim-subjects">
        <span v-for="subject in subjects" :key="subject" class="chip chip--lime">{{ subject }}</span>
      </div>
      <p v-else class="claim-empty">
        还没有认领任何学科，去「课件」页认领后即可管理对应学员的内容授权与批阅。
      </p>
    </section>

    <section class="panel panel--pad action-panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">ACCOUNT / 账户操作</span>
          <h2 class="section-title">账户与入口</h2>
        </div>
      </div>
      <div class="action-row">
        <router-link to="/settings/password" class="btn btn--soft">
          <i class="fas fa-key" aria-hidden="true"></i> 修改密码
        </router-link>
        <button type="button" class="btn btn--primary" :disabled="loggingOut" @click="handleLogout">
          <i class="fas" :class="loggingOut ? 'fa-circle-notch fa-spin' : 'fa-arrow-right-from-bracket'"
            aria-hidden="true"></i>
          {{ loggingOut ? '退出中…' : '退出登录' }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useCoursewareStore } from '@/stores/coursewareStore'

const router = useRouter()
const authStore = useAuthStore()
const coursewareStore = useCoursewareStore()

const loggingOut = ref(false)

const accountId = computed(() => authStore.currentUser?.accountId ?? null)
const userInitial = computed(() => (authStore.username || 'A').slice(0, 1).toUpperCase())
const subjects = computed(() => coursewareStore.visibleSubjects || [])
const subjectCount = computed(() => subjects.value.length)
// 课件数包含「整科认领」带出的文章，因此用 visibleArticleIds 而不是单独的 articleClaims。
const articleCount = computed(() => {
  const ids = coursewareStore.visibleArticleIds
  return ids instanceof Set ? ids.size : 0
})
const unclaimedCount = computed(() => coursewareStore.unclaimedCount ?? 0)

async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await authStore.logout()
  } finally {
    loggingOut.value = false
    await router.push('/')
  }
}
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 24px;
  border-radius: var(--r-lg);
}

.profile-card__avatar {
  width: 64px;
  height: 64px;
  font-size: 1.4rem;
  background: var(--butter);
  box-shadow: 3px 3px 0 var(--ink);
  flex-shrink: 0;
}

.profile-card__main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.profile-card__name {
  font-size: 1.5rem;
  letter-spacing: -0.02em;
  word-break: break-all;
}

.profile-card__chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.section-title {
  font-size: 1.1rem;
}

.claim-panel,
.action-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.claim-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.claim-item {
  padding: 18px 20px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-md);
  box-shadow: 3px 3px 0 var(--ink);
}

.claim-item--lime { background: var(--lime); }
.claim-item--sky { background: var(--sky); }
.claim-item--amber { background: var(--butter); }

.claim-item__value {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.claim-item__label {
  margin-top: 8px;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--ink);
}

.claim-subjects {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.claim-empty {
  font-size: 0.86rem;
  color: var(--text-3);
}

.action-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .profile-card {
    padding: 18px;
    gap: 14px;
  }

  .profile-card__avatar {
    width: 52px;
    height: 52px;
    font-size: 1.15rem;
  }

  .profile-card__name {
    font-size: 1.25rem;
  }

  .claim-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
  }

  .claim-item {
    padding: 14px;
  }

  .claim-item__value {
    font-size: 1.6rem;
  }

  .claim-item__label {
    font-size: 0.72rem;
  }

  .action-row .btn {
    flex: 1 1 auto;
  }
}
</style>
