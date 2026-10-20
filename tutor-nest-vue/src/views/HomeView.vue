<!-- src/views/HomeView.vue -->
<template>
  <div class="home">
    <!-- ============================ 未登录：杂志封面式着陆页 ============================ -->
    <template v-if="!authStore.isLoggedIn">
      <section class="hero">
        <div class="hero__copy">
          <span class="eyebrow">NEST · 学巢 / RISO CAMPUS</span>
          <h1 class="hero__title">
            把每一次努力<br />
            变成<span class="text-gradient">看得见的成长</span>
          </h1>
          <p class="hero__sub">讲义、错题、训练、收藏 —— 一座属于你的学院式学习空间。</p>

          <div class="hero__actions">
            <button type="button" class="btn btn--primary btn--lg" @click="focusAuth('login')">
              进入学习舱 <i class="fas fa-arrow-right" aria-hidden="true"></i>
            </button>
            <button type="button" class="btn btn--ghost btn--lg" @click="scrollToId('cards')">
              <i class="fas fa-images" aria-hidden="true"></i> 看看收藏卡
            </button>
          </div>

          <ul class="hero__chips">
            <li class="chip"><span class="dot dot--vermilion"></span>KaTeX 公式</li>
            <li class="chip"><span class="dot dot--cobalt"></span>手写批注</li>
            <li class="chip"><span class="dot dot--lime"></span>错题训练</li>
          </ul>

          <div class="hero__stats">
            <div class="hero__stat">
              <strong>{{ publicStats.articles }}</strong>
              <span>篇讲义</span>
            </div>
            <div class="hero__stat">
              <strong>{{ publicStats.subjects }}</strong>
              <span>门学科</span>
            </div>
            <div class="hero__stat">
              <strong>{{ publicStats.cards }}</strong>
              <span>张收藏卡</span>
            </div>
            <div class="hero__stat is-accent">
              <strong>+100</strong>
              <span>每日签到积分</span>
            </div>
          </div>
        </div>

        <div ref="visualRef" class="hero__visual" aria-hidden="true">
          <span class="hero__shape hero__shape--circle"></span>
          <span class="hero__shape hero__shape--grid"></span>
          <span class="hero__shape hero__shape--strip"></span>

          <div class="collage">
            <figure class="polaroid polaroid--back">
              <img :src="asset('cards/dessert-r.png')" alt="" />
            </figure>
            <figure class="polaroid polaroid--mid">
              <img :src="asset('cards/ocean-r.png')" alt="" />
            </figure>
            <figure class="polaroid polaroid--front">
              <img :src="asset('cards/cosmos-r.png')" alt="" />
              <figcaption>星际漫游 · 稀有卡</figcaption>
            </figure>
            <span class="tape tape--a"></span>
            <span class="tape tape--b"></span>
          </div>

          <span class="sticker sticker--1"><i class="fas fa-star" aria-hidden="true"></i> 稀有卡池</span>
          <span class="sticker sticker--2"><i class="fas fa-fire" aria-hidden="true"></i> 连续签到</span>
          <span class="sticker sticker--3"><i class="fas fa-bolt" aria-hidden="true"></i> 200 分一抽</span>
        </div>
      </section>

      <RevealOnScroll class="auth-section" direction="up">
        <section id="auth" ref="authRef" class="auth">
          <div class="auth__panel panel">
            <aside class="auth__aside">
              <div class="auth__brand">
                <BrandMark :size="40" />
                <div>
                  <strong>NEST</strong>
                  <span>学巢 · 学习资料仓库</span>
                </div>
              </div>
              <h2 class="auth__headline">今天，<br />先从签到开始。</h2>
              <p class="auth__desc">登录后解锁学科地图、错题批改与收藏卡池。</p>

              <ul class="auth__perks">
                <li>
                  <span class="perk-dot perk-dot--cobalt"><i class="fas fa-map" aria-hidden="true"></i></span>
                  <div><strong>学科地图</strong><span>按权限解锁讲义与练习</span></div>
                </li>
                <li>
                  <span class="perk-dot perk-dot--vermilion"><i class="fas fa-book-medical" aria-hidden="true"></i></span>
                  <div><strong>错题批改</strong><span>收题、归因、重练到掌握</span></div>
                </li>
                <li>
                  <span class="perk-dot perk-dot--lime"><i class="fas fa-gift" aria-hidden="true"></i></span>
                  <div><strong>签到与收藏</strong><span>积分里程碑兑换 35 张卡</span></div>
                </li>
              </ul>

              <div class="auth__mini-cards" aria-hidden="true">
                <img :src="asset('cards/puppy-c2.png')" alt="" />
                <img :src="asset('cards/ocean-c4.png')" alt="" />
                <img :src="asset('cards/cosmos-c0.png')" alt="" />
              </div>
            </aside>

            <div class="auth__pane">
              <div class="auth__tabs seg" role="tablist" aria-label="登录或注册">
                <button type="button" role="tab" class="seg__btn" :class="{ 'is-active': mode === 'login' }"
                  :aria-selected="mode === 'login'" @click="switchMode('login')">
                  登录
                </button>
                <button type="button" role="tab" class="seg__btn" :class="{ 'is-active': mode === 'register' }"
                  :aria-selected="mode === 'register'" @click="switchMode('register')">
                  注册
                </button>
              </div>

              <p class="auth__pane-title">{{ mode === 'login' ? '欢迎回来' : '创建你的学习空间' }}</p>

              <form class="auth__form" novalidate @submit.prevent="mode === 'login' ? handleLogin() : handleRegister()">
                <label class="field">
                  <span class="field-label">用户名</span>
                  <div class="input-wrap">
                    <i class="fas fa-user" aria-hidden="true"></i>
                    <input ref="usernameInput" v-model="username" class="input" type="text" autocomplete="username"
                      placeholder="用户名" required />
                  </div>
                </label>

                <label class="field">
                  <span class="field-label">密码</span>
                  <div class="input-wrap">
                    <i class="fas fa-lock" aria-hidden="true"></i>
                    <input v-model="password" class="input" :type="showPassword ? 'text' : 'password'"
                      :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="密码"
                      minlength="6" maxlength="128" required />
                    <button type="button" class="input-wrap__action" :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                      @click="showPassword = !showPassword">
                      <i :class="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'" aria-hidden="true"></i>
                    </button>
                  </div>
                </label>

                <label v-if="mode === 'register'" class="field">
                  <span class="field-label">确认密码</span>
                  <div class="input-wrap">
                    <i class="fas fa-shield-halved" aria-hidden="true"></i>
                    <input v-model="confirmPassword" class="input" type="password" autocomplete="new-password"
                      placeholder="确认密码" minlength="6" maxlength="128" required />
                  </div>
                </label>

                <transition name="fade">
                  <p v-if="error" class="auth__error" role="alert">
                    <i class="fas fa-circle-exclamation" aria-hidden="true"></i> {{ error }}
                  </p>
                </transition>

                <button type="submit" class="btn btn--primary btn--lg btn--block auth__submit" :disabled="submitting">
                  <span v-if="submitting" class="spinner" aria-hidden="true"></span>
                  {{ submitting ? '请稍候…' : (mode === 'login' ? '登录' : '注册') }}
                </button>

                <p class="auth__hint">
                  <template v-if="mode === 'login'">还没有账号？<button type="button" class="auth__link"
                      @click="switchMode('register')">立即注册</button></template>
                  <template v-else>密码长度 6～128 个字符；已有账号？<button type="button" class="auth__link"
                      @click="switchMode('login')">直接登录</button></template>
                </p>
              </form>
            </div>
          </div>
        </section>
      </RevealOnScroll>

      <section class="features">
        <RevealOnScroll v-for="(feature, index) in features" :key="feature.title" :delay="index * 0.06">
          <article class="feature" :class="`feature--${feature.tone}`">
            <span class="feature__num mono">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="feature__icon"><i :class="feature.icon" aria-hidden="true"></i></span>
            <h3 class="feature__title">{{ feature.title }}</h3>
            <p class="feature__desc">{{ feature.desc }}</p>
            <span class="feature__meta mono">{{ feature.meta }}</span>
          </article>
        </RevealOnScroll>
      </section>

      <section id="cards" class="cards-section">
        <div class="section-head">
          <div>
            <span class="eyebrow eyebrow--light">COLLECTION / 收藏卡池</span>
            <h2 class="cards-section__title">学习，也能开出<span class="marker">好看的奖励</span></h2>
          </div>
          <button type="button" class="btn btn--soft" @click="focusAuth('login')">
            登录解锁 <i class="fas fa-arrow-right" aria-hidden="true"></i>
          </button>
        </div>

        <div class="marquee cards-marquee">
          <div class="marquee__track">
            <figure v-for="(card, index) in galleryCards" :key="`a-${index}`" class="gallery-card">
              <img :src="card.img" :alt="card.name" loading="lazy" />
              <figcaption>
                <span class="gallery-card__name">{{ card.name }}</span>
                <span class="gallery-card__tag">{{ card.rare ? '稀有' : '普通' }}</span>
              </figcaption>
            </figure>
            <figure v-for="(card, index) in galleryCards" :key="`b-${index}`" class="gallery-card" aria-hidden="true">
              <img :src="card.img" :alt="card.name" loading="lazy" />
              <figcaption>
                <span class="gallery-card__name">{{ card.name }}</span>
                <span class="gallery-card__tag">{{ card.rare ? '稀有' : '普通' }}</span>
              </figcaption>
            </figure>
          </div>
        </div>
      </section>
    </template>

    <!-- ============================ 已登录：学习仪表盘 ============================ -->
    <template v-else>
      <section class="dash-hero panel">
        <div class="dash-hero__main">
          <span class="eyebrow">{{ todayLabel }} / {{ greeting }}</span>
          <h1 class="dash-hero__title">{{ authStore.username }}，<span class="text-gradient">继续发光</span></h1>
          <p class="dash-hero__sub">
            <template v-if="todoList.length">还有 {{ totalPending }} 道题等你提交，从最近的一篇继续。</template>
            <template v-else-if="unmasteredCount > 0">没有待提交的题目，去错题本收个尾。</template>
            <template v-else>今天的进度很干净，签个到再出发。</template>
          </p>

          <div class="dash-hero__actions">
            <button type="button" class="btn btn--primary" @click="continueLearning">
              <i class="fas fa-play" aria-hidden="true"></i> {{ todoList.length ? '继续学习' : '去任务中心' }}
            </button>
            <button type="button" class="btn btn--soft" :disabled="checkinStore.todayCheckedIn" @click="openCheckin">
              <i :class="checkinStore.todayCheckedIn ? 'fas fa-check' : 'fas fa-calendar-check'" aria-hidden="true"></i>
              {{ checkinStore.todayCheckedIn ? '今日已签到' : '签到 +100' }}
            </button>
            <router-link to="/wrong-questions" class="btn btn--ghost">
              <i class="fas fa-book-medical" aria-hidden="true"></i> 错题本
              <span v-if="unmasteredCount" class="badge">{{ unmasteredCount }}</span>
            </router-link>
          </div>

          <div class="dash-hero__chips">
            <span class="chip chip--amber"><i class="fas fa-coins" aria-hidden="true"></i> {{ tasksStore.points }} 积分</span>
            <span class="chip chip--lime"><i class="fas fa-fire" aria-hidden="true"></i> 连续 {{ streak }} 天</span>
            <span class="chip chip--cyan"><i class="fas fa-gift" aria-hidden="true"></i> {{ tasksStore.collection.length }} 张卡</span>
          </div>
        </div>

        <div class="level-card">
          <div class="level-card__card">
            <span class="level-card__label mono">LEVEL PROFILE · 成长等级</span>
            <span class="level-card__name">{{ authStore.username }}</span>
            <div class="level-card__lvrow">
              <strong class="level-card__level">LV.{{ levelInfo.level }}</strong>
              <span class="level-card__title">{{ levelInfo.title }}</span>
            </div>
            <strong class="level-card__points">{{ tasksStore.points }}<small>pts</small></strong>
            <div class="level-card__bar">
              <span :style="{ width: levelInfo.progress + '%' }"></span>
            </div>
            <div class="level-card__foot">
              <span>距 LV.{{ levelInfo.nextLevel }}</span>
              <span>还差 {{ levelInfo.toNext }} 积分</span>
            </div>
          </div>
          <router-link to="/tasks" class="level-card__link">
            查看里程碑 <i class="fas fa-arrow-right" aria-hidden="true"></i>
          </router-link>
        </div>
      </section>

      <div class="stat-grid dash-stats">
        <article class="stat-card">
          <p class="stat-value"><CountUp :value="stats.subjects" /></p>
          <p class="stat-label">门学科</p>
          <p class="stat-sub">已解锁的学科舱位</p>
        </article>
        <article class="stat-card stat-card--cyan">
          <p class="stat-value"><CountUp :value="stats.articles" /></p>
          <p class="stat-label">篇讲义</p>
          <p class="stat-sub">可访问的学习内容</p>
        </article>
        <article class="stat-card stat-card--amber">
          <p class="stat-value"><CountUp :value="totalPending" /></p>
          <p class="stat-label">题待提交</p>
          <p class="stat-sub">别让它们过夜</p>
        </article>
        <article class="stat-card stat-card--coral">
          <p class="stat-value"><CountUp :value="unmasteredCount" /></p>
          <p class="stat-label">道错题未掌握</p>
          <p class="stat-sub">去训练舱消灭它们</p>
        </article>
      </div>

      <section class="section">
        <div class="section-head">
          <div>
            <span class="eyebrow">SUBJECTS / 学科舱位</span>
            <h2 class="section-title">选择今天要进入的舱位</h2>
          </div>
          <span class="section-note">{{ subjects.length }} 个学科 · {{ stats.articles }} 篇讲义</span>
        </div>

        <div v-if="subjects.length === 0" class="empty">
          <span class="empty__icon"><i class="fas fa-satellite" aria-hidden="true"></i></span>
          <p class="empty__title">暂时没有可访问的学科</p>
          <p class="empty__text">请联系管理员为你开通内容授权，授权后会立即出现在这里。</p>
        </div>

        <div v-else class="subject-grid">
          <router-link v-for="(subject, index) in subjects" :key="subject.name"
            :to="`/category?subject=${encodeURIComponent(subject.name)}`" class="subject-card"
            :class="`subject-card--${subjectMeta(subject.name).key}`" :style="{ '--i': index }">
            <img class="subject-card__cover" :src="subjectMeta(subject.name).cover" :alt="`${subject.name}封面`"
              loading="lazy" />
            <span class="subject-card__band"></span>
            <div class="subject-card__body">
              <span class="subject-card__icon"><i :class="`fas ${subjectMeta(subject.name).icon}`"
                  aria-hidden="true"></i></span>
              <h3 class="subject-card__name">{{ subject.name }}</h3>
              <p class="subject-card__meta">{{ subject.count }} 篇文章</p>
              <span class="subject-card__pending" :class="{ 'is-clear': pendingBySubject.get(subject.name) === 0 }">
                <i :class="pendingBySubject.get(subject.name) ? 'fas fa-clock' : 'fas fa-circle-check'" aria-hidden="true"></i>
                {{ pendingBySubject.get(subject.name) ? `${pendingBySubject.get(subject.name)} 题待提交` : '全部已提交' }}
              </span>
              <span class="subject-card__go">进入舱位 <i class="fas fa-arrow-right" aria-hidden="true"></i></span>
            </div>
          </router-link>
        </div>
      </section>

      <div class="dash-cols">
        <RevealOnScroll class="dash-col" direction="up">
          <section class="panel panel--pad today-panel">
            <div class="section-head">
              <div>
                <span class="eyebrow">TODAY / 今日待办</span>
                <h2 class="section-title">接着上次的进度</h2>
              </div>
              <span class="section-note">{{ totalPending }} 题</span>
            </div>

            <ul v-if="todoList.length" class="todo-list">
              <li v-for="item in todoList" :key="item.articleId">
                <router-link :to="`/blog/${item.articleId}`" class="todo-item">
                  <span class="todo-item__index">{{ item.index }}</span>
                  <span class="todo-item__main">
                    <span class="todo-item__title">{{ item.title }}</span>
                    <span class="todo-item__meta">{{ item.subject }} · {{ item.pending }} 题待提交</span>
                  </span>
                  <span class="todo-item__go"><i class="fas fa-arrow-right" aria-hidden="true"></i></span>
                </router-link>
              </li>
            </ul>
            <div v-else class="empty empty--inline">
              <span class="empty__icon"><i class="fas fa-mug-hot" aria-hidden="true"></i></span>
              <p class="empty__title">今天没有待提交的题</p>
              <p class="empty__text">去错题本翻翻旧账，或者直接开始一次训练。</p>
              <router-link to="/wrong-training" class="btn btn--soft btn--sm">开始训练</router-link>
            </div>
          </section>
        </RevealOnScroll>

        <RevealOnScroll class="dash-col" direction="up" :delay="0.06">
          <section class="panel panel--pad quick-panel">
            <div class="section-head">
              <div>
                <span class="eyebrow">SHORTCUTS / 快捷入口</span>
                <h2 class="section-title">一步直达</h2>
              </div>
            </div>
            <div class="quick-grid">
              <router-link to="/wrong-questions" class="quick-item quick-item--coral">
                <span class="quick-item__icon"><i class="fas fa-book-medical" aria-hidden="true"></i></span>
                <span class="quick-item__text"><strong>错题本</strong><small>{{ unmasteredCount }} 题待掌握</small></span>
              </router-link>
              <router-link to="/wrong-training" class="quick-item quick-item--lime">
                <span class="quick-item__icon"><i class="fas fa-dumbbell" aria-hidden="true"></i></span>
                <span class="quick-item__text"><strong>错题训练</strong><small>沉浸式重练</small></span>
              </router-link>
              <router-link to="/tasks" class="quick-item quick-item--amber">
                <span class="quick-item__icon"><i class="fas fa-trophy" aria-hidden="true"></i></span>
                <span class="quick-item__text"><strong>任务中心</strong><small>{{ tasksStore.collection.length }} 张收藏卡</small></span>
              </router-link>
              <router-link to="/settings/password" class="quick-item quick-item--sky">
                <span class="quick-item__icon"><i class="fas fa-key" aria-hidden="true"></i></span>
                <span class="quick-item__text"><strong>账户设置</strong><small>修改登录密码</small></span>
              </router-link>
            </div>

            <div class="quick-cards">
              <div class="quick-cards__head">
                <span>我的收藏</span>
                <router-link to="/tasks">全部 <i class="fas fa-arrow-right" aria-hidden="true"></i></router-link>
              </div>
              <div class="quick-cards__row">
                <div v-for="(card, index) in ownedCards" :key="index" class="mini-card" :class="{ 'is-rare': card.rare }">
                  <img :src="card.img" :alt="card.name" loading="lazy" />
                </div>
                <router-link v-if="!ownedCards.length" to="/tasks" class="mini-card mini-card--empty">
                  <i class="fas fa-lock" aria-hidden="true"></i>
                  <span>未解锁</span>
                </router-link>
              </div>
            </div>
          </section>
        </RevealOnScroll>
      </div>

      <section class="recent-section">
        <div class="section-head">
          <div>
            <span class="eyebrow">RECENT / 最近更新</span>
            <h2 class="section-title">新到的讲义</h2>
          </div>
          <span class="section-note">按发布时间排序</span>
        </div>
        <div class="recent-grid">
          <router-link v-for="article in recentArticles" :key="article.id" :to="`/blog/${article.id}`"
            class="recent-card panel">
            <span class="recent-card__subject">{{ article.series }}</span>
            <h3 class="recent-card__title">{{ article.title }}</h3>
            <span class="recent-card__meta">
              <i class="fas fa-calendar-day" aria-hidden="true"></i> {{ article.date }}
              <i class="fas fa-arrow-right recent-card__arrow" aria-hidden="true"></i>
            </span>
          </router-link>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useCheckinStore } from '@/stores/checkinStore'
