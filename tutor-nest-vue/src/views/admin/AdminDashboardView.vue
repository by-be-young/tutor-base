<!-- src/views/admin/AdminDashboardView.vue -->
<template>
  <div class="admin-page">
    <!-- 欢迎语 + 页面级快捷操作 -->
    <section class="panel dash-head">
      <div class="dash-head__copy">
        <span class="eyebrow">{{ todayLabel }} / 管理工作台</span>
        <h2 class="dash-head__title">{{ authStore.username }}，<span class="text-gradient">今天先看谁</span></h2>
        <p class="dash-head__sub">学员动态、待办批阅与课件认领一屏掌握，点击任意学员即可进入管理。</p>
      </div>

      <div class="dash-quick">
        <router-link to="/admin/reviews" class="btn btn--primary btn--sm">
          <i class="fas fa-pen-nib" aria-hidden="true"></i> 去批阅
        </router-link>
        <router-link to="/admin/content" class="btn btn--soft btn--sm">
          <i class="fas fa-book-open" aria-hidden="true"></i> 去课件认领
        </router-link>
        <router-link to="/admin/learners" class="btn btn--ghost btn--sm">
          <i class="fas fa-users" aria-hidden="true"></i> 去学员列表
        </router-link>
      </div>
    </section>

    <!-- 概览指标 -->
    <section class="panel panel--pad dash-overview" aria-labelledby="dash-overview-title">
      <div class="section-head">
        <div>
          <span class="eyebrow">OVERVIEW / 管理概览</span>
          <h2 id="dash-overview-title" class="section-title">近 7 天概况</h2>
        </div>
        <button v-if="overviewError" type="button" class="btn btn--ghost btn--sm"
          :disabled="overviewLoading" @click="loadOverview">
          <i class="fas fa-rotate-right" aria-hidden="true"></i> 重试
        </button>
      </div>

      <div v-if="overviewLoading" class="stat-grid dash-metrics" aria-hidden="true">
        <div v-for="index in 5" :key="index" class="stat-card dash-skeleton">
          <span class="skeleton dash-skeleton__value"></span>
          <span class="skeleton dash-skeleton__label"></span>
        </div>
      </div>

      <template v-else>
        <p v-if="overviewError" class="dash-error" role="alert">
          <i class="fas fa-triangle-exclamation" aria-hidden="true"></i>
          <span>{{ overviewError }}</span>
        </p>

        <ul class="stat-grid dash-metrics" aria-label="管理概览指标">
          <li v-for="metric in metrics" :key="metric.key" class="stat-card" :class="metric.tone">
            <span class="stat-value">{{ metric.value }}</span>
            <span class="stat-label">{{ metric.label }}</span>
            <span class="stat-sub">{{ metric.hint }}</span>
          </li>
        </ul>

        <p v-if="!overviewError && overview" class="dash-overview__note">
          学员 {{ overview.learners }} 人 · 已认领 {{ overview.claimedLearners }} · 未认领 {{ overview.unclaimedLearners }}
          · 可见课件 {{ overview.courseware.visible }} 篇
        </p>
      </template>
    </section>

    <!-- 30 天趋势 -->
    <section class="panel panel--pad trend-panel" aria-labelledby="dash-trend-title">
      <div class="section-head">
        <div>
          <span class="eyebrow">TREND / 30 天趋势</span>
          <h2 id="dash-trend-title" class="section-title">提交与批阅走势</h2>
        </div>
        <div class="trend-panel__tools">
          <ul class="trend-legend" aria-label="趋势图例">
            <li class="trend-legend__item">
              <span class="trend-legend__swatch is-submissions" aria-hidden="true"></span>提交
            </li>
            <li class="trend-legend__item">
              <span class="trend-legend__swatch is-reviews" aria-hidden="true"></span>批阅
            </li>
          </ul>
          <button v-if="activityError" type="button" class="btn btn--ghost btn--sm"
            :disabled="activityLoading" @click="loadActivity">
            <i class="fas fa-rotate-right" aria-hidden="true"></i> 重试
          </button>
        </div>
      </div>

      <div v-if="activityLoading" class="trend-skeleton" aria-hidden="true">
        <span class="skeleton trend-skeleton__block"></span>
      </div>

      <p v-else-if="activityError" class="dash-error" role="alert">
        <i class="fas fa-triangle-exclamation" aria-hidden="true"></i>
        <span>{{ activityError }}</span>
      </p>

      <EmptyState v-else-if="activityPoints.length === 0" icon="fas fa-chart-line"
        title="还没有趋势数据" text="学员提交或批阅后，这里会出现近 30 天的走势。" />

      <div v-else class="trend-chart">
        <svg class="trend-chart__svg" :viewBox="`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`" role="img"
          :aria-label="trendAriaLabel">
          <g class="trend-chart__grid" aria-hidden="true">
            <line v-for="(line, index) in chartGrid" :key="`grid-${index}`" :x1="CHART_PADDING.left"
              :x2="CHART_WIDTH - CHART_PADDING.right" :y1="line.y" :y2="line.y" />
          </g>

          <g class="trend-chart__axis" aria-hidden="true">
            <text v-for="(line, index) in chartGrid" :key="`axis-${index}`" :x="CHART_PADDING.left - 8"
              :y="line.y + 4" text-anchor="end">{{ line.label }}</text>
            <text v-for="tick in chartTicks" :key="`tick-${tick.index}`" :x="tick.x"
              :y="CHART_HEIGHT - 8" text-anchor="middle">{{ tick.label }}</text>
          </g>

          <path class="trend-chart__line is-submissions" :d="submissionsPath" aria-hidden="true" />
          <path class="trend-chart__line is-reviews" :d="reviewsPath" aria-hidden="true" />
        </svg>

        <p class="trend-chart__foot">
          <span>近 {{ activityDays }} 天</span>
          <span>提交 {{ activityTotals.submissions }} 次</span>
          <span>批阅 {{ activityTotals.reviews }} 次</span>
        </p>
      </div>
    </section>

    <!-- 学员动态 -->
    <section class="panel panel--pad activity-panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">ACTIVITY / 学员动态</span>
          <h2 class="section-title">最近活跃的学员</h2>
        </div>
        <span class="section-note">按最近动态倒序 · 不含测试学员 · 前 {{ recentStudents.length }} 名</span>
      </div>

      <div v-if="recentStudents.length === 0" class="empty empty--inline">
        <span class="empty__icon"><i class="fas fa-user-plus" aria-hidden="true"></i></span>
        <p class="empty__title">{{ adminStore.students.length ? '暂无普通学员动态' : '还没有学员' }}</p>
        <p class="empty__text">
          {{ adminStore.students.length
            ? '测试学员不计入活跃度统计与学员动态列表，可到学员页查看或维护。'
            : '到「学员」页新增第一位学员，认领后即可配置内容授权与批阅。' }}
        </p>
        <router-link to="/admin/learners" class="btn btn--soft btn--sm">
          {{ adminStore.students.length ? '去学员列表' : '去新增学员' }}
        </router-link>
      </div>

      <ul v-else class="activity-list" role="list" aria-label="学员动态列表">
        <li v-for="student in recentStudents" :key="student.id">
          <button type="button" class="activity-row" :aria-label="`打开 ${student.username} 的管理页面`"
            @click="openStudent(student)">
            <span class="avatar activity-row__avatar" aria-hidden="true">{{ initialOf(student.username) }}</span>

            <span class="activity-row__main">
              <span class="activity-row__name">
                {{ student.username }}
                <span class="activity-row__claim" :class="`is-${claimStateOf(student)}`">
                  {{ claimLabelOf(student) }}
                </span>
              </span>
              <span class="activity-row__meta">
                <i class="far fa-clock" aria-hidden="true"></i>
                {{ formatRelativeActivity(student.lastActivityAt) }}
                <span aria-hidden="true">·</span>
                在学 {{ subjectCountOf(student) }} 门学科
              </span>
            </span>

            <span class="activity-row__week" :aria-label="weekAriaLabel(student)">
              <span v-for="(active, index) in weekBars(student)" :key="index" class="activity-row__bar"
                :class="{ 'is-active': active }" aria-hidden="true"></span>
            </span>

            <span class="activity-row__go"><i class="fas fa-arrow-right" aria-hidden="true"></i></span>
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useAdminStore } from '@/stores/adminStore'
import { useCoursewareStore } from '@/stores/coursewareStore'
import { useAdminWorkspace } from '@/composables/useAdminWorkspace'
import { analyticsGateway } from '@/gateways/analyticsGateway'
import { buildLinePath, formatCompactNumber, maxValue } from '@/utils/analytics'
import { claimState, formatRelativeActivity, isTestStudent, sortStudentsByRecentActivity } from '@/utils/adminStudents'
import { activeSubjects, buildSubjectScope } from '@/utils/adminSubjects'
import EmptyState from '@/components/ui/EmptyState.vue'

