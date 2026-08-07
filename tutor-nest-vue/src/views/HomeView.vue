<template>
  <div class="main-content">
    <!-- 装饰圆点 -->
    <div class="deco-dot dot1"></div>
    <div class="deco-dot dot2"></div>

    <!-- 上部内容 -->
    <div class="content-top">
      <!-- 未登录：显示大标题 -->
      <div v-if="!authStore.isLoggedIn" class="login-section">
        <h1 class="login-title">学习资料仓库</h1>
      </div>

      <!-- 已登录：学科卡片区域 -->
      <div v-else class="logged-in-content">
        <div v-if="subjects.length === 0" class="empty-state">
          <p>您暂时没有可访问的科目，请联系管理员。</p>
        </div>
        <div v-else class="subject-cards-scroll" ref="scrollContainer">
          <div class="subject-cards">
            <router-link v-for="subject in subjects" :key="subject.name"
              :to="`/category?subject=${encodeURIComponent(subject.name)}`"
              :class="['subject-card', getCardClass(subject.name)]">
              <div class="card-icon">
                <i :class="`fas ${getIconForSubject(subject.name)}`"></i>
              </div>
              <div class="card-name">{{ subject.name }}</div>
              <div class="card-count">{{ subject.count }} 篇文章</div>
              <div class="card-arrow">
                <i class="fas fa-arrow-right"></i>
              </div>
            </router-link>
          </div>
        </div>

        <!-- 错题本入口卡片 -->
        <router-link to="/wrong-questions" class="wrong-questions-entry">
          <div class="wq-entry-icon">
            <i class="fas fa-book-medical"></i>
          </div>
          <div class="wq-entry-text">
            <div class="wq-entry-title">错题本</div>
            <div class="wq-entry-sub">
              <template v-if="unmasteredCount > 0">
                还有 {{ unmasteredCount }} 道错题未掌握
              </template>
              <template v-else>
                没有未掌握的错题，继续保持
              </template>
            </div>
          </div>
          <div v-if="unmasteredCount > 0" class="wq-entry-badge">{{ unmasteredCount }}</div>
          <div class="wq-entry-arrow">
            <i class="fas fa-arrow-right"></i>
          </div>
        </router-link>
      </div>
    </div>

    <!-- 底部固定区域 -->
    <div class="bottom-fixed">
      <!-- 统计信息（登录后显示） -->
      <div v-if="authStore.isLoggedIn" class="stats-area">
        <div class="stats-item">
          <span class="stats-number">{{ stats.subjects }}</span>
          <span class="stats-label">门学科</span>
        </div>
        <div class="stats-divider"></div>
        <div class="stats-item">
          <span class="stats-number">{{ stats.articles }}</span>
          <span class="stats-label">篇文章</span>
        </div>
      </div>

      <!-- 登录表单（未登录时显示） -->
      <form v-else class="login-form" @submit.prevent="handleLogin">
        <input v-model="username" type="text" placeholder="你的用户名" required autofocus ref="usernameInput" />
        <button type="submit" class="login-btn">进入</button>
        <button type="button" class="register-btn" @click="handleRegister">注册</button>
        <div v-if="error" class="login-error">{{ error }}</div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { useWrongQuestionsStore } from '@/stores/wrongQuestionsStore'

const router = useRouter()
const authStore = useAuthStore()
const blogStore = useArticleStore()
const wrongQuestionsStore = useWrongQuestionsStore()

// 本地状态
const username = ref('')
const error = ref('')
const scrollContainer = ref(null)
const usernameInput = ref(null)
const blogData = ref([])

// 图标映射
const ICON_MAP = {
  '英语': 'fa-language',
  '化学': 'fa-flask'
}

