<!-- src/components/common/AppHeader.vue -->
<template>
  <header class="site-header" :class="{ 'is-scrolled': scrolled }">
    <div class="header__inner">
      <router-link to="/" class="brand" aria-label="NEST 学巢 首页">
        <BrandMark :size="32" />
        <span class="brand__text">
          <strong>NEST</strong>
          <span>学巢</span>
        </span>
      </router-link>

      <nav class="header__nav" aria-label="主导航">
        <router-link v-for="item in navItems" :key="item.to" :to="item.to" class="nav-link"
          :class="{ 'is-active': isActive(item) }">
          <i :class="item.icon" aria-hidden="true"></i>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="header__actions">
        <button type="button" class="search-trigger" aria-label="搜索与跳转" @click="uiStore.openPalette()">
          <i class="fas fa-magnifying-glass" aria-hidden="true"></i>
          <span class="search-trigger__label">搜索</span>
          <kbd class="kbd search-trigger__kbd">⌘K</kbd>
        </button>

        <button v-if="authStore.isLoggedIn" type="button" class="level-chip" :title="levelTooltip"
          @click="router.push('/tasks?tab=achievements')">
          <LevelBadge :points="tasksStore.lifetimePoints" size="xs" />
          <span class="level-chip__title">{{ levelInfo.title }}</span>
        </button>

        <button v-if="authStore.isLoggedIn" type="button" class="points-chip" title="我的积分"
          @click="router.push('/tasks')">
          <i class="fas fa-coins" aria-hidden="true"></i>
          <span>{{ tasksStore.points }}</span>
        </button>

        <template v-if="authStore.isLoggedIn">
          <div ref="userMenuRef" class="user">
            <button type="button" class="user__trigger" :aria-expanded="userMenuOpen" aria-haspopup="menu"
              @click.stop="userMenuOpen = !userMenuOpen">
              <span class="avatar user__avatar">{{ userInitial }}</span>
              <span class="user__meta">
                <span class="user__name">{{ authStore.username }}</span>
                <span class="user__level">LV.{{ levelInfo.level }} · {{ levelInfo.title }}</span>
              </span>
              <i class="fas fa-chevron-down user__caret" aria-hidden="true"></i>
            </button>

            <transition name="dropdown">
              <div v-if="userMenuOpen" class="user-menu" role="menu">
                <div class="user-menu__head">
                  <span class="avatar avatar--lg">{{ userInitial }}</span>
                  <div>
                    <p class="user-menu__name">{{ authStore.username }}</p>
                    <p class="user-menu__sub">
                      <span v-if="authStore.isAdministrator" class="chip chip--coral">管理员</span>
                      <span v-else class="chip chip--cyan">学习者</span>
                      <span class="user-menu__points">LV.{{ levelInfo.level }} {{ levelInfo.title }} · {{ tasksStore.lifetimePoints }} pts</span>
                    </p>
                  </div>
                </div>
                <button v-for="entry in userMenuItems" :key="entry.label" type="button" class="user-menu__item"
                  role="menuitem" @click="runMenu(entry)">
                  <i :class="entry.icon" aria-hidden="true"></i>
                  <span>{{ entry.label }}</span>
                  <i class="fas fa-arrow-right user-menu__arrow" aria-hidden="true"></i>
                </button>
                <div class="user-menu__foot">
                  <button type="button" class="user-menu__logout" @click="handleLogout">
                    <i class="fas fa-arrow-right-from-bracket" aria-hidden="true"></i> 退出登录
                  </button>
                </div>
              </div>
            </transition>
          </div>
        </template>

        <template v-else>
          <button type="button" class="btn btn--ghost btn--sm header__login" @click="uiStore.requestAuth('login')">
            登录
          </button>
          <button type="button" class="btn btn--primary btn--sm header__register"
            @click="uiStore.requestAuth('register')">
            注册
          </button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useTasksStore } from '@/stores/tasksStore'
import { useUiStore } from '@/stores/uiStore'
import { levelFromPoints } from '@/utils/level'
import BrandMark from '@/components/common/BrandMark.vue'
import LevelBadge from '@/components/ui/LevelBadge.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const tasksStore = useTasksStore()
const uiStore = useUiStore()

const scrolled = ref(false)
const userMenuOpen = ref(false)
const userMenuRef = ref(null)

