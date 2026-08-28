<!-- src/components/common/AppHeader.vue -->
<template>
    <nav class="navbar">
        <div class="nav-inner">
            <!-- 左侧 -->
            <div class="nav-left">
                <a v-if="showBackButton" href="#" class="nav-logo" @click.prevent="handleBack">
                <i class="fas fa-arrow-left"></i> {{ backText }}
            </a>
                <span v-else class="nav-logo">学习资料仓库</span>
            </div>

            <!-- 中间：标题 + 学科切换器 -->
            <div v-if="showCenterInfo" class="nav-center">
                <span class="nav-current-subject">{{ centerTitle }}</span>
                <div v-if="showSubjectSwitcher" class="nav-subject-switcher">
                    <router-link v-for="subject in availableSubjects" :key="subject"
                        :to="`/category?subject=${encodeURIComponent(subject)}`"
                        :class="['nav-subject-btn', { active: subject === currentSubject }]">
                        {{ subject }}
                    </router-link>
                </div>
            </div>

            <!-- 右侧 -->
            <div class="nav-right">
                <!-- 导航链接组 -->
                <div class="nav-links">
                    <router-link v-if="authStore.isLoggedIn" to="/tasks" class="nav-btn nav-btn-tasks"
                        :class="{ 'is-active': route.name === 'Tasks' }">
                        <i class="fas fa-list-check"></i> <span>任务</span>
                    </router-link>
                    <router-link v-if="authStore.isLoggedIn" to="/wrong-questions" class="nav-btn nav-btn-wrong-questions"
                        :class="{ 'is-active': route.name === 'WrongQuestions' }">
                        <i class="fas fa-book-medical"></i> <span>错题本</span>
                    </router-link>
                    <router-link v-if="authStore.isAdministrator" to="/admin" class="nav-btn nav-btn-admin">
                        <i class="fas fa-shield-halved"></i> <span>管理</span>
                    </router-link>
                </div>

                <!-- 用户区 -->
                <div v-if="authStore.isLoggedIn" class="nav-user" ref="userMenuRef">
                    <button class="nav-user-trigger" @click="userMenuOpen = !userMenuOpen"
                        :aria-expanded="userMenuOpen">
                        <i class="fas fa-user-circle"></i>
                        <span class="nav-user-name">{{ authStore.username }}</span>
                        <i class="fas fa-chevron-down nav-user-caret"></i>
                    </button>
                    <div v-if="userMenuOpen" class="nav-user-menu">
                        <router-link to="/settings/password" class="nav-user-item" @click="userMenuOpen = false">
                            <i class="fas fa-key"></i> 修改密码
                        </router-link>
                        <button type="button" class="nav-user-item nav-user-item-logout" @click="handleLogout">
                            <i class="fas fa-sign-out-alt"></i> 退出登录
                        </button>
                    </div>
                </div>
                <template v-else>
                    <button class="nav-btn nav-btn-login" @click="$emit('login-click')">登录</button>
                    <button class="nav-btn nav-btn-register" @click="$emit('register-click')">注册</button>
                </template>
            </div>
        </div>
    </nav>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const emit = defineEmits(['login-click', 'register-click'])

// 用户下拉菜单
const userMenuRef = ref(null)
const userMenuOpen = ref(false)

function closeUserMenu() {
  userMenuOpen.value = false
}

function onDocumentClick(event) {
  if (userMenuRef.value && !userMenuRef.value.contains(event.target)) {
    closeUserMenu()
  }
}

function onKeydown(event) {
  if (event.key === 'Escape') {
    closeUserMenu()
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})

// 计算属性
const showBackButton = computed(() => {
    return route.name !== 'Home'
})

const showCenterInfo = computed(() => {
    return route.name === 'Category' || route.name === 'ArticleDetail'
})

const centerTitle = computed(() => {
    if (route.name === 'Category') {
        return route.query.subject || '科目'
    }
    if (route.name === 'ArticleDetail') {
        return '文章详情'
    }
    return ''
})