// 计算属性
const subjects = computed(() => {
  if (!authStore.isLoggedIn || !blogStore.blogData.length) return []

  const permissionIds = authStore.getPermissionIds()
    .map(Number)
    .filter(Number.isFinite)

  const allowedArticles = blogStore.blogData.filter(b =>
    permissionIds.includes(Number(b.id))
  )

  const subjectMap = new Map()
  allowedArticles.forEach(b => {
    const subject = b.series
    if (!subjectMap.has(subject)) {
      subjectMap.set(subject, [])
    }
    subjectMap.get(subject).push(b)
  })

  return Array.from(subjectMap.entries())
    .map(([name, articles]) => ({
      name,
      count: articles.length
    }))
    .sort((a, b) => a.name.localeCompare(b.name))
})

const stats = computed(() => {
  if (!subjects.value.length) {
    return { subjects: 0, articles: 0 }
  }
  return {
    subjects: subjects.value.length,
    articles: subjects.value.reduce((sum, s) => sum + s.count, 0)
  }
})

// 未掌握的错题数量（入口卡片徽章）
const unmasteredCount = computed(() => {
  return wrongQuestionsStore.questions.filter(q => !q.mastered).length
})

// 方法
function getIconForSubject(subject) {
  return ICON_MAP[subject] || 'fa-book'
}

function getCardClass(subject) {
  if (subject === '英语') return 'card-english'
  if (subject === '化学') return 'card-chemistry'
  return ''
}

async function handleLogin() {
  error.value = ''
  const trimmedUsername = username.value.trim()

  if (!trimmedUsername) {
    error.value = '请输入用户名'
    await nextTick()
    usernameInput.value?.focus()
    return
  }

  try {
    await authStore.login(trimmedUsername)
    await loadArticlesData()
  } catch (err) {
    error.value = err.message || '登录失败，请重试'
    await nextTick()
    usernameInput.value?.focus()
  }
}

async function handleRegister() {
  error.value = ''
  const trimmedUsername = username.value.trim()

  if (!trimmedUsername) {
    error.value = '请输入用户名'
    await nextTick()
    usernameInput.value?.focus()
    return
  }

  try {
    await authStore.register(trimmedUsername)
    await loadArticlesData()
  } catch (err) {
    error.value = err.message || '注册失败，请重试'
    await nextTick()
    usernameInput.value?.focus()
  }
}

async function loadArticlesData() {
  try {
    await blogStore.loadArticleData()
  } catch (err) {
    error.value = '加载数据失败，请稍后重试'
  }
}

// 加载错题本数据（用于入口卡片计数）
async function loadWrongQuestions() {
  try {
    const studentId = wrongQuestionsStore.getStudentId(authStore.currentUser)
    await wrongQuestionsStore.fetchQuestions(studentId)
  } catch (err) {
    console.error('加载错题数据失败:', err)
  }
}

// 水平滚动
function enableHorizontalScroll(container) {
  if (!container) return

  container.addEventListener('wheel', (e) => {
    if (window.innerWidth <= 640) return
    if (container.scrollWidth <= container.clientWidth) return

    e.preventDefault()
    container.scrollLeft += e.deltaY || e.detail || 0
  }, { passive: false })
}

// 管理员快捷键
let plusCount = 0
let timer = null

function handleKeydown(e) {
  if (e.key === '+') {
    plusCount++
    clearTimeout(timer)
    timer = setTimeout(() => {
      plusCount = 0
    }, 1000)

    if (plusCount >= 3) {
      plusCount = 0
      router.push('/admin')
    }
  }
}

// 生命周期
onMounted(async () => {
  // 如果已登录，加载数据
  if (authStore.isLoggedIn) {
    await loadArticlesData()
    await loadWrongQuestions()
  }

  // 设置水平滚动
  await nextTick()
  enableHorizontalScroll(scrollContainer.value)

  // 监听键盘快捷键
  document.addEventListener('keydown', handleKeydown)
})

// 清理事件监听
import { onUnmounted } from 'vue'
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})

