// src/router/index.js
import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

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
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { requiresAuth: true, requiredRole: 'ADMINISTRATOR' }
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
  routes
})

// 路由守卫
router.beforeEach(async (to) => {
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
  return true
})

export default router