const centerSubtitle = computed(() => {
    if (route.name === 'Category') {
        return '' // 文章数量由 CategoryView 管理
    }
    return ''
})

const showSubjectSwitcher = computed(() => {
    return route.name === 'Category' && authStore.isLoggedIn
})

const currentSubject = computed(() => {
    return route.query.subject || ''
})

const availableSubjects = computed(() => {
    // 这里可以从 store 获取所有可用学科
    return ['化学', '英语']
})

const backLink = computed(() => {
    return route.query.from === 'admin' ? '/admin' : '/'
})

const backText = computed(() => {
    return route.query.from === 'admin' ? '管理员' : '学习资料仓库'
})

function handleBack() {
    if (window.history.state?.back) {
        router.back()
    } else {
        router.push(backLink.value)
    }
}

async function handleLogout() {
    closeUserMenu()
    try {
        await authStore.logout()
    } finally {
        await router.push('/')
    }
}
</script>

<style scoped>
.navbar {
    background: rgba(255, 255, 255, 0.3);
    backdrop-filter: blur(12px);
    border-bottom: 1px solid rgba(255, 255, 255, 0.15);
    padding: 12px 24px;
    position: sticky;
    top: 0;
    z-index: 100;
    flex-shrink: 0;
}

.nav-inner {
    max-width: 1200px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.nav-left {
    display: flex;
    align-items: center;
}

.nav-logo {
    font-size: 1.25rem;
    font-weight: 700;
    color: #2d4a3a;
    letter-spacing: 1px;
    text-decoration: none;
    display: flex;
    align-items: center;
    gap: 8px;
}

.nav-center {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;
    justify-content: center;
}

.nav-current-subject {
    font-size: 1.1rem;
    font-weight: 600;
    color: #2d4a3a;
}

.nav-post-count {
    font-size: 0.9rem;
    color: var(--gray);
    background: rgba(120, 170, 155, 0.12);
    padding: 0 14px;
    border-radius: 30px;
}

.nav-right {
    display: flex;
    align-items: center;
    gap: 10px;
}

.nav-links {
    display: flex;
    align-items: center;
    gap: 8px;
}

.nav-btn {
    padding: 6px 18px;
    border-radius: 30px;
    border: 1px solid transparent;
    font-size: 0.9rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.25s ease;
    background: transparent;
}

.nav-btn-login {
    background: rgba(123, 200, 196, 0.25);
    color: #2d4a3a;
    border-color: rgba(123, 200, 196, 0.15);
}

.nav-btn-login:hover {
    background: rgba(91, 168, 164, 0.35);
    transform: translateY(-1px);
}

.nav-btn-register {
    color: #2d4a3a;
    border-color: rgba(120, 170, 155, 0.3);
}

.nav-btn-register:hover {
    background: rgba(255, 255, 255, 0.35);
    transform: translateY(-1px);
}

.nav-btn-admin {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: rgba(91, 168, 164, 0.12);
    color: #2d6a5e;
    border-color: rgba(91, 168, 164, 0.22);
}

.nav-btn-admin:hover {
    background: rgba(91, 168, 164, 0.24);
    transform: translateY(-1px);
    color: #1f5c52;
}

.nav-btn-wrong-questions {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: rgba(217, 186, 75, 0.12);
    color: #8a6d1a;
    border-color: rgba(217, 186, 75, 0.2);
}

.nav-btn-wrong-questions:hover {
    background: rgba(217, 186, 75, 0.22);
    transform: translateY(-1px);
    color: #705d13;
}

.nav-btn-wrong-questions.is-active {
    background: rgba(217, 186, 75, 0.28);
    border-color: rgba(217, 186, 75, 0.4);
    font-weight: 600;
}

.nav-btn-tasks {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: rgba(151, 130, 200, 0.14);
    color: #5d4a8a;
    border-color: rgba(151, 130, 200, 0.22);
}

.nav-btn-tasks:hover {
    background: rgba(151, 130, 200, 0.26);
    transform: translateY(-1px);
    color: #4a3a72;
}

.nav-btn-tasks.is-active {
    background: rgba(151, 130, 200, 0.3);
    border-color: rgba(151, 130, 200, 0.45);
    font-weight: 600;
}

.nav-subject-switcher {
    display: flex;
    gap: 8px;
}

.nav-subject-btn {
    padding: 4px 16px;
    border-radius: 30px;
    background: rgba(255, 255, 255, 0.15);
    color: var(--gray);
    font-size: 0.85rem;
    border: 1px solid transparent;
    transition: background 0.2s, color 0.2s, border-color 0.2s;
    cursor: pointer;
    text-decoration: none;
}

.nav-subject-btn:hover {
    background: rgba(255, 255, 255, 0.3);
    color: #2d4a3a;
}

.nav-subject-btn.active {
    background: rgba(91, 168, 164, 0.2);
    color: var(--teal-dark);
    border-color: var(--teal-dark);
    font-weight: 600;
}

/* 用户下拉菜单 */
.nav-user {
    position: relative;
}

.nav-user-trigger {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    border-radius: 30px;
    border: 1px solid rgba(120, 170, 155, 0.2);
    background: rgba(255, 255, 255, 0.25);
    color: #2d4a3a;
    font-size: 0.9rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.25s ease;
    font-family: inherit;
    max-width: 180px;
}

.nav-user-trigger:hover {
    background: rgba(255, 255, 255, 0.45);
}

.nav-user-trigger > i:first-child {
    font-size: 1.05rem;
    color: var(--teal-dark);
}

.nav-user-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.nav-user-caret {
    font-size: 0.7rem;
    color: var(--gray);
    transition: transform 0.2s;
}

.nav-user-trigger[aria-expanded="true"] .nav-user-caret {
    transform: rotate(180deg);
}

.nav-user-menu {
    position: absolute;
    top: calc(100% + 8px);
    right: 0;
    min-width: 150px;
    background: rgba(255, 255, 255, 0.96);
    backdrop-filter: blur(12px);
    border: 1px solid rgba(120, 170, 155, 0.2);
    border-radius: 14px;
    box-shadow: 0 12px 32px rgba(80, 130, 120, 0.16);
    padding: 6px;
    z-index: 120;
    animation: menuIn 0.18s ease;
}

@keyframes menuIn {
    from {
        opacity: 0;
        transform: translateY(-6px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.nav-user-item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 9px 12px;
    border: none;
    background: none;
    border-radius: 10px;
    color: #2d4a3a;
    font-size: 0.9rem;
    cursor: pointer;
    transition: background 0.2s;
    font-family: inherit;
    text-align: left;
}

.nav-user-item i {
    width: 16px;
    text-align: center;
    color: var(--gray);
}

.nav-user-item:hover {
    background: rgba(91, 168, 164, 0.12);
}

.nav-user-item-logout {
    color: #d57587;
}

.nav-user-item-logout i {
    color: #d57587;
}

.nav-user-item-logout:hover {
    background: rgba(213, 117, 135, 0.1);
}

@media (max-width: 640px) {
    .navbar {
        padding: 8px 12px;
    }

    .nav-logo {
        font-size: 1rem;
    }

    .nav-right {
        gap: 6px;
    }

    .nav-links {
        gap: 4px;
    }

    /* 窄屏下链接组只保留图标 */
    .nav-btn span,
    .nav-user-name,
    .nav-user-caret {
        display: none;
    }

    .nav-btn {
        font-size: 0.85rem;
        padding: 6px 10px;
    }

    .nav-btn-login,
    .nav-btn-register {
        padding: 5px 12px;
        font-size: 0.8rem;
    }

    .nav-user-trigger {
        padding: 6px 10px;
    }

    .nav-user-menu {
        right: -4px;
    }
}
</style>
