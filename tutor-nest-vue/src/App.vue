<!-- src/App.vue -->
<template>
  <a class="skip-link" href="#main">跳到主要内容</a>

  <PaperBackdrop />

  <AppHeader />

  <main id="main" class="app-main">
    <div v-if="routeState.loading" class="route-progress" role="status" aria-label="页面加载中">
      <span></span>
    </div>

    <div v-if="routeState.error" class="route-error panel">
      <span class="empty__icon"><i class="fas fa-triangle-exclamation" aria-hidden="true"></i></span>
      <h1 class="route-error__title">页面加载失败</h1>
      <p class="route-error__text">{{ errorMessage }}</p>
      <div class="route-error__actions">
        <button type="button" class="btn btn--primary" @click="retryRoute">重新加载</button>
        <router-link to="/" class="btn btn--ghost" @click="clearRouteError()">回到首页</router-link>
      </div>
    </div>

    <router-view v-else v-slot="{ Component }">
      <transition name="page">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </main>

  <footer class="site-footer">
    <div class="footer__inner">
      <div class="footer__brand">
        <BrandMark :size="30" />
        <span class="footer__wordmark">NEST · 学巢</span>
      </div>
      <p class="footer__tagline">把每一次努力，变成看得见的成长。</p>
      <div class="footer__links">
        <template v-if="authStore.isLoggedIn">
          <router-link to="/tasks">任务中心</router-link>
          <router-link to="/wrong-questions">错题本</router-link>
          <router-link to="/settings/password">账户设置</router-link>
        </template>
        <template v-else>
          <button type="button" class="footer__link-btn" @click="uiStore.requestAuth('login')">登录</button>
          <button type="button" class="footer__link-btn" @click="uiStore.requestAuth('register')">注册</button>
        </template>
      </div>
      <span class="footer__meta">© 2026 NEST · RISO CAMPUS</span>
    </div>
  </footer>

  <CheckInModal />
  <MobileDock />
  <CommandPalette />
</template>

<script setup>
import { computed, onErrorCaptured, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useUiStore } from '@/stores/uiStore'
import { routeState, clearRouteError } from '@/router'
import PaperBackdrop from '@/components/ui/PaperBackdrop.vue'
import AppHeader from '@/components/common/AppHeader.vue'
import MobileDock from '@/components/common/MobileDock.vue'
import CommandPalette from '@/components/common/CommandPalette.vue'
import CheckInModal from '@/components/tasks/CheckInModal.vue'
import BrandMark from '@/components/common/BrandMark.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const uiStore = useUiStore()

const errorMessage = computed(() => {
  const message = String(routeState.error?.message || routeState.error || '')
  if (/Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk/i.test(message)) {
    return '页面资源加载中断（可能是开发服务器刚重启）。点击下方按钮即可恢复。'
  }
  return message || '页面初始化时发生异常，请重新加载。'
})

onErrorCaptured((error) => {
  console.error('[NEST] 页面渲染异常:', error)
  routeState.error = error
  return false
})

function retryRoute() {
  clearRouteError()
  window.location.reload()
}

watch(() => uiStore.authIntentNonce, () => {
  if (uiStore.authIntent && route.path !== '/') {
    router.push('/')
  }
})

onMounted(() => {
  authStore.restoreSession()
})
</script>

<style scoped>
.route-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  z-index: 300;
  overflow: hidden;
  background: rgba(28, 26, 20, 0.12);
}

.route-progress span {
  display: block;
  width: 36%;
  height: 100%;
  background: repeating-linear-gradient(
    90deg,
    var(--vermilion) 0 14px,
    var(--butter) 14px 28px,
    var(--cobalt) 28px 42px
  );
  animation: route-progress 1s linear infinite;
}

@keyframes route-progress {
  from { transform: translateX(-100%); }
  to { transform: translateX(320%); }
}

.route-error {
  max-width: 560px;
  margin: 72px auto;
  padding: 36px 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}

.route-error__title {
  font-size: 1.3rem;
}

.route-error__text {
  color: var(--text-2);
  font-size: 0.86rem;
  word-break: break-word;
}

.route-error__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 6px;
}

.app-main {
  min-height: calc(100vh - var(--header-h) - 220px);
}

.site-footer {
  position: relative;
  z-index: 2;
  margin-top: 40px;
  padding: 34px 28px 28px;
  background: var(--ink);
  color: var(--paper-1);
  border-top: 2px solid var(--ink);
}

.footer__inner {
  display: flex;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
  max-width: var(--page-max);
  margin: 0 auto;
}

.footer__brand {
  display: flex;
  align-items: center;
  gap: 9px;
}

.footer__wordmark {
  font-family: var(--font-display);
  font-size: 0.84rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--paper-1);
}

.footer__tagline {
  font-size: 0.78rem;
  color: rgba(255, 253, 246, 0.62);
}

.footer__links {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.footer__links a,
.footer__link-btn {
  color: rgba(255, 253, 246, 0.72);
  font-size: 0.78rem;
  font-weight: 600;
  transition: color var(--dur-fast);
}

.footer__links a:hover,
.footer__link-btn:hover {
  color: var(--lime);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.footer__meta {
  font-family: var(--font-mono);
  font-size: 0.64rem;
  letter-spacing: 0.06em;
  color: rgba(255, 253, 246, 0.45);
}

@media (max-width: 768px) {
  .site-footer {
    padding: 24px 18px calc(100px + env(safe-area-inset-bottom));
  }

  .footer__tagline {
    display: none;
  }

  .footer__links {
    margin-left: 0;
  }

  .footer__meta {
    margin-left: auto;
  }
}
</style>