const router = useRouter()
const authStore = useAuthStore()
const adminStore = useAdminStore()
const coursewareStore = useCoursewareStore()
const { blogData, accountId, handleSelectStudent, loadWorkspace } = useAdminWorkspace()

const WEEK_DAYS = 7
const DAY_MS = 24 * 60 * 60 * 1000

const CHART_WIDTH = 720
const CHART_HEIGHT = 260
const CHART_PADDING = Object.freeze({ top: 18, right: 18, bottom: 30, left: 44 })
const CHART_GRID_LINES = 4

const overview = ref(null)
const overviewLoading = ref(false)
const overviewError = ref('')
const activityPoints = ref([])
const activityDays = ref(30)
const activityLoading = ref(false)
const activityError = ref('')

const todayLabel = computed(() => new Intl.DateTimeFormat('zh-CN', {
  month: 'long', day: 'numeric', weekday: 'long'
}).format(new Date()))

function analysisErrorMessage(error, subject) {
  const status = error?.status
  if (status === 404) return `${subject}接口暂不可用，后端可能尚未部署该功能，请稍后重试。`
  if (status === 401) return '登录状态已失效，请重新登录后再试。'
  if (status === 403) return '当前账户无权查看数据分析。'
  return `${subject}加载失败，请稍后重试。`
}