import { useArticleStore } from '@/stores/blogStore'
import { useWrongQuestionsStore } from '@/stores/wrongQuestionsStore'
import { useTasksStore } from '@/stores/tasksStore'
import { useUiStore } from '@/stores/uiStore'
import { learningGateway } from '@/gateways/learningGateway'
import { levelFromPoints } from '@/utils/level'
import { CARD_SETS, findCard } from '@/data/cardCatalog'
import BrandMark from '@/components/common/BrandMark.vue'
import CountUp from '@/components/ui/CountUp.vue'
import RevealOnScroll from '@/components/ui/RevealOnScroll.vue'

const router = useRouter()
const authStore = useAuthStore()
const blogStore = useArticleStore()
const wrongQuestionsStore = useWrongQuestionsStore()
const checkinStore = useCheckinStore()
const tasksStore = useTasksStore()
const uiStore = useUiStore()

const base = import.meta.env.BASE_URL

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const mode = ref('login')
const error = ref('')
const showPassword = ref(false)
const submitting = ref(false)

const authRef = ref(null)
const usernameInput = ref(null)
const visualRef = ref(null)

const progress = ref([])

const publicStats = computed(() => {
  const subjects = new Set(blogStore.blogData.map(item => item.series).filter(Boolean))
  return {
    articles: blogStore.blogData.length || 35,
    subjects: subjects.size || 3,
    cards: CARD_SETS.reduce((total, set) => total + 1 + set.commons.length, 0)
  }
})