// 暴露方法给父组件（导航栏的登录按钮）
defineExpose({
  focusUsername: () => {
    usernameInput.value?.focus()
  }
})
</script>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.main-content {
  flex: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 30px 24px 20px;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: calc(100vh - 70px);
  position: relative;
}

/* 装饰圆点 */
.deco-dot {
  position: absolute;
  border-radius: 50%;
  opacity: 0.2;
  pointer-events: none;
  z-index: 0;
}

.dot1 {
  width: 220px;
  height: 220px;
  background: var(--teal-pale, #D4F4F2);
  top: -60px;
  right: -60px;
}

.dot2 {
  width: 160px;
  height: 160px;
  background: var(--green-light, #8FCFB8);
  bottom: 100px;
  left: -40px;
}

/* 内容上部 */
.content-top {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 2;
  padding-bottom: 20px;
}

/* 未登录大标题 */
.login-section {
  text-align: center;
}

.login-title {
  font-size: 4rem;
  font-weight: 700;
  color: #2d4a3a;
  letter-spacing: 2px;
  text-shadow: 0 4px 20px rgba(80, 130, 120, 0.06);
}

/* 已登录：卡片区域 */
.logged-in-content {
  width: 100%;
  animation: fadeIn 0.4s ease;
}

.empty-state {
  text-align: center;
  color: var(--gray, #5a7a6a);
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.subject-cards-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  padding: 8px 4px 20px 4px;
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: var(--teal-light, #B5E6E3) transparent;
  display: flex;
  justify-content: center;
}

.subject-cards-scroll::-webkit-scrollbar {
  height: 6px;
}

.subject-cards-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.subject-cards-scroll::-webkit-scrollbar-thumb {
  background: var(--teal-light, #B5E6E3);
  border-radius: 10px;
}

.subject-cards {
  display: flex;
  gap: 40px;
  justify-content: center;
  flex-wrap: nowrap;
  padding: 4px 8px;
}

/* ========== 书本卡片样式（5页堆叠） ========== */
.subject-card {
  position: relative;
  flex: 0 0 auto;
  width: 200px;
  padding: 30px 20px 28px;
  border-radius: 4px 16px 16px 4px;
  background: #ffffff;
  /* 改为不透明白色，内页才能清晰显示 */
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-left: 8px solid var(--teal-dark, #5BA8A4);
  cursor: pointer;
  text-align: center;
  transform: perspective(800px) rotateY(-2deg) rotateX(2deg);
  transform-origin: left center;
  transition: all 0.35s cubic-bezier(0.2, 0.9, 0.4, 1);
  text-decoration: none;
  color: inherit;
  display: block;

  /* 多层阴影模拟5页堆叠（卡片本身是第1页，后面4层阴影代表第2~5页） */
  box-shadow:
    /* 原有的轻柔环境阴影（可保留） */
    8px 8px 24px rgba(80, 130, 120, 0.06),
    /* 左侧内阴影模拟书脊 */
    -2px 0 0 0 rgba(91, 168, 164, 0.2) inset,
    /* 书页堆叠（向右下方向偏移） */
    2px 2px 0 0 #f9f9f9,
    4px 4px 0 0 #f0f0f0,
    6px 6px 0 0 #e5e5e5,
    8px 8px 0 0 #dbdbdb;
}

/* 移除原有的右侧渐变伪元素，不再需要 */
.subject-card::after {
  display: none;
}

.subject-card:hover {
  transform: perspective(800px) rotateY(-6deg) rotateX(4deg) translateY(-6px);
  box-shadow:
    12px 16px 40px rgba(80, 130, 120, 0.12),
    -2px 0 0 0 rgba(91, 168, 164, 0.3) inset,
    /* hover 时书页偏移稍微加大，增加立体厚度 */
    2px 2px 0 0 #f9f9f9,
    5px 5px 0 0 #f0f0f0,
    8px 8px 0 0 #e5e5e5,
    11px 11px 0 0 #dbdbdb;
  background: #ffffff;
}

/* 图标、文字等样式保持不变 */
.subject-card .card-icon {
  font-size: 2.6rem;
  margin-bottom: 10px;
  color: var(--teal-dark, #5BA8A4);
}

.subject-card .card-name {
  font-size: 1.5rem;
  font-weight: 600;
  color: #2d4a3a;
}

.subject-card .card-count {
  font-size: 0.9rem;
  color: var(--gray, #5a7a6a);
  margin-top: 6px;
  background: rgba(255, 255, 255, 0.3);
  padding: 2px 14px;
  border-radius: 30px;
  display: inline-block;
}

.subject-card .card-arrow {
  margin-top: 14px;
  opacity: 0.2;
  transition: 0.3s;
}

.subject-card:hover .card-arrow {
  opacity: 1;
  transform: translateX(6px);
}

/* 不同学科书脊颜色 */
.card-english {
  border-left-color: var(--teal-dark, #5BA8A4);
}

.card-english .card-icon {
  color: var(--teal-dark, #5BA8A4);
}

.card-chemistry {
  border-left-color: var(--green, #8FCFB8);
}

.card-chemistry .card-icon {
  color: var(--green, #8FCFB8);
}

/* ========== 错题本入口卡片 ========== */
.wrong-questions-entry {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 8px auto 0;
  max-width: 480px;
  padding: 16px 22px;
  border-radius: 18px;
  background: rgba(255, 251, 230, 0.75);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(217, 186, 75, 0.35);
  border-left: 8px solid #d9ba4b;
  box-shadow: 8px 8px 24px rgba(80, 130, 120, 0.08);
  text-decoration: none;
  color: inherit;
  transition: all 0.35s cubic-bezier(0.2, 0.9, 0.4, 1);
  position: relative;
}

.wrong-questions-entry:hover {
  transform: translateY(-3px) rotateX(2deg);
  box-shadow: 12px 16px 40px rgba(80, 130, 120, 0.14);
  background: rgba(255, 251, 230, 0.92);
}

.wq-entry-icon {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  color: #8a6d1a;
  background: rgba(217, 186, 75, 0.18);
}

.wq-entry-text {
  flex: 1;
  min-width: 0;
}

.wq-entry-title {
  font-size: 1.2rem;
  font-weight: 700;
  color: #705d13;
}

.wq-entry-sub {
  font-size: 0.85rem;
  color: #8a7a4a;
  margin-top: 2px;
}

.wq-entry-badge {
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border-radius: 999px;
  background: #d9ba4b;
  color: #ffffff;
  font-size: 0.9rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(217, 186, 75, 0.4);
}

.wq-entry-arrow {
  color: rgba(138, 109, 26, 0.35);
  transition: transform 0.3s ease;
}

.wrong-questions-entry:hover .wq-entry-arrow {
  transform: translateX(5px);
  color: #8a6d1a;
}

/* 底部固定区域 */
.bottom-fixed {
  flex-shrink: 0;
  z-index: 2;
  padding: 16px 0 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 30px 30px 0 0;
  margin-top: auto;
}

/* 统计信息 */
.stats-area {
  display: flex;
  align-items: center;
  gap: 30px;
  padding: 8px 24px;
}

.stats-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.stats-number {
  font-size: 2rem;
  font-weight: 700;
  color: #2d4a3a;
  line-height: 1;
}

.stats-label {
  font-size: 1rem;
  color: var(--gray, #5a7a6a);
  font-weight: 300;
}

.stats-divider {
  width: 1px;
  height: 30px;
  background: rgba(90, 122, 106, 0.2);
}

/* 登录表单 */
.login-form {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 600px;
  width: 100%;
  padding: 0 16px;
  flex-wrap: wrap;
  justify-content: center;
}

.login-form input {
  flex: 2;
  min-width: 180px;
  padding: 12px 18px;
  border: none;
  border-radius: 50px;
  font-size: 1rem;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(8px);
  box-shadow: 0 2px 8px rgba(80, 130, 120, 0.04);
  transition: background 0.2s, box-shadow 0.2s;
  color: #2d4a3a;
}

.login-form input::placeholder {
  color: rgba(90, 122, 106, 0.5);
}

.login-form input:focus {
  outline: none;
  background: rgba(255, 255, 255, 0.5);
  box-shadow: 0 4px 16px rgba(80, 130, 120, 0.08);
}

.login-form .login-btn,
.login-form .register-btn {
  padding: 12px 24px;
  border-radius: 50px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  color: #2d4a3a;
  box-shadow: 0 2px 8px rgba(80, 130, 120, 0.04);
}

.login-form .login-btn {
  background: rgba(91, 168, 164, 0.3);
}

.login-form .login-btn:hover {
  background: rgba(91, 168, 164, 0.5);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(91, 168, 164, 0.1);
}

.login-form .register-btn {
  background: rgba(255, 255, 255, 0.15);
}

.login-form .register-btn:hover {
  background: rgba(255, 255, 255, 0.35);
  transform: translateY(-2px);
}

.login-error {
  width: 100%;
  text-align: center;
  color: #d57587;
  font-size: 0.9rem;
  margin-top: 6px;
  min-height: 1.2em;
}

/* 响应式 */
@media (min-width: 1024px) {
  .login-title {
    font-size: 5.2rem;
  }

  .subject-card {
    width: 250px;
    padding: 40px 28px 36px;
  }

  .subject-card .card-icon {
    font-size: 3.2rem;
  }

  .subject-card .card-name {
    font-size: 1.8rem;
  }
}

@media (min-width: 641px) and (max-width: 1023px) {
  .subject-card {
    width: 180px;
    padding: 28px 18px 24px;
  }

  .subject-card .card-icon {
    font-size: 2.2rem;
  }

  .subject-card .card-name {
    font-size: 1.3rem;
  }
}

@media (max-width: 640px) {
  .main-content {
    padding: 16px 12px 10px;
    min-height: calc(100vh - 60px);
  }

  .login-title {
    font-size: 2.8rem;
  }

  .subject-cards-scroll {
    overflow-x: visible;
    overflow-y: visible;
    padding: 0;
  }

  .subject-cards {
    flex-wrap: wrap;
    justify-content: center;
    gap: 20px;
  }

  .subject-card {
    flex: 1 1 140px;
    max-width: 200px;
    min-width: 120px;
    padding: 24px 14px 20px;
    width: auto;
  }

  .subject-card .card-icon {
    font-size: 2rem;
  }

  .subject-card .card-name {
    font-size: 1.2rem;
  }

  .subject-card .card-count {
    font-size: 0.75rem;
  }

  .bottom-fixed {
    padding: 12px 0 8px;
    border-radius: 20px 20px 0 0;
  }

  .stats-area {
    gap: 16px;
    padding: 6px 12px;
  }

  .stats-number {
    font-size: 1.6rem;
  }

  .stats-label {
    font-size: 0.9rem;
  }

  .login-form {
    gap: 8px;
  }

  .login-form input {
    flex: 1 1 100%;
    min-width: 100%;
    padding: 10px 14px;
    font-size: 0.95rem;
  }

  .login-form .login-btn,
  .login-form .register-btn {
    padding: 10px 18px;
    font-size: 0.95rem;
    flex: 1;
  }

  .dot1,
  .dot2 {
    display: none;
  }

  .wrong-questions-entry {
    max-width: 100%;
    padding: 12px 16px;
    gap: 12px;
  }

  .wq-entry-icon {
    width: 42px;
    height: 42px;
    font-size: 1.2rem;
  }

  .wq-entry-title {
    font-size: 1.05rem;
  }

  .wq-entry-sub {
    font-size: 0.78rem;
  }
}
</style>