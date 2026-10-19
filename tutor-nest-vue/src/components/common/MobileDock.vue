<!-- src/components/common/MobileDock.vue -->
<template>
  <nav v-if="authStore.isLoggedIn" class="mobile-dock" aria-label="移动端快捷导航">
    <router-link v-for="item in leftItems" :key="item.to" :to="item.to" class="dock-item"
      :class="{ 'is-active': isActive(item.to) }">
      <i :class="item.icon" aria-hidden="true"></i>
      <span>{{ item.label }}</span>
    </router-link>

    <button type="button" class="dock-fab" :class="{ 'is-done': checkinStore.todayCheckedIn }"
      :aria-label="checkinStore.todayCheckedIn ? '今日已签到' : '去签到'" @click="openCheckin">
      <i :class="checkinStore.todayCheckedIn ? 'fas fa-check' : 'fas fa-calendar-check'" aria-hidden="true"></i>
      <span class="dock-fab__label">{{ checkinStore.todayCheckedIn ? '已签' : '签到' }}</span>
    </button>

    <router-link v-for="item in rightItems" :key="item.to" :to="item.to" class="dock-item"
      :class="{ 'is-active': isActive(item.to) }">
      <i :class="item.icon" aria-hidden="true"></i>
      <span>{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useCheckinStore } from '@/stores/checkinStore'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const checkinStore = useCheckinStore()

const leftItems = computed(() => [
  { to: '/', label: '首页', icon: 'fas fa-house' },
  { to: '/tasks', label: '任务', icon: 'fas fa-list-check' }
])

const rightItems = computed(() => {
  const items = [{ to: '/wrong-questions', label: '错题', icon: 'fas fa-book-medical' }]
  if (authStore.isAdministrator) {
    items.push({ to: '/admin', label: '管理', icon: 'fas fa-shield-halved' })
  } else {
    items.push({ to: '/settings/password', label: '设置', icon: 'fas fa-gear' })
  }
  return items
})

function isActive(to) {
  if (to === '/') return route.path === '/'
  return route.path.startsWith(to)
}

function openCheckin() {
  if (checkinStore.todayCheckedIn) {
    showToast('今天已经签到过了，明天见', 'info')
    return
  }
  if (route.path !== '/tasks') router.push('/tasks')
  checkinStore.openModal()
}

onMounted(() => {
  if (authStore.isLoggedIn && checkinStore.month === null) {
    checkinStore.loadMonth()
  }
})
</script>

<style scoped>
.mobile-dock {
  display: none;
}

@media (max-width: 768px) {
  .mobile-dock {
    position: fixed;
    left: 10px;
    right: 10px;
    bottom: calc(10px + env(safe-area-inset-bottom));
    z-index: 130;
    display: flex;
    align-items: center;
    justify-content: space-around;
    height: 64px;
    padding: 0 6px;
    border-radius: 16px;
    border: 2px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 4px 4px 0 var(--ink);
  }

  .dock-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
    flex: 1;
    height: 48px;
    border-radius: 10px;
    color: var(--text-3);
    font-size: 0.62rem;
    font-weight: 700;
    transition: color var(--dur-fast), background var(--dur-fast);
  }

  .dock-item i {
    font-size: 1rem;
  }

  .dock-item.is-active {
    color: var(--ink);
    background: var(--lime);
  }

  .dock-fab {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 1px;
    width: 58px;
    height: 58px;
    margin-top: -24px;
    border-radius: 50%;
    border: 2px solid var(--ink);
    color: var(--paper-1);
    background: var(--vermilion);
    box-shadow: 3px 3px 0 var(--ink);
  }

  .dock-fab i {
    font-size: 1rem;
  }

  .dock-fab__label {
    font-size: 0.54rem;
    font-weight: 800;
    letter-spacing: 0.06em;
  }

  .dock-fab.is-done {
    background: var(--lime);
    color: var(--ink);
  }
}
</style>