const subjects = computed(() => {
  if (!authStore.isLoggedIn || !blogStore.blogData.length) return []

  const permissionIds = authStore.getPermissionIds()
    .map(Number)
    .filter(Number.isFinite)

  const allowedArticles = blogStore.blogData.filter(item => permissionIds.includes(Number(item.id)))
  const subjectMap = new Map()
  allowedArticles.forEach(item => {
    if (!subjectMap.has(item.series)) subjectMap.set(item.series, [])
    subjectMap.get(item.series).push(item)
  })

  return Array.from(subjectMap.entries())
    .map(([name, articles]) => ({ name, count: articles.length }))
    .sort((left, right) => left.name.localeCompare(right.name))
})

const stats = computed(() => ({
  subjects: subjects.value.length,
  articles: subjects.value.reduce((total, subject) => total + subject.count, 0)
}))

const unmasteredCount = computed(() => wrongQuestionsStore.questions.filter(question => !question.mastered).length)

const pendingByArticle = computed(() => {
  const map = new Map()
  for (const item of progress.value) {
    const pending = Math.max(0, Number(item.totalQuestions || 0) - Number(item.submittedQuestions || 0))
    if (pending > 0) map.set(Number(item.articleId), pending)
  }
  return map
})

const pendingBySubject = computed(() => {
  const map = new Map()
  for (const [articleId, pending] of pendingByArticle.value) {
    const article = blogStore.blogData.find(item => Number(item.id) === articleId)
    if (!article) continue
    map.set(article.series, (map.get(article.series) || 0) + pending)
  }
  for (const subject of subjects.value) {
    if (!map.has(subject.name)) map.set(subject.name, 0)
  }
  return map
})