async function loadOverview() {
  if (overviewLoading.value) return
  overviewLoading.value = true
  overviewError.value = ''
  try {
    overview.value = await analyticsGateway.getOverview()
  } catch (error) {
    overview.value = null
    overviewError.value = analysisErrorMessage(error, '概览数据')
  } finally {
    overviewLoading.value = false
  }
}

async function loadActivity() {
  if (activityLoading.value) return
  activityLoading.value = true
  activityError.value = ''
  try {
    const response = await analyticsGateway.getActivity(30)
    activityPoints.value = response.points
    activityDays.value = response.days
  } catch (error) {
    activityPoints.value = []
    activityError.value = analysisErrorMessage(error, '趋势数据')
  } finally {
    activityLoading.value = false
  }
}

// 待认领课件继续使用 coursewareStore，其余指标来自 analytics overview。
const coursewareMetric = computed(() => ({
  key: 'courseware',
  label: '待认领课件',
  value: coursewareStore.unclaimedCount ?? 0,
  hint: '来自课件认领面板',
  tone: ''
}))

const overviewMetrics = computed(() => {
  const data = overview.value || {}
  return [
    {
      key: 'mine',
      label: '我的学员',
      value: data.claimedLearners ?? 0,
      hint: `共 ${data.learners ?? 0} 名学员`,
      tone: 'stat-card--cyan'
    },
    {
      key: 'active',
      label: '活跃学员',
      value: data.activeLearners7d ?? 0,
      hint: '近 7 天有动态',
      tone: 'stat-card--lime'
    },
    {
      key: 'pending',
      label: '待批阅',
      value: data.pendingReviews ?? 0,
      hint: '等待批阅的作答',
      tone: 'stat-card--coral'
    },
    {
      key: 'submissions',
      label: '近 7 日提交',
      value: data.submissions7d ?? 0,
      hint: '学员提交次数',
      tone: 'stat-card--amber'
    },
    coursewareMetric.value
  ]
})

// 概览接口失败时仍展示本地课件指标，页面不白屏。
const metrics = computed(() => (overviewError.value ? [coursewareMetric.value] : overviewMetrics.value))

function roundCoordinate(value) {
  return Math.round(value * 100) / 100
}

const chartInnerWidth = CHART_WIDTH - CHART_PADDING.left - CHART_PADDING.right
const chartInnerHeight = CHART_HEIGHT - CHART_PADDING.top - CHART_PADDING.bottom

const chartMax = computed(() =>
  Math.max(1, maxValue(activityPoints.value, ['submissions', 'reviews'])))

const chartGrid = computed(() => {
  const rows = []
  for (let step = 0; step <= CHART_GRID_LINES; step += 1) {
    const ratio = step / CHART_GRID_LINES
    rows.push({
      label: formatCompactNumber(Math.round(chartMax.value * ratio)),
      y: roundCoordinate(CHART_PADDING.top + chartInnerHeight * (1 - ratio))
    })
  }
  return rows
})

