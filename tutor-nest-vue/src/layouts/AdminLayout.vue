<!-- src/layouts/AdminLayout.vue -->
<template>
  <div class="admin-shell">
    <!-- 顶部导航：与学员端 AppHeader 保持同一套视觉语言 -->
    <header class="admin-header" :class="{ 'is-scrolled': scrolled }">
      <div class="admin-header__inner">
        <router-link to="/admin" class="admin-brand" aria-label="NEST 管理台工作台">
          <BrandMark :size="30" />
          <span class="admin-brand__text">
            <strong>NEST</strong>
            <span>管理台</span>
          </span>
        </router-link>

        <nav class="admin-nav" aria-label="管理后台导航">
          <router-link v-for="item in navItems" :key="item.to" :to="item.to" class="admin-nav__link"
            :class="{ 'is-active': isActive(item) }">
            <i :class="item.icon" aria-hidden="true"></i>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <div class="admin-header__actions">
          <button type="button" class="admin-search" aria-label="搜索与跳转" @click="uiStore.openPalette()">
            <i class="fas fa-magnifying-glass" aria-hidden="true"></i>
            <span class="admin-search__label">搜索</span>
            <kbd class="kbd admin-search__kbd">⌘K</kbd>
          </button>

          <div ref="userMenuRef" class="admin-user">
            <button type="button" class="admin-user__trigger" :aria-expanded="userMenuOpen" aria-haspopup="menu"
              @click.stop="userMenuOpen = !userMenuOpen">
              <span class="avatar admin-user__avatar">{{ userInitial }}</span>
              <span class="admin-user__meta">
                <span class="admin-user__name">{{ authStore.username }}</span>
                <span class="admin-user__role">管理员</span>
              </span>
              <i class="fas fa-chevron-down admin-user__caret" aria-hidden="true"></i>
            </button>

            <transition name="dropdown">
              <div v-if="userMenuOpen" class="admin-menu" role="menu">
                <div class="admin-menu__head">
                  <span class="avatar avatar--lg">{{ userInitial }}</span>
                  <div>
                    <p class="admin-menu__name">{{ authStore.username }}</p>
                    <p class="admin-menu__sub">管理员 · NEST 管理台</p>
                  </div>
                </div>
                <router-link to="/settings/password" class="admin-menu__item" role="menuitem"
                  @click="userMenuOpen = false">
                  <i class="fas fa-key" aria-hidden="true"></i>
                  <span>修改密码</span>
                </router-link>
                <button type="button" class="admin-menu__item admin-menu__item--danger" role="menuitem"
                  @click="handleLogout">
                  <i class="fas fa-arrow-right-from-bracket" aria-hidden="true"></i>
                  <span>退出登录</span>
                </button>
              </div>
            </transition>
          </div>
        </div>
      </div>
    </header>

    <!-- 页面标题：沿用学员端 page-head 的位置与字号 -->
    <div class="admin-pagehead">
      <h1 class="admin-pagehead__title">{{ pageTitle }}</h1>
      <p class="admin-pagehead__sub">学员管理与我的课件</p>
    </div>

    <div class="admin-content">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" :key="route.path" />
        </transition>
      </router-view>
    </div>

    <!-- 移动端：底部固定 5 项导航，与学员端 MobileDock 同款 -->
    <nav class="admin-dock" aria-label="管理端移动导航">
      <router-link v-for="item in navItems" :key="item.to" :to="item.to" class="admin-dock__item"
        :class="{ 'is-active': isActive(item) }">
        <i :class="item.icon" aria-hidden="true"></i>
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useAdminStore } from '@/stores/adminStore'
import { useArticleStore } from '@/stores/blogStore'
import { useCoursewareStore } from '@/stores/coursewareStore'
import { useUiStore } from '@/stores/uiStore'
import { ensureArticleData } from '@/composables/useAdminWorkspace'
import { showToast } from '@/utils/toast'
import BrandMark from '@/components/common/BrandMark.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const adminStore = useAdminStore()
const blogStore = useArticleStore()
const coursewareStore = useCoursewareStore()
const uiStore = useUiStore()