const totalPending = computed(() => Array.from(pendingByArticle.value.values()).reduce((a, b) => a + b, 0))

const todoList = computed(() => {
  const items = []
  for (const subject of subjects.value) {
    const articles = blogStore.blogData.filter(item =>
      item.series === subject.name && pendingByArticle.value.has(Number(item.id)))
    for (const article of articles) {
      items.push({
        articleId: article.id,
        title: article.title,
        subject: article.series,
        pending: pendingByArticle.value.get(Number(article.id)),
        date: article.date
      })
    }
  }
  return items
    .sort((left, right) => String(right.date).localeCompare(String(left.date)))
    .slice(0, 4)
    .map((item, index) => ({ ...item, index: String(index + 1).padStart(2, '0') }))
})

const recentArticles = computed(() => {
  const permissionIds = authStore.getPermissionIds().map(Number).filter(Number.isFinite)
  return blogStore.blogData
    .filter(item => permissionIds.includes(Number(item.id)))
    .slice()
    .sort((left, right) => String(right.date).localeCompare(String(left.date)))
    .slice(0, 3)
})

const streak = computed(() => {
  if (!checkinStore.dates.length) return 0
  const checked = new Set(checkinStore.dates)
  const cursor = new Date()
  const todayKey = formatDateKey(cursor)
  if (!checked.has(todayKey)) cursor.setDate(cursor.getDate() - 1)
  let count = 0
  while (count < 366) {
    const key = formatDateKey(cursor)
    if (!checked.has(key)) break
    count += 1
    cursor.setDate(cursor.getDate() - 1)
  }
  return count
})

const levelInfo = computed(() => levelFromPoints(tasksStore.lifetimePoints))
const nextMilestoneGap = computed(() => {
  const next = (Math.floor(tasksStore.points / 200) + 1) * 200
  return Math.max(0, next - tasksStore.points)
})

const todayLabel = computed(() => new Intl.DateTimeFormat('zh-CN', {
  month: 'long', day: 'numeric', weekday: 'long'
}).format(new Date()))

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '深夜好'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const galleryCards = computed(() => {
  const cards = []
  for (const set of CARD_SETS) {
    cards.push({ name: set.rare.name, img: asset(set.rare.img), rare: true })
    cards.push({ name: set.commons[0].name, img: asset(set.commons[0].img), rare: false })
    cards.push({ name: set.commons[2].name, img: asset(set.commons[2].img), rare: false })
  }
  return cards
})

const ownedCards = computed(() => tasksStore.collection
  .map(row => {
    const card = findCard(row.set_key, row.card_key)
    if (!card) return null
    return { name: card.name, img: asset(card.img), rare: row.rarity === 'rare' }
  })
  .filter(Boolean)
  .slice(0, 5))

const features = [
  { title: '分级讲义', desc: '按学科与章节组织的内容目录，找到下一课。', meta: 'CONTENT', icon: 'fas fa-layer-group', tone: 'butter' },
  { title: '错题批改', desc: '收题、归因、掌握，一眼看清薄弱点。', meta: 'REVIEW', icon: 'fas fa-book-medical', tone: 'coral' },
  { title: '沉浸训练', desc: '重做、自判、写回，直到真正掌握。', meta: 'TRAINING', icon: 'fas fa-dumbbell', tone: 'lime' },
  { title: '收藏卡池', desc: '积分里程碑开出 5 套 35 张卡。', meta: 'REWARDS', icon: 'fas fa-gift', tone: 'sky' }
]

const SUBJECT_META = {
  化学: { key: 'chem', icon: 'fa-flask', cover: 'subjects/chem-bg.png' },
  英语: { key: 'en', icon: 'fa-language', cover: 'subjects/en-bg.png' },
  初中英语: { key: 'en-junior', icon: 'fa-graduation-cap', cover: 'subjects/junior-en-bg.png' }
}

function subjectMeta(name) {
  const meta = SUBJECT_META[name] || { key: 'default', icon: 'fa-book', cover: 'subjects/chem-bg.png' }
  return { ...meta, cover: asset(meta.cover) }
}