function formatTickDate(date) {
  const text = String(date || '')
  return text.length >= 10 ? text.slice(5, 10) : text
}

const chartTicks = computed(() => {
  const points = activityPoints.value
  const count = points.length
  if (count === 0) return []
  const stepX = count > 1 ? chartInnerWidth / (count - 1) : 0
  return points
    .map((point, index) => ({ point, index }))
    .filter(({ index }) => index % 7 === 0)
    .map(({ point, index }) => ({
      index,
      x: roundCoordinate(CHART_PADDING.left + (count === 1 ? chartInnerWidth / 2 : stepX * index)),
      label: formatTickDate(point.date)
    }))
})

const submissionsPath = computed(() =>
  buildLinePath(activityPoints.value, 'submissions', CHART_WIDTH, CHART_HEIGHT, CHART_PADDING))

const reviewsPath = computed(() =>
  buildLinePath(activityPoints.value, 'reviews', CHART_WIDTH, CHART_HEIGHT, CHART_PADDING))

const activityTotals = computed(() => activityPoints.value.reduce((totals, point) => ({
  submissions: totals.submissions + (Number(point.submissions) || 0),
  reviews: totals.reviews + (Number(point.reviews) || 0)
}), { submissions: 0, reviews: 0 }))

const trendAriaLabel = computed(() => (
  `近 ${activityDays.value} 天提交与批阅趋势：提交 ${activityTotals.value.submissions} 次，`
  + `批阅 ${activityTotals.value.reviews} 次。`
))

// 学员动态与 7 天活跃横条只统计普通学员：测试学员不进入活跃度视图。
const recentStudents = computed(() =>
  sortStudentsByRecentActivity(
    adminStore.students.filter(student => !isTestStudent(student))
  ).slice(0, 8))

const allSubjects = computed(() => {
  const subjects = new Set()
  blogData.value.forEach(article => {
    if (article.series) subjects.add(article.series)
  })
  return Array.from(subjects).sort()
})

// 管理端只看自己认领的课件：学员科目数按「认领课件 ∩ 学员授权」计算。
const visibleArticles = computed(() => {
  const ids = coursewareStore.visibleArticleIds
  if (!(ids instanceof Set)) return blogData.value
  return blogData.value.filter(article => ids.has(Number(article.id)))
})

function initialOf(username) {
  return Array.from(String(username || '?'))[0]?.toUpperCase() || '?'
}

function claimStateOf(student) {
  return claimState(student, accountId.value)
}

function claimLabelOf(student) {
  const state = claimStateOf(student)
  if (state === 'mine') return '我的'
  if (state === 'unclaimed') return '未认领'
  return `由 ${student.claimedByUsername || '其他管理员'} 认领`
}

function subjectCountOf(student) {
  const scope = buildSubjectScope({
    subjects: allSubjects.value,
    articles: visibleArticles.value,
    permissions: student.permissions
  })
  return activeSubjects(scope).length
}

/** 近 7 天横条：最早 → 今天，命中 lastActivityAt 当天则点亮。 */
function weekBars(student) {
  const timestamp = student.lastActivityAt ? Date.parse(String(student.lastActivityAt)) : NaN
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const bars = []
  for (let offset = WEEK_DAYS - 1; offset >= 0; offset -= 1) {
    const dayStart = today.getTime() - offset * DAY_MS
    const active = Number.isFinite(timestamp)
      && timestamp >= dayStart
      && timestamp < dayStart + DAY_MS
    bars.push(active)
  }
  return bars
}

function weekAriaLabel(student) {
  const activeDays = weekBars(student).filter(Boolean).length
  return activeDays ? `近 7 天有 ${activeDays} 天活跃` : '近 7 天没有活跃记录'
}

function openStudent(student) {
  handleSelectStudent(student.id)
  router.push('/admin/learners')
}

onMounted(() => {
  loadWorkspace()
  loadOverview()
  loadActivity()
})
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ------------------------------------------------------------------ */
/* 欢迎语 + 页面级快捷操作                                             */
/* ------------------------------------------------------------------ */
.dash-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  padding: 24px 26px;
  border-radius: var(--r-lg);
}

.dash-head__copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.dash-head__title {
  font-size: clamp(1.5rem, 2.6vw, 2.1rem);
}