const scrolled = ref(false)
const userMenuOpen = ref(false)
const userMenuRef = ref(null)

const navItems = [
  { to: '/admin', label: '工作台', icon: 'fas fa-chart-line', exact: true },
  { to: '/admin/learners', label: '学员', icon: 'fas fa-users' },
  { to: '/admin/reviews', label: '批阅', icon: 'fas fa-pen-nib' },
  { to: '/admin/content', label: '课件', icon: 'fas fa-book-open' },
  { to: '/admin/me', label: '我的', icon: 'fas fa-user-gear' }
]

const pageTitle = computed(() => route.meta.adminTitle || '工作台')
const userInitial = computed(() => (authStore.username || 'A').slice(0, 1).toUpperCase())

function isActive(item) {
  if (item.exact) return route.path === item.to
  return route.path === item.to || route.path.startsWith(`${item.to}/`)
}

function onScroll() {
  scrolled.value = window.scrollY > 4
}

async function handleLogout() {
  userMenuOpen.value = false
  try {
    await authStore.logout()
  } finally {
    await router.push('/')
  }
}

function onDocumentClick(event) {
  if (userMenuRef.value && !userMenuRef.value.contains(event.target)) {
    userMenuOpen.value = false
  }
}

function onKeydown(event) {
  if (event.key === 'Escape') userMenuOpen.value = false
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
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()

  // 课件目录依赖文章目录；失败不阻塞管理页渲染，只提示。
  try {
    await ensureArticleData(blogStore)
    await coursewareStore.loadWorkspace(blogStore.blogData)
  } catch (error) {
    showToast(error?.message || '课件目录加载失败，部分课件功能暂不可用', 'error')
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
/* ---------------------------------------------------------------- */
/* 顶部导航：沿用学员端 AppHeader 的尺寸、配色与激活态                 */
/* ---------------------------------------------------------------- */
.admin-header {
  position: sticky;
  top: 0;
  z-index: 120;
  background: rgba(243, 236, 221, 0.94);
  border-bottom: 1.5px solid var(--ink);
  transition: box-shadow 0.2s var(--ease-out);
}

.admin-header.is-scrolled {
  box-shadow: 0 4px 0 rgba(28, 26, 20, 0.08);
}

.admin-header__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 1320px;
  margin: 0 auto;
  height: 66px;
  padding: 0 24px;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.admin-brand__text {
  display: flex;
  align-items: baseline;
  gap: 7px;
  line-height: 1;
}

.admin-brand__text strong {
  font-family: var(--font-display);
  font-size: 1.02rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--ink);
}

.admin-brand__text span {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  color: var(--text-3);
  letter-spacing: 0.1em;
}

.admin-nav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.admin-nav__link {
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
  white-space: nowrap;
  transition: background var(--dur-fast), color var(--dur-fast), box-shadow var(--dur-fast);
}

.admin-nav__link:hover {
  color: var(--ink);
  background: var(--paper-2);
}

.admin-nav__link.is-active {
  color: var(--ink);
  background: var(--lime);
  border-color: var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
}

.admin-nav__link i {
  font-size: 0.82rem;
}

.admin-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.admin-search {
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

.admin-search:hover {
  background: var(--paper-2);
  box-shadow: 2px 2px 0 var(--ink);
}

.admin-search__kbd {
  pointer-events: none;
}

/* 用户菜单 */
.admin-user {
  position: relative;
}

.admin-user__trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 10px;
  border-radius: var(--r-full);
  border: 1.5px solid transparent;
  transition: background var(--dur-fast), border-color var(--dur-fast);
}

.admin-user__trigger:hover,
.admin-user__trigger[aria-expanded="true"] {
  background: var(--paper-2);
  border-color: var(--ink);
}

.admin-user__avatar {
  width: 30px;
  height: 30px;
  font-size: 0.8rem;
}

.admin-user__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.15;
}