function asset(path) {
  return `${base}${path.replace(/^\//, '')}`
}

function formatDateKey(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

async function loadPublicData() {
  try {
    if (!blogStore.blogData.length) await blogStore.loadArticleData()
  } catch {
    /* 保持默认统计 */
  }
}

async function loadDashboardData() {
  await loadPublicData()
  try {
    await wrongQuestionsStore.fetchQuestions(wrongQuestionsStore.getStudentId(authStore.currentUser))
  } catch { /* 忽略，入口徽章为 0 */ }
  try {
    await tasksStore.load(authStore.currentUser)
  } catch { /* 忽略 */ }
  try {
    await checkinStore.loadMonth()
  } catch { /* 忽略 */ }
  await loadProgress()
}

async function loadProgress() {
  try {
    progress.value = await learningGateway.getLearnerProgress()
  } catch {
    progress.value = []
  }
}

async function handleLogin() {
  error.value = ''
  const trimmed = username.value.trim()
  if (!trimmed) {
    error.value = '请输入用户名'
    await focusInput()
    return
  }
  if (!password.value) {
    error.value = '请输入密码'
    return
  }
  submitting.value = true
  try {
    await authStore.login(trimmed, password.value)
    password.value = ''
    useCheckinStore().openAfterLogin()
    await loadDashboardData()
    const redirect = router.currentRoute.value.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/')) {
      await router.push(redirect)
    }
  } catch (err) {
    error.value = authErrorMessage(err, '登录失败，请重试')
    await focusInput()
  } finally {
    submitting.value = false
  }
}

async function handleRegister() {
  error.value = ''
  const trimmed = username.value.trim()
  if (!trimmed) {
    error.value = '请输入用户名'
    await focusInput()
    return
  }
  if (password.value.length < 6 || password.value.length > 128) {
    error.value = '密码长度须为 6～128 个字符'
    return
  }
  if (password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  submitting.value = true
  try {
    await authStore.register(trimmed, password.value)
    password.value = ''
    confirmPassword.value = ''
    await loadDashboardData()
    const redirect = router.currentRoute.value.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/')) {
      await router.push(redirect)
    }
  } catch (err) {
    error.value = err.code === 'username_conflict'
      ? '该用户名已被注册，请换一个'
      : authErrorMessage(err, '注册失败，请重试')
  } finally {
    submitting.value = false
  }
}

/** 把网络层错误翻译成可操作的提示，避免只看到 "Failed to fetch"。 */
function authErrorMessage(err, fallback) {
  const message = err?.message || ''
  if (err?.name === 'TypeError' || /failed to fetch|networkerror|load failed|fetch failed/i.test(message)) {
    return '无法连接后端服务：请先启动 backend（默认 http://127.0.0.1:8080），或检查 VITE_API_BASE_URL / Vite 代理配置'
  }
  return message || fallback
}

async function focusInput() {
  await nextTick()
  usernameInput.value?.focus()
}

function switchMode(next) {
  mode.value = next
  error.value = ''
  if (next === 'register') confirmPassword.value = ''
}

function focusAuth(nextMode = 'login') {
  mode.value = nextMode
  error.value = ''
  nextTick(() => {
    authRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    usernameInput.value?.focus({ preventScroll: true })
  })
}

function scrollToId(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function openCheckin() {
  if (checkinStore.todayCheckedIn) return
  checkinStore.openModal()
}

function continueLearning() {
  if (todoList.value.length) {
    router.push(`/blog/${todoList.value[0].articleId}`)
    return
  }
  router.push('/tasks')
}

function onVisualMove(event) {
  const el = visualRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const x = (event.clientX - rect.left) / rect.width - 0.5
  const y = (event.clientY - rect.top) / rect.height - 0.5
  el.style.setProperty('--px', x.toFixed(3))
  el.style.setProperty('--py', y.toFixed(3))
}

function resetVisual() {
  const el = visualRef.value
  if (!el) return
  el.style.setProperty('--px', '0')
  el.style.setProperty('--py', '0')
}

watch(() => authStore.isLoggedIn, async (loggedIn) => {
  if (loggedIn) {
    await loadDashboardData()
  } else {
    mode.value = 'login'
    progress.value = []
  }
})

watch(() => uiStore.authIntentNonce, () => {
  const intent = uiStore.consumeAuthIntent()
  if (intent && !authStore.isLoggedIn) focusAuth(intent)
})

onMounted(async () => {
  await loadPublicData()
  if (authStore.isLoggedIn) await loadDashboardData()
  if (visualRef.value && window.matchMedia('(pointer: fine)').matches) {
    visualRef.value.addEventListener('mousemove', onVisualMove)
    visualRef.value.addEventListener('mouseleave', resetVisual)
  }
})

defineExpose({
  focusUsername: () => focusInput(),
  switchToRegister: () => switchMode('register')
})
</script>

<style scoped>
.home {
  width: 100%;
  max-width: var(--page-max);
  margin: 0 auto;
  padding: 30px 28px 96px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid var(--ink);
  flex-shrink: 0;
}

.dot--vermilion { background: var(--vermilion); }
.dot--cobalt { background: var(--cobalt); }
.dot--lime { background: var(--lime); }

/* ============================ Hero ============================ */
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(0, 0.98fr);
  gap: 44px;
  align-items: center;
  padding: 40px 0 30px;
}

.hero__copy {
  animation: fade-up 0.75s var(--ease-out) both;
}

.hero__title {
  margin: 18px 0 0;
  font-size: clamp(2.5rem, 5vw, 4.1rem);
  line-height: 1.1;
  letter-spacing: -0.035em;
  font-weight: 800;
}

.hero__title .text-gradient {
  display: inline-block;
  padding: 0 0.06em;
}

.hero__sub {
  margin-top: 18px;
  max-width: 46ch;
  font-size: 1.02rem;
  color: var(--text-2);
}

.hero__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 28px;
}

.hero__chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 20px;
}

.hero__stats {
  display: flex;
  flex-wrap: wrap;
  margin-top: 32px;
  border-top: 2px solid var(--ink);
  padding-top: 18px;
}

.hero__stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-right: 26px;
  margin-right: 26px;
  border-right: 1.5px solid var(--line-strong);
}

.hero__stat:last-child {
  border-right: 0;
  margin-right: 0;
  padding-right: 0;
}

.hero__stat strong {
  font-family: var(--font-display);
  font-size: 1.6rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--ink);
}

.hero__stat span {
  font-size: 0.72rem;
  color: var(--text-3);
  font-weight: 600;
}

.hero__stat.is-accent strong {
  color: var(--vermilion);
}

.hero__visual {
  position: relative;
  min-height: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fade-up 0.9s var(--ease-out) 0.06s both;
}

.hero__shape {
  position: absolute;
  border: 2px solid var(--ink);
}