.dash-head__sub {
  font-size: 0.88rem;
  color: var(--text-2);
}

.dash-quick {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}

/* ------------------------------------------------------------------ */
/* 概览指标                                                            */
/* ------------------------------------------------------------------ */
.dash-overview {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dash-metrics {
  margin-top: 4px;
}

.dash-metrics .stat-value {
  font-variant-numeric: tabular-nums;
}

.dash-overview__note {
  margin-top: 12px;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  color: var(--text-3);
}

.dash-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 106px;
}

.dash-skeleton__value {
  width: 58%;
  height: 30px;
}

.dash-skeleton__label {
  width: 78%;
  height: 12px;
}

.dash-error {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--coral);
  color: var(--ink);
  box-shadow: 3px 3px 0 var(--ink);
  font-size: 0.84rem;
  font-weight: 600;
}

/* ------------------------------------------------------------------ */
/* 30 天趋势                                                          */
/* ------------------------------------------------------------------ */
.trend-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.trend-panel__tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.trend-legend {
  display: flex;
  align-items: center;
  gap: 14px;
}

.trend-legend__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-2);
}

.trend-legend__swatch {
  width: 18px;
  height: 5px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
}

.trend-legend__swatch.is-submissions {
  background: var(--vermilion);
}

.trend-legend__swatch.is-reviews {
  background: var(--cobalt);
}

.trend-skeleton {
  padding: 6px 0;
}

.trend-skeleton__block {
  display: block;
  width: 100%;
  height: 232px;
  border-radius: var(--r-md);
}

.trend-chart {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trend-chart__svg {
  width: 100%;
  height: auto;
}

.trend-chart__grid line {
  stroke: var(--line);
  stroke-width: 1;
  stroke-dasharray: 4 5;
}

.trend-chart__axis text {
  fill: var(--text-3);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.02em;
}

.trend-chart__line {
  fill: none;
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}

.trend-chart__line.is-submissions {
  stroke: var(--vermilion);
}

.trend-chart__line.is-reviews {
  stroke: var(--cobalt);
}

.trend-chart__foot {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  color: var(--text-3);
}

/* ------------------------------------------------------------------ */
/* 学员动态                                                            */
/* ------------------------------------------------------------------ */
.activity-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.activity-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 1.5px solid transparent;
  border-radius: var(--r-sm);
  text-align: left;
  transition: background var(--dur-fast), border-color var(--dur-fast), box-shadow var(--dur-fast);
}

.activity-row:hover,
.activity-row:focus-visible {
  background: var(--paper-2);
  border-color: var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
}

.activity-row__avatar {
  width: 36px;
  height: 36px;
  font-size: 0.82rem;
  background: var(--butter);
  flex-shrink: 0;
}

.activity-row__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1 1 auto;
  min-width: 0;
}

.activity-row__name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--ink);
  min-width: 0;
}

.activity-row__claim {
  flex-shrink: 0;
  padding: 1px 8px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  background: var(--paper-2);
  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.activity-row__claim.is-mine { background: var(--lime); }
.activity-row__claim.is-unclaimed { background: var(--butter); }

.activity-row__meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.activity-row__week {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 26px;
  flex-shrink: 0;
}

.activity-row__bar {
  width: 7px;
  height: 10px;
  border: 1px solid var(--ink);
  border-radius: 2px;
  background: var(--paper-2);
}

.activity-row__bar:nth-child(2n) { height: 14px; }
.activity-row__bar:nth-child(3n) { height: 18px; }
.activity-row__bar:last-child { height: 24px; }

.activity-row__bar.is-active {
  background: var(--vermilion);
}

.activity-row__go {
  flex-shrink: 0;
  font-size: 0.72rem;
  color: var(--text-4);
}

@media (max-width: 1024px) {
  .dash-metrics {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }
}

@media (max-width: 768px) {
  .dash-head {
    padding: 18px;
    gap: 14px;
  }

  .dash-quick {
    width: 100%;
    margin-left: 0;
  }

  .trend-chart__foot {
    gap: 10px;
  }

  .activity-row {
    flex-wrap: wrap;
    gap: 8px;
    padding: 10px;
  }

  .activity-row__main {
    flex-basis: calc(100% - 60px);
  }

  .activity-row__week {
    margin-left: 48px;
  }

  .activity-row__go {
    margin-left: auto;
  }
}
</style>
