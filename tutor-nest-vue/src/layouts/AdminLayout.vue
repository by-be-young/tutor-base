<!-- src/layouts/AdminLayout.vue -->
<template>
  <div class="admin-shell">
    <!-- 桌面：左侧固定导航 -->
    <aside class="admin-rail" aria-label="管理后台侧栏">
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
          <span class="admin-nav__icon"><i :class="item.icon" aria-hidden="true"></i></span>
          <span class="admin-nav__label">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="admin-rail__foot">
        <span class="admin-rail__stamp" aria-hidden="true">RISO · ADMIN</span>
      </div>
    </aside>

    <div class="admin-shell__body">
      <!-- 顶部管理栏 -->
      <header class="admin-bar">
        <div class="admin-bar__left">
          <span class="admin-bar__nest">NEST · 学巢</span>
          <span class="admin-bar__divider" aria-hidden="true"></span>
          <h1 class="admin-bar__title">{{ pageTitle }}</h1>
        </div>

        <div ref="userMenuRef" class="admin-bar__user">
          <button type="button" class="admin-bar__trigger" :aria-expanded="userMenuOpen" aria-haspopup="menu"
            @click.stop="userMenuOpen = !userMenuOpen">
            <span class="avatar admin-bar__avatar" aria-hidden="true">{{ userInitial }}</span>
            <span class="admin-bar__usertext">
              <strong>{{ authStore.username }}</strong>
              <small>管理员</small>
            </span>
            <i class="fas fa-chevron-down admin-bar__caret" aria-hidden="true"></i>
          </button>

          <transition name="dropdown">
            <div v-if="userMenuOpen" class="admin-menu" role="menu">
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
      </header>

      <!-- 页面内容 -->
      <div class="admin-content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </div>
    </div>

    <!-- 移动端：底部固定 5 项导航 -->
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
import { ensureArticleData } from '@/composables/useAdminWorkspace'
import { showToast } from '@/utils/toast'
import BrandMark from '@/components/common/BrandMark.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const adminStore = useAdminStore()
const blogStore = useArticleStore()
const coursewareStore = useCoursewareStore()

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
})
</script>

<style scoped>
.admin-shell {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  gap: 0;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 22px 28px 64px;
  min-height: calc(100vh - var(--header-h));
}

/* ------------------------------------------------------------------ */
/* 左侧固定导航                                                        */
/* ------------------------------------------------------------------ */
.admin-rail {
  position: sticky;
  top: calc(var(--header-h) + 22px);
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 18px 16px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-lg);
  background: var(--paper-1);
  box-shadow: var(--shadow-card);
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  border-bottom: 1.5px dashed var(--line-strong);
}

.admin-brand__text {
  display: flex;
  align-items: baseline;
  gap: 7px;
  line-height: 1;
}

.admin-brand__text strong {
  font-family: var(--font-display);
  font-size: 0.96rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--ink);
}

.admin-brand__text span {
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.1em;
  color: var(--text-3);
}

.admin-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.admin-nav__link {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  border: 1.5px solid transparent;
  color: var(--text-2);
  font-size: 0.88rem;
  font-weight: 700;
  transition: background var(--dur), border-color var(--dur), color var(--dur), box-shadow var(--dur);
}

.admin-nav__link:hover {
  background: var(--paper-2);
  border-color: var(--ink);
  color: var(--ink);
}

.admin-nav__link.is-active {
  background: var(--ink);
  border-color: var(--ink);
  color: var(--paper-1);
  box-shadow: 3px 3px 0 rgba(28, 26, 20, 0.28);
}

.admin-nav__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  border: 1.5px solid var(--ink);
  background: var(--paper-2);
  color: var(--ink);
  font-size: 0.84rem;
  flex-shrink: 0;
}

.admin-nav__link.is-active .admin-nav__icon {
  background: var(--butter);
  color: var(--ink);
}

.admin-nav__label {
  flex: 1;
  min-width: 0;
}

.admin-rail__foot {
  margin-top: auto;
  padding-top: 14px;
  border-top: 1.5px solid var(--ink);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-rail__exit {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-2);
  font-size: 0.78rem;
  font-weight: 700;
}

.admin-rail__exit:hover {
  color: var(--ink);
  text-decoration: underline;
  text-decoration-thickness: 2px;
  text-underline-offset: 4px;
}

.admin-rail__stamp {
  align-self: flex-start;
  padding: 2px 7px;
  border: 1.5px dashed var(--ink);
  color: var(--vermilion);
  font-family: var(--font-mono);
  font-size: 0.54rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  transform: rotate(-2deg);
}