.hero__shape--circle {
  width: 330px;
  height: 330px;
  border-radius: 50%;
  background: var(--butter);
  top: 8%;
  right: 4%;
}

.hero__shape--grid {
  width: 150px;
  height: 150px;
  left: 2%;
  bottom: 8%;
  background-image: radial-gradient(var(--cobalt) 2px, transparent 2.4px);
  background-size: 13px 13px;
  border: 0;
  opacity: 0.55;
}

.hero__shape--strip {
  width: 190px;
  height: 18px;
  left: 6%;
  top: 12%;
  background: repeating-linear-gradient(90deg, var(--vermilion) 0 16px, transparent 16px 28px);
  border: 0;
  transform: rotate(-9deg);
}

.collage {
  position: relative;
  width: min(430px, 94%);
  height: 430px;
  transform: perspective(1000px) rotateY(calc(var(--px, 0) * 10deg)) rotateX(calc(var(--py, 0) * -8deg));
  transition: transform 0.18s ease-out;
  transform-style: preserve-3d;
}

.polaroid {
  position: absolute;
  width: 58%;
  margin: 0 auto;
  padding: 12px 12px 44px;
  background: var(--paper-1);
  border: 2px solid var(--ink);
  box-shadow: var(--shadow-card);
  border-radius: 4px;
}

.polaroid img {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  border: 1.5px solid var(--ink);
}

.polaroid figcaption {
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 12px;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--ink);
  text-align: center;
  letter-spacing: 0.02em;
}

.polaroid--back {
  left: -6%;
  top: 8%;
  transform: rotate(-13deg);
}

.polaroid--mid {
  right: -6%;
  top: 4%;
  transform: rotate(11deg);
}

.polaroid--front {
  left: 50%;
  top: 12%;
  transform: translateX(-50%) rotate(-2deg);
  z-index: 2;
}

.tape {
  position: absolute;
  width: 96px;
  height: 26px;
  background: rgba(201, 239, 74, 0.85);
  border: 1.5px solid var(--ink);
  z-index: 4;
  opacity: 0.92;
}

.tape--a {
  left: 19%;
  top: 6%;
  transform: rotate(-24deg);
}

.tape--b {
  right: 16%;
  bottom: 17%;
  transform: rotate(18deg);
  background: rgba(255, 197, 61, 0.9);
}

.sticker {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 13px;
  border-radius: var(--r-full);
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  box-shadow: 2px 2px 0 var(--ink);
  font-size: 0.76rem;
  font-weight: 800;
  color: var(--ink);
  z-index: 5;
  animation: float-y 5s ease-in-out infinite;
}

.sticker i {
  color: var(--vermilion);
}

.sticker--1 {
  top: 2%;
  right: -2%;
  --float-rotate: 4deg;
  transform: rotate(4deg);
}

.sticker--2 {
  left: -4%;
  top: 44%;
  --float-rotate: -5deg;
  transform: rotate(-5deg);
  animation-delay: -1.4s;
}

.sticker--2 i {
  color: var(--cobalt);
}

.sticker--3 {
  bottom: 2%;
  right: 4%;
  --float-rotate: 3deg;
  transform: rotate(3deg);
  animation-delay: -2.8s;
}

.sticker--3 i {
  color: var(--teal);
}

/* ============================ Auth ============================ */
.auth-section {
  margin-top: 44px;
  scroll-margin-top: 96px;
}

.auth {
  scroll-margin-top: 96px;
}

.auth__panel {
  display: grid;
  grid-template-columns: minmax(0, 0.88fr) minmax(0, 1.12fr);
  overflow: hidden;
}

.auth__aside {
  position: relative;
  padding: 34px 32px;
  background: var(--butter);
  border-right: 2px solid var(--ink);
  display: flex;
  flex-direction: column;
}

.auth__brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.auth__brand div {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.auth__brand strong {
  font-family: var(--font-display);
  letter-spacing: 0.14em;
  font-size: 0.92rem;
  color: var(--ink);
}

.auth__brand span {
  font-size: 0.72rem;
  color: var(--text-2);
}

.auth__headline {
  margin-top: 30px;
  font-size: clamp(1.7rem, 2.7vw, 2.3rem);
  line-height: 1.14;
  letter-spacing: -0.03em;
  color: var(--ink);
}

.auth__desc {
  margin-top: 10px;
  max-width: 30ch;
  font-size: 0.86rem;
  color: var(--text-2);
}

.auth__perks {
  display: flex;
  flex-direction: column;
  gap: 13px;
  margin-top: 24px;
}

.auth__perks li {
  display: flex;
  align-items: center;
  gap: 11px;
}

.perk-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  font-size: 0.84rem;
  color: var(--ink);
  flex-shrink: 0;
  box-shadow: 2px 2px 0 var(--ink);
}

.perk-dot--cobalt { background: var(--cobalt); color: #fff; }
.perk-dot--vermilion { background: var(--vermilion); color: #fff; }
.perk-dot--lime { background: var(--lime); color: var(--ink); }

.auth__perks div {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.auth__perks strong {
  font-size: 0.86rem;
  color: var(--ink);
}

.auth__perks span {
  font-size: 0.74rem;
  color: var(--text-2);
}

.auth__mini-cards {
  display: flex;
  gap: 9px;
  margin-top: auto;
  padding-top: 26px;
}

.auth__mini-cards img {
  width: 54px;
  height: 70px;
  object-fit: cover;
  border-radius: 3px;
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  padding: 3px;
  box-shadow: 2px 2px 0 var(--ink);
  transform: rotate(-4deg);
  transition: transform var(--dur) var(--ease-spring);
}

.auth__mini-cards img:nth-child(2) {
  transform: rotate(3deg) translateY(-6px);
}

.auth__mini-cards img:nth-child(3) {
  transform: rotate(-1deg);
}

.auth__mini-cards img:hover {
  transform: translateY(-6px) rotate(0);
}

.auth__pane {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 34px 38px;
  background: var(--paper-1);
}

.auth__tabs {
  align-self: flex-start;
}

.auth__pane-title {
  margin-top: 20px;
  font-family: var(--font-display);
  font-size: 1.3rem;
  font-weight: 800;
  color: var(--ink);
}

.auth__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 16px;
}

.auth__form .input {
  height: 50px;
}

.auth__error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--r-sm);
  border: 1.5px solid var(--vermilion);
  background: rgba(237, 75, 42, 0.1);
  color: #a82b12;
  font-size: 0.82rem;
  font-weight: 600;
}

.auth__submit {
  margin-top: 2px;
}

.auth__hint {
  text-align: center;
  font-size: 0.78rem;
  color: var(--text-3);
}