const navItems = computed(() => {
  const items = [{ to: '/', label: '首页', icon: 'fas fa-house' }]
  if (authStore.isLoggedIn) {
    items.push({ to: '/tasks', label: '任务', icon: 'fas fa-list-check' })
    items.push({ to: '/wrong-questions', label: '错题本', icon: 'fas fa-book-medical' })
  }
  if (authStore.isAdministrator) {
    items.push({ to: '/admin', label: '管理', icon: 'fas fa-shield-halved' })
  }
  return items
})

const userMenuItems = computed(() => {
  const entries = [
    { label: '任务中心', icon: 'fas fa-list-check', to: '/tasks' },
    { label: '成就', icon: 'fas fa-medal', to: '/tasks?tab=achievements' },
    { label: '错题本', icon: 'fas fa-book-medical', to: '/wrong-questions' },
    { label: '修改密码', icon: 'fas fa-key', to: '/settings/password' }
  ]
  if (authStore.isAdministrator) {
    entries.unshift({ label: '管理中心', icon: 'fas fa-shield-halved', to: '/admin' })
  }
  return entries
})

const userInitial = computed(() => (authStore.username || 'N').slice(0, 1).toUpperCase())
const levelInfo = computed(() => levelFromPoints(tasksStore.lifetimePoints))
const levelTooltip = computed(() =>
  `LV.${levelInfo.value.level} ${levelInfo.value.title} · ${levelInfo.value.points} 积分 · 距下一级还差 ${levelInfo.value.toNext}`)

function isActive(item) {
  if (item.to === '/') return route.path === '/'
  return route.path.startsWith(item.to)
}

function runMenu(entry) {
  userMenuOpen.value = false
  router.push(entry.to)
}

async function handleLogout() {
  userMenuOpen.value = false
  try {
    await authStore.logout()
  } finally {
    await router.push('/')
  }
}

function onScroll() {
  scrolled.value = window.scrollY > 8
}

function onDocumentClick(event) {
  if (userMenuRef.value && !userMenuRef.value.contains(event.target)) {
    userMenuOpen.value = false
  }
}

function onKeydown(event) {
  if (event.key === 'Escape') userMenuOpen.value = false
}

watch(() => authStore.isLoggedIn, async (loggedIn) => {
  if (!loggedIn) {
    userMenuOpen.value = false
    return
  }
  if (!tasksStore.isLoading) {
    await tasksStore.load(authStore.currentUser)
  }
})

onMounted(async () => {
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
  onScroll()
  if (authStore.isLoggedIn && !tasksStore.isLoading) {
    await tasksStore.load(authStore.currentUser)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 120;
  background: rgba(243, 236, 221, 0.94);
  border-bottom: 1.5px solid var(--ink);
  transition: box-shadow 0.2s var(--ease-out);
}

.site-header.is-scrolled {
  box-shadow: 0 4px 0 rgba(28, 26, 20, 0.08);
}

.header__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 1320px;
  margin: 0 auto;
  height: 66px;
  padding: 0 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.brand__text {
  display: flex;
  align-items: baseline;
  gap: 7px;
  line-height: 1;
}

.brand__text strong {
  font-family: var(--font-display);
  font-size: 1.02rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--ink);
}

.brand__text span {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  color: var(--text-3);
  letter-spacing: 0.1em;
}

.header__nav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 36px;
  padding: 0 13px;
  border-radius: 8px;
  border: 1.5px solid transparent;
  color: var(--text-2);
  font-size: 0.85rem;
  font-weight: 700;
  transition: background var(--dur-fast), color var(--dur-fast), border-color var(--dur-fast);
}

.nav-link i {
  font-size: 0.8rem;
}

.nav-link:hover {
  color: var(--ink);
  background: var(--paper-2);
}

.nav-link.is-active {
  color: var(--ink);
  background: var(--lime);
  border-color: var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
}

.header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.search-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 8px 0 12px;
  border-radius: 8px;
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  color: var(--text-2);
  font-size: 0.8rem;
  font-weight: 600;
  transition: background var(--dur-fast), box-shadow var(--dur-fast);
}

.search-trigger:hover {
  background: var(--paper-2);
  box-shadow: 2px 2px 0 var(--ink);
}

.search-trigger__kbd {
  pointer-events: none;
}

.points-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1.5px solid var(--ink);
  background: var(--butter);
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 0.8rem;
  font-weight: 700;
  box-shadow: 2px 2px 0 var(--ink);
  transition: transform var(--dur-fast), box-shadow var(--dur-fast);
}