/* ------------------------------------------------------------------ */
/* 右侧：顶部管理栏 + 内容                                             */
/* ------------------------------------------------------------------ */
.admin-shell__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-left: 22px;
}

.admin-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 18px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-lg);
  background: var(--paper-1);
  box-shadow: var(--shadow-card);
}

.admin-bar__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.admin-bar__nest {
  font-family: var(--font-display);
  font-size: 0.84rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  color: var(--ink);
  white-space: nowrap;
}

.admin-bar__divider {
  width: 1.5px;
  height: 20px;
  background: var(--ink);
  opacity: 0.5;
  flex-shrink: 0;
}

.admin-bar__title {
  font-size: 1.12rem;
  letter-spacing: -0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.admin-bar__user {
  position: relative;
  flex-shrink: 0;
}

.admin-bar__trigger {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  height: 42px;
  padding: 0 8px 0 4px;
  border-radius: 10px;
  border: 1.5px solid transparent;
  transition: background var(--dur-fast), border-color var(--dur-fast);
}

.admin-bar__trigger:hover,
.admin-bar__trigger[aria-expanded='true'] {
  background: var(--paper-2);
  border-color: var(--ink);
}

.admin-bar__avatar {
  width: 32px;
  height: 32px;
  font-size: 0.8rem;
}

.admin-bar__usertext {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.15;
  max-width: 130px;
}

.admin-bar__usertext strong {
  font-size: 0.82rem;
  color: var(--ink);
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-bar__usertext small {
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.1em;
  color: var(--vermilion);
  font-weight: 600;
}

.admin-bar__caret {
  font-size: 0.62rem;
  color: var(--text-3);
  transition: transform var(--dur-fast);
}

.admin-bar__trigger[aria-expanded='true'] .admin-bar__caret {
  transform: rotate(180deg);
}

.admin-menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 150;
  width: 216px;
  padding: 8px;
  border-radius: 14px;
  border: 2px solid var(--ink);
  background: var(--paper-1);
  box-shadow: var(--shadow-float);
}

.admin-menu__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--text-2);
  font-size: 0.86rem;
  font-weight: 700;
  text-align: left;
  transition: background var(--dur-fast), color var(--dur-fast);
}

.admin-menu__item i:first-child {
  width: 18px;
  text-align: center;
  color: var(--text-3);
}

.admin-menu__item:hover {
  background: var(--lime);
  color: var(--ink);
}

.admin-menu__item--danger {
  margin-top: 4px;
  border-top: 1.5px dashed var(--line-strong);
  border-radius: 0 0 8px 8px;
  color: #b32d14;
}

.admin-menu__item--danger:hover {
  background: rgba(237, 75, 42, 0.14);
  color: #b32d14;
}

.admin-content {
  min-width: 0;
  flex: 1;
}

/* ------------------------------------------------------------------ */
/* 移动端底部导航                                                      */
/* ------------------------------------------------------------------ */
.admin-dock {
  display: none;
}

/* ------------------------------------------------------------------ */
/* 下拉过渡                                                            */
/* ------------------------------------------------------------------ */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.16s var(--ease-out), transform 0.16s var(--ease-out);
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* ------------------------------------------------------------------ */
/* 响应式                                                              */
/* ------------------------------------------------------------------ */
@media (max-width: 1024px) {
  .admin-shell {
    padding: 18px 20px 56px;
  }

  .admin-shell__body {
    padding-left: 16px;
  }
}

@media (max-width: 768px) {
  .admin-shell {
    display: block;
    padding: 12px 14px calc(var(--dock-h) + 34px + env(safe-area-inset-bottom));
  }

  .admin-rail {
    display: none;
  }

  .admin-shell__body {
    padding-left: 0;
    gap: 14px;
  }

  .admin-bar {
    padding: 10px 12px;
  }

  .admin-bar__nest,
  .admin-bar__divider {
    display: none;
  }

  .admin-bar__title {
    font-size: 1rem;
  }

  .admin-bar__usertext small {
    display: none;
  }

  .admin-bar__trigger {
    height: 38px;
    padding: 0 4px;
  }

  .admin-bar__caret {
    display: none;
  }

  .admin-dock {
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

  .admin-dock__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
    flex: 1;
    height: 48px;
    border-radius: 10px;
    color: var(--text-3);
    font-size: 0.6rem;
    font-weight: 700;
    transition: color var(--dur-fast), background var(--dur-fast);
  }

  .admin-dock__item i {
    font-size: 1rem;
  }

  .admin-dock__item.is-active {
    color: var(--ink);
    background: var(--lime);
  }
}
</style>