.auth__link {
  color: var(--cobalt);
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.auth__link:hover {
  color: var(--vermilion);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ============================ Features ============================ */
.features {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-top: 28px;
}

.feature {
  position: relative;
  padding: 22px 20px 20px;
  height: 100%;
  border: 2px solid var(--ink);
  border-radius: var(--r-md);
  box-shadow: 4px 4px 0 var(--ink);
  display: flex;
  flex-direction: column;
  transition: transform var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
}

.feature:hover {
  transform: translate(-2px, -2px);
  box-shadow: 6px 6px 0 var(--ink);
}

.feature--butter { background: var(--butter); }
.feature--coral { background: var(--coral); }
.feature--lime { background: var(--lime); }
.feature--sky { background: var(--sky); }

.feature__num {
  position: absolute;
  top: 16px;
  right: 18px;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--ink);
  opacity: 0.55;
}

.feature__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  color: var(--ink);
  font-size: 0.98rem;
}

.feature__title {
  margin-top: 16px;
  font-size: 1.02rem;
  color: var(--ink);
}

.feature__desc {
  margin-top: 6px;
  font-size: 0.82rem;
  color: rgba(28, 26, 20, 0.78);
}

.feature__meta {
  margin-top: 16px;
  font-size: 0.6rem;
  letter-spacing: 0.18em;
  color: rgba(28, 26, 20, 0.5);
}

/* ============================ Gallery（ink 反白区） ============================ */
.cards-section {
  margin-top: 56px;
  padding: 38px 34px 34px;
  border-radius: var(--r-lg);
  background: var(--ink);
  color: var(--paper-1);
  border: 2px solid var(--ink);
  box-shadow: var(--shadow-card);
  scroll-margin-top: 96px;
}

.eyebrow--light {
  color: rgba(255, 253, 246, 0.6);
}

.eyebrow--light::before {
  background: var(--lime);
  border-color: var(--paper-1);
}

.cards-section__title {
  margin-top: 10px;
  font-size: clamp(1.5rem, 2.6vw, 2rem);
  color: var(--paper-1);
  letter-spacing: -0.02em;
}

.cards-section .marker {
  display: inline-block;
  padding: 0 0.08em;
  color: var(--ink);
  background: var(--lime);
  border-radius: 3px;
}

.cards-section .section-head {
  margin-bottom: 24px;
}

.cards-marquee {
  margin: 0 -10px;
  padding: 10px;
}

.cards-marquee .marquee__track {
  gap: 18px;
}

.gallery-card {
  flex: 0 0 auto;
  width: 170px;
  padding: 8px 8px 10px;
  background: var(--paper-1);
  border: 1.5px solid var(--ink);
  border-radius: 3px;
  box-shadow: 3px 3px 0 rgba(0, 0, 0, 0.5);
  transition: transform var(--dur) var(--ease-spring);
}

.gallery-card:nth-child(3n + 1) { transform: rotate(-1.6deg); }
.gallery-card:nth-child(3n + 2) { transform: rotate(1.2deg); }

.gallery-card:hover {
  transform: translateY(-6px) rotate(0);
}

.gallery-card img {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  border: 1.5px solid var(--ink);
}

.gallery-card figcaption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  padding-top: 7px;
}

.gallery-card__name {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gallery-card__tag {
  font-family: var(--font-mono);
  font-size: 0.56rem;
  padding: 1px 5px;
  border: 1px solid var(--ink);
  border-radius: var(--r-full);
  color: var(--ink);
  flex-shrink: 0;
}

/* ============================ Dashboard ============================ */
.dash-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(260px, 0.6fr);
  gap: 30px;
  align-items: center;
  padding: 32px 34px;
  margin-top: 6px;
  overflow: hidden;
}

.dash-hero__title {
  margin-top: 14px;
  font-size: clamp(1.9rem, 3.4vw, 2.7rem);
  letter-spacing: -0.03em;
}

.dash-hero__title .text-gradient {
  padding: 0 0.06em;
}

.dash-hero__sub {
  margin-top: 10px;
  font-size: 0.9rem;
  color: var(--text-2);
}

.dash-hero__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 22px;
}

.dash-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}

.level-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.level-card__card {
  position: relative;
  width: 100%;
  max-width: 260px;
  padding: 20px;
  border-radius: 14px;
  border: 2px solid var(--ink);
  background: var(--vermilion);
  color: var(--paper-1);
  box-shadow: var(--shadow-card);
  transform: rotate(-2deg);
}

.level-card__label {
  font-size: 0.58rem;
  letter-spacing: 0.24em;
  color: rgba(255, 253, 246, 0.75);
}

.level-card__name {
  display: block;
  margin-top: 12px;
  font-family: var(--font-display);
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--paper-1);
}

.level-card__lvrow {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 4px;
}

.level-card__level {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--paper-1);
}

.level-card__title {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--butter);
  letter-spacing: 0.04em;
}

.level-card__points {
  display: block;
  margin-top: 10px;
  font-family: var(--font-display);
  font-size: 2.6rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.05em;
  color: var(--paper-1);
}

.level-card__points small {
  margin-left: 4px;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 500;
  letter-spacing: 0.08em;
  color: rgba(255, 253, 246, 0.7);
}

.level-card__bar {
  height: 8px;
  margin-top: 14px;
  border-radius: var(--r-full);
  background: rgba(255, 253, 246, 0.25);
  border: 1px solid var(--ink);
  overflow: hidden;
}

.level-card__bar span {
  display: block;
  height: 100%;
  background: var(--lime);
  transition: width var(--dur-slow) var(--ease-out);
}

.level-card__foot {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: rgba(255, 253, 246, 0.8);
}

.level-card__link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--ink);
  text-decoration: underline;
  text-decoration-thickness: 2px;
  text-underline-offset: 4px;
  text-decoration-color: var(--vermilion);
}

.dash-stats {
  margin-top: 18px;
}

/* ============================ Subjects ============================ */
.subject-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 18px;
}

.subject-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border-radius: var(--r-md);
  overflow: hidden;
  border: 2px solid var(--ink);
  background: var(--paper-1);
  box-shadow: var(--shadow-card);
  transition: transform var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
  animation: fade-up 0.6s var(--ease-out) both;
  animation-delay: calc(var(--i, 0) * 0.06s);
}

.subject-card:hover {
  transform: translate(-2px, -2px);
  box-shadow: 7px 7px 0 var(--ink);
}

.subject-card__cover {
  width: 100%;
  height: 170px;
  object-fit: cover;
  border-bottom: 2px solid var(--ink);
}

.subject-card__band {
  display: block;
  height: 12px;
  border-bottom: 2px solid var(--ink);
}

.subject-card--chem .subject-card__band { background: var(--sky); }
.subject-card--en .subject-card__band { background: var(--vermilion); }
.subject-card--en-junior .subject-card__band { background: var(--cobalt); }
.subject-card--default .subject-card__band { background: var(--lime); }

.subject-card__body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 16px 18px 18px;
  background: var(--paper-1);
}

.subject-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  margin-top: -36px;
  border-radius: 9px;
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  font-size: 0.92rem;
  color: var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
}