.admin-user__name {
  font-size: 0.82rem;
  font-weight: 800;
  color: var(--ink);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-user__role {
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--text-3);
  letter-spacing: 0.08em;
}

.admin-user__caret {
  font-size: 0.66rem;
  color: var(--text-3);
}

.admin-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 10px);
  width: 236px;
  padding: 8px;
  border-radius: var(--r-lg);
  border: 1.5px solid var(--ink);
  background: var(--paper-0);
  box-shadow: 4px 4px 0 var(--ink);
  z-index: 160;
}

.admin-menu__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 12px;
  border-bottom: 1.5px dashed var(--line-strong);
  margin-bottom: 6px;
}

.admin-menu__name {
  font-size: 0.86rem;
  font-weight: 800;
  color: var(--ink);
}

.admin-menu__sub {
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--text-3);
}

.admin-menu__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border-radius: 8px;
  color: var(--text-2);
  font-size: 0.82rem;
  font-weight: 600;
  text-align: left;
  transition: background var(--dur-fast), color var(--dur-fast);
}

.admin-menu__item:hover {
  background: var(--paper-2);
  color: var(--ink);
}

.admin-menu__item--danger {
  color: var(--vermilion);
}

.admin-menu__item--danger:hover {
  background: rgba(214, 63, 43, 0.12);
  color: var(--vermilion);
}

/* ---------------------------------------------------------------- */
/* 页面标题 + 内容                                                    */
/* ---------------------------------------------------------------- */
.admin-pagehead {
  max-width: 1320px;
  margin: 0 auto;
  padding: 28px 24px 6px;
}

.admin-pagehead__title {
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 2.4vw, 1.9rem);
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--ink);
}

.admin-pagehead__sub {
  margin-top: 4px;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  letter-spacing: 0.1em;
  color: var(--text-3);
}

.admin-content {
  max-width: 1320px;
  margin: 0 auto;
  padding: 8px 24px 72px;
  min-height: calc(100vh - var(--header-h) - 220px);
}

/* ---------------------------------------------------------------- */
/* 移动端底部导航                                                     */
/* ---------------------------------------------------------------- */
.admin-dock {
  display: none;
}

@media (max-width: 1100px) {
  .admin-nav {
    gap: 2px;
  }

  .admin-nav__link {
    padding: 0 9px;
    font-size: 0.8rem;
  }

  .admin-search__label {
    display: none;
  }

  .admin-search__kbd {
    display: none;
  }
}

@media (max-width: 900px) {
  .admin-nav {
    display: none;
  }

  .admin-header__inner {
    height: 60px;
    padding: 0 16px;
  }

  .admin-user__meta {
    display: none;
  }

  .admin-pagehead {
    padding: 20px 16px 2px;
  }

  .admin-content {
    padding: 8px 16px calc(96px + env(safe-area-inset-bottom));
  }

  .admin-dock {
    position: fixed;
    left: 10px;
    right: 10px;
    bottom: calc(10px + env(safe-area-inset-bottom));
    z-index: 130;
    display: flex;
    align-items: stretch;
    justify-content: space-between;
    gap: 2px;
    padding: 6px;
    border-radius: var(--r-full);
    border: 1.5px solid var(--ink);
    background: rgba(255, 253, 246, 0.96);
    box-shadow: 4px 4px 0 var(--ink);
    backdrop-filter: blur(8px);
  }

  .admin-dock__item {
    flex: 1 1 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
    min-width: 0;
    padding: 6px 2px 4px;
    border-radius: var(--r-full);
    color: var(--text-3);
    font-size: 0.62rem;
    font-weight: 700;
  }

  .admin-dock__item i {
    font-size: 1rem;
  }

  .admin-dock__item.is-active {
    color: var(--ink);
    background: var(--lime);
    border: 1px solid var(--ink);
  }
}
</style>
