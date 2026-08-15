// src/router/index.js
import { createRouter, createWebHashHistory } from 'vue-router'

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
    meta: { requiresAuth: true }
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
router.beforeEach((to, from, next) => {
  // 这里可以添加认证检查
  // const authStore = useAuthStore()
  // if (to.meta.requiresAuth && !authStore.isLoggedIn) {
  //   next('/')
  // } else {
  //   next()
  // }
  next()
})

export default router