.subject-card__name {
  margin-top: 10px;
  font-size: 1.4rem;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.subject-card__meta {
  margin-top: 2px;
  font-size: 0.76rem;
  color: var(--text-3);
  font-weight: 600;
}

.subject-card__pending {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 10px;
  padding: 3px 9px;
  border-radius: var(--r-full);
  border: 1.5px solid var(--ink);
  background: var(--butter);
  color: var(--ink);
  font-size: 0.68rem;
  font-weight: 700;
}

.subject-card__pending.is-clear {
  background: var(--lime);
}

.subject-card__go {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 13px;
  font-size: 0.78rem;
  font-weight: 800;
  color: var(--ink);
  transition: gap var(--dur);
}

.subject-card:hover .subject-card__go {
  gap: 10px;
}

/* ============================ Todo / quick ============================ */
.dash-cols {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: 18px;
  margin-top: 46px;
  align-items: start;
}

.dash-col {
  min-width: 0;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 12px;
  border-radius: 10px;
  border: 1.5px solid transparent;
  transition: background var(--dur-fast), border-color var(--dur-fast), transform var(--dur-fast);
}

.todo-item:hover {
  background: var(--paper-0);
  border-color: var(--ink);
  transform: translateX(2px);
}

.todo-item__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1.5px solid var(--ink);
  background: var(--paper-2);
  font-family: var(--font-mono);
  font-size: 0.66rem;
  color: var(--ink);
  flex-shrink: 0;
}

.todo-item__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.todo-item__title {
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-item__meta {
  font-size: 0.72rem;
  color: var(--text-3);
}

.todo-item__go {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1.5px solid var(--ink);
  background: var(--lime);
  color: var(--ink);
  font-size: 0.66rem;
  transition: transform var(--dur-fast);
}

.todo-item:hover .todo-item__go {
  transform: translateX(2px);
}

.empty--inline {
  padding: 30px 18px;
  border: 0;
  background: transparent;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 12px;
  border-radius: 10px;
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  transition: transform var(--dur-fast), box-shadow var(--dur-fast);
}

.quick-item:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 var(--ink);
}

.quick-item__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  border: 1.5px solid var(--ink);
  font-size: 0.84rem;
  flex-shrink: 0;
  color: var(--ink);
}

.quick-item--coral .quick-item__icon { background: var(--coral); }
.quick-item--lime .quick-item__icon { background: var(--lime); }
.quick-item--amber .quick-item__icon { background: var(--butter); }
.quick-item--sky .quick-item__icon { background: var(--sky); }

.quick-item__text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.quick-item__text strong {
  font-size: 0.82rem;
  color: var(--text-1);
}

.quick-item__text small {
  font-size: 0.68rem;
  color: var(--text-3);
}

.quick-cards {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1.5px dashed var(--line-strong);
}

.quick-cards__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 0.78rem;
  font-weight: 800;
  color: var(--text-1);
}

.quick-cards__head a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.7rem;
  color: var(--cobalt);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.quick-cards__row {
  display: flex;
  gap: 8px;
}

.mini-card {
  width: 58px;
  height: 76px;
  padding: 3px;
  border-radius: 3px;
  border: 1.5px solid var(--ink);
  background: var(--paper-1);
  box-shadow: 2px 2px 0 var(--ink);
  transition: transform var(--dur) var(--ease-spring);
}

.mini-card:hover {
  transform: translateY(-4px) rotate(-2deg);
}

.mini-card.is-rare {
  border-color: var(--vermilion);
}

.mini-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mini-card--empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--text-4);
  font-size: 0.58rem;
}

/* ============================ Recent ============================ */
.recent-section {
  margin-top: 46px;
}

.recent-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.recent-card {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: transform var(--dur-fast), box-shadow var(--dur-fast);
}

.recent-card:hover {
  transform: translate(-2px, -2px);
  box-shadow: 7px 7px 0 var(--ink);
}

.recent-card__subject {
  padding: 8px 18px;
  border-bottom: 1.5px solid var(--ink);
  background: var(--butter);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  color: var(--ink);
}

.recent-card:nth-child(2) .recent-card__subject { background: var(--lime); }
.recent-card:nth-child(3) .recent-card__subject { background: var(--sky); }

.recent-card__title {
  padding: 16px 18px 0;
  font-size: 0.98rem;
  line-height: 1.4;
  color: var(--text-1);
}

.recent-card__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 18px 18px;
  margin-top: auto;
  font-size: 0.7rem;
  color: var(--text-3);
}

.recent-card__arrow {
  margin-left: auto;
  transition: transform var(--dur-fast);
}

.recent-card:hover .recent-card__arrow {
  transform: translateX(4px);
  color: var(--vermilion);
}

/* ============================ Responsive ============================ */
@media (max-width: 1100px) {
  .hero {
    grid-template-columns: minmax(0, 1fr);
    min-height: 0;
    padding-top: 26px;
  }

  .hero__visual {
    order: -1;
    min-height: 380px;
  }

  .collage {
    height: 350px;
  }

  .features {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dash-hero {
    grid-template-columns: minmax(0, 1fr);
  }

  .level-card {
    align-items: flex-start;
  }

  .level-card__card {
    max-width: 300px;
  }

  .dash-cols {
    grid-template-columns: minmax(0, 1fr);
  }

  .recent-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 860px) {
  .auth__panel {
    grid-template-columns: minmax(0, 1fr);
  }

  .auth__aside {
    border-right: 0;
    border-bottom: 2px solid var(--ink);
    padding: 24px 22px;
  }

  .auth__perks,
  .auth__mini-cards {
    display: none;
  }

  .auth__headline {
    margin-top: 16px;
  }

  .auth__pane {
    padding: 24px 22px 28px;
  }
}

@media (max-width: 768px) {
  .home {
    padding: 14px 16px 110px;
  }

  .hero__title {
    font-size: clamp(2.05rem, 9vw, 2.7rem);
  }

  .hero__sub {
    font-size: 0.92rem;
  }

  .hero__visual {
    min-height: 320px;
  }

  .collage {
    height: 300px;
  }

  .hero__shape--circle {
    width: 230px;
    height: 230px;
  }

  .sticker {
    font-size: 0.68rem;
    padding: 6px 10px;
  }

  .hero__stat {
    padding-right: 14px;
    margin-right: 14px;
  }

  .hero__stat strong {
    font-size: 1.2rem;
  }

  .features {
    grid-template-columns: minmax(0, 1fr);
  }

  .cards-section {
    margin-top: 40px;
    padding: 24px 18px;
  }

  .cards-section .section-head {
    align-items: flex-start;
  }

  .gallery-card {
    width: 138px;
  }

  .dash-hero {
    padding: 22px 18px;
  }

  .dash-hero__title {
    font-size: 1.85rem;
  }

  .subject-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .quick-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
