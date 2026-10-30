// src/router/index.js
import { reactive } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import AdminLayout from '@/layouts/AdminLayout.vue'

const ADMIN_SHELL_META = {
  requiresAuth: true,
  requiredRole: 'ADMINISTRATOR',
  adminShell: true
}

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue')
  },
  {
    path: '/category',
    name: 'Category',
    component: () => import('@/views/CategoryView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/blog/:id',
    name: 'ArticleDetail',
    component: () => import('@/views/ArticleDetailView.vue'),
    meta: { requiresAuth: true }
  },
  {
    // 管理端独立外壳：左侧导航 + 顶部管理栏 + 内容区，不再复用学员端布局。
    path: '/admin',
    component: AdminLayout,
    meta: ADMIN_SHELL_META,
    children: [
      {
        path: '',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/AdminDashboardView.vue'),
        meta: { adminTitle: '工作台' }
      },
      {
        path: 'learners',
        name: 'AdminLearners',
        component: () => import('@/views/admin/AdminLearnersView.vue'),
        meta: { adminTitle: '学员' }
      },
      {
        path: 'reviews',
        name: 'AdminReviews',
        component: () => import('@/views/admin/AdminReviewsView.vue'),
        meta: { adminTitle: '批阅' }
      },
      {
        path: 'content',
        name: 'AdminContent',
        component: () => import('@/views/admin/AdminContentView.vue'),
        meta: { adminTitle: '课件' }
      },
      {
        path: 'applications',
        name: 'AdminTeacherApplications',
        component: () => import('@/views/admin/AdminTeacherApplicationsView.vue'),
        meta: { adminTitle: '注册审核', requiredSuperAdmin: true }
      },
      {
        path: 'me',
        name: 'AdminProfile',
        component: () => import('@/views/admin/AdminProfileView.vue'),
        meta: { adminTitle: '我的' }
      }
    ]
  },
  {
    path: '/tasks',
    name: 'Tasks',
    component: () => import('@/views/TasksView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/wrong-questions',
    name: 'WrongQuestions',
    component: () => import('@/views/WrongQuestionsView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/wrong-training',
    name: 'WrongTraining',
    component: () => import('@/views/WrongTrainingView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/settings/password',
    name: 'PasswordChange',
    component: () => import('@/views/PasswordChangeView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/chemistry',
    name: 'Chemistry',
    redirect: '/category?subject=化学'
  },
  {
    path: '/english',
    name: 'English',
    redirect: '/category?subject=英语'
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 96, behavior: 'smooth' }
    return { top: 0 }
  }
})

/** 管理员进入这些学员端入口时统一回到管理工作台。 */
const ADMIN_REDIRECT_PATHS = new Set([
  '/',
  '/category',
  '/tasks',
  '/wrong-questions',
  '/wrong-training'
])

/** 路由加载/异常状态：用于顶部进度条与整页兜底，避免异步 chunk 失败时白屏 */
export const routeState = reactive({ loading: false, error: null })

let loadingTimer = null
const CHUNK_RELOAD_KEY = 'nest:chunk-reload'

function beginLoading() {
  window.clearTimeout(loadingTimer)
  loadingTimer = window.setTimeout(() => {
    routeState.loading = true
  }, 160)
}

function endLoading() {
  window.clearTimeout(loadingTimer)
  routeState.loading = false
}

router.beforeEach(async (to) => {
  beginLoading()
  const authStore = useAuthStore()
  await authStore.ensureInitialized()

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { name: 'Home', query: { redirect: to.fullPath } }
  }
  const requiresAdministrator = to.meta.requiredRole === 'ADMINISTRATOR'
    || (to.name === 'ArticleDetail' && ['answer', 'review'].includes(to.query.mode))
  if (requiresAdministrator && !authStore.isAdministrator) {
    return { name: 'Home' }
  }
  // 注册审核仅超级管理员可见；普通管理员误入时回到工作台。
  if (to.meta.requiredSuperAdmin && !authStore.isSuperAdministrator) {
    return { name: 'AdminDashboard' }
  }
  // 管理员不进入学员端页面（首页 / 学科 / 任务 / 错题），统一回管理工作台。
  if (authStore.isAdministrator && ADMIN_REDIRECT_PATHS.has(to.path)) {
    return { name: 'AdminDashboard' }
  }
  return true
})

router.afterEach(() => {
  endLoading()
  try {
    window.sessionStorage.removeItem(CHUNK_RELOAD_KEY)
  } catch { /* 忽略隐私模式 */ }
})

router.onError((error) => {
  endLoading()
  const message = String(error?.message || error)
  const chunkFailed = /Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk|ChunkLoadError/i.test(message)
  if (chunkFailed) {
    // 开发服务器重启 / 发布后旧 chunk 失效：自动刷新一次；仍失败则展示兜底页
    try {
      if (!window.sessionStorage.getItem(CHUNK_RELOAD_KEY)) {
        window.sessionStorage.setItem(CHUNK_RELOAD_KEY, '1')
        window.location.reload()
        return
      }
    } catch { /* 忽略隐私模式 */ }
  }
  routeState.error = error
})

export function clearRouteError() {
  routeState.error = null
}

export default router
