<!-- src/components/common/AppHeader.vue -->
<template>
    <nav class="navbar">
        <div class="nav-inner">
            <!-- 左侧 -->
            <div class="nav-left">
                <router-link v-if="showBackButton" :to="backLink" class="nav-logo">
                <i class="fas fa-arrow-left"></i> {{ backText }}
            </router-link>
                <span v-else class="nav-logo">学习资料仓库</span>
            </div>

            <!-- 中间 -->
            <div v-if="showCenterInfo" class="nav-center">
                <span class="nav-current-subject">{{ centerTitle }}</span>
                <span v-if="centerSubtitle" class="nav-post-count">{{ centerSubtitle }}</span>
            </div>

            <!-- 右侧 -->
            <div class="nav-right">
                <!-- 学科切换按钮 -->
                <div v-if="showSubjectSwitcher" class="nav-subject-switcher">
                    <router-link v-for="subject in availableSubjects" :key="subject"
                        :to="`/category?subject=${encodeURIComponent(subject)}`"
                        :class="['nav-subject-btn', { active: subject === currentSubject }]">
                        {{ subject }}
                    </router-link>
                </div>

                <span class="nav-user-status">
                    {{ authStore.isLoggedIn ? authStore.username : '未登录' }}
                </span>
                <button v-if="!authStore.isLoggedIn" class="nav-btn nav-btn-login" @click="$emit('login-click')">
                    登录
                </button>
                <button v-else class="nav-btn nav-btn-logout" @click="handleLogout">
                    退出
                </button>
            </div>
        </div>
    </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const emit = defineEmits(['login-click'])

// 计算属性
const showBackButton = computed(() => {
    return route.name !== 'Home'
})

const showCenterInfo = computed(() => {
    return route.name === 'Category' || route.name === 'BlogDetail'
})

const centerTitle = computed(() => {
    if (route.name === 'Category') {
        return route.query.subject || '科目'
    }
    if (route.name === 'BlogDetail') {
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

function handleLogout() {
    authStore.logout()
    router.push('/')
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
    gap: 16px;
}

.nav-user-status {
    font-size: 0.9rem;
    color: var(--gray);
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

.nav-btn-logout {
    color: var(--gray);
    border-color: rgba(120, 170, 155, 0.15);
}

.nav-btn-logout:hover {
    background: rgba(213, 117, 135, 0.08);
    border-color: rgba(213, 117, 135, 0.2);
    color: #d57587;
}

.nav-subject-switcher {
    display: flex;
    gap: 8px;
    margin-right: 8px;
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

@media (max-width: 640px) {
    .navbar {
        padding: 8px 16px;
    }

    .nav-logo {
        font-size: 1rem;
    }

    .nav-user-status {
        font-size: 0.75rem;
    }

    .nav-btn {
        font-size: 0.8rem;
        padding: 4px 12px;
    }
}
</style>