.level-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 36px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  color: var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
  transition: transform var(--dur-fast), box-shadow var(--dur-fast);
}

.level-chip:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 var(--ink);
}

.level-chip__title {
  font-size: 0.72rem;
  font-weight: 800;
  white-space: nowrap;
}

.level-chip :deep(.level-badge__ring) {
  border-width: 1px;
}

.points-chip:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 var(--ink);
}

.user {
  position: relative;
}

.user__trigger {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  height: 42px;
  padding: 0 8px 0 4px;
  border-radius: 10px;
  border: 1.5px solid transparent;
  transition: background var(--dur-fast), border-color var(--dur-fast);
}

.user__trigger:hover,
.user__trigger[aria-expanded='true'] {
  background: var(--paper-1);
  border-color: var(--ink);
}

.user__avatar {
  width: 32px;
  height: 32px;
  font-size: 0.8rem;
}

.user__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.15;
  max-width: 110px;
}

.user__name {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--ink);
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user__level {
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.1em;
  color: var(--vermilion);
  font-weight: 600;
}

.user__caret {
  font-size: 0.62rem;
  color: var(--text-3);
  transition: transform var(--dur-fast);
}

.user__trigger[aria-expanded='true'] .user__caret {
  transform: rotate(180deg);
}

.user-menu {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: 268px;
  padding: 8px;
  border-radius: 14px;
  border: 2px solid var(--ink);
  background: var(--paper-1);
  box-shadow: var(--shadow-float);
  z-index: 150;
}

.user-menu__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 10px 14px;
  margin-bottom: 6px;
  border-bottom: 1.5px dashed var(--line-strong);
}

.user-menu__name {
  font-size: 0.9rem;
  font-weight: 800;
  color: var(--ink);
}

.user-menu__sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.user-menu__points {
  font-family: var(--font-mono);
  font-size: 0.68rem;
  color: var(--text-3);
}

.user-menu__item {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--text-2);
  font-size: 0.86rem;
  font-weight: 600;
  text-align: left;
  transition: background var(--dur-fast), color var(--dur-fast);
}

.user-menu__item i:first-child {
  width: 18px;
  text-align: center;
  color: var(--text-3);
}

.user-menu__item:hover {
  background: var(--lime);
  color: var(--ink);
}

.user-menu__arrow {
  margin-left: auto;
  font-size: 0.7rem;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity var(--dur-fast), transform var(--dur-fast);
}

.user-menu__item:hover .user-menu__arrow {
  opacity: 0.8;
  transform: none;
}

.user-menu__foot {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1.5px dashed var(--line-strong);
}

.user-menu__logout {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  color: #b32d14;
  font-size: 0.84rem;
  font-weight: 700;
  transition: background var(--dur-fast);
}

.user-menu__logout:hover {
  background: rgba(237, 75, 42, 0.12);
}

.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.16s var(--ease-out), transform 0.16s var(--ease-out);
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.header__login {
  color: var(--text-2);
}

@media (max-width: 1120px) {
  .search-trigger__label,
  .search-trigger .search-trigger__kbd,
  .level-chip__title {
    display: none;
  }

  .search-trigger {
    width: 36px;
    justify-content: center;
    padding: 0;
  }
}

@media (max-width: 980px) {
  .brand__text span {
    display: none;
  }

  .user__meta {
    display: none;
  }

  .nav-link span {
    display: none;
  }

  .nav-link {
    width: 38px;
    justify-content: center;
    padding: 0;
  }
}

@media (max-width: 768px) {
  .header__inner {
    height: 58px;
    padding: 0 14px;
    gap: 8px;
  }

  .header__nav {
    display: none;
  }

  .brand__text strong {
    font-size: 0.9rem;
  }

  .header__register {
    display: none;
  }

  .header__login {
    height: 32px;
    padding: 0 10px;
  }

  .points-chip {
    height: 32px;
    padding: 0 9px;
    font-size: 0.74rem;
  }

  .level-chip {
    display: none;
  }

  .user__trigger {
    height: 38px;
    padding: 0 3px;
  }

  .user__caret {
    display: none;
  }

  .user-menu {
    position: fixed;
    top: 68px;
    right: 12px;
    width: min(300px, calc(100vw - 24px));
  }
}
</style>
