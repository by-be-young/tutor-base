<!-- src/components/admin/LearnerDetailDrawer.vue -->
<template>
  <Teleport to="body">
    <div class="drawer-backdrop" @click.self="close">
      <aside ref="panelRef" class="detail-drawer" role="dialog" aria-modal="true"
        :aria-label="`学员详情：${username}`" tabindex="-1">
        <header class="detail-drawer__head">
          <span class="avatar avatar--lg detail-drawer__avatar" aria-hidden="true">{{ initialOf(username) }}</span>
          <div class="detail-drawer__title">
            <span class="eyebrow">LEARNER / 学员详情</span>
            <h2 class="detail-drawer__name">{{ username }}</h2>
            <span class="detail-drawer__meta">
              <span class="chip chip--cyan">{{ claimLabel }}</span>
              <span class="detail-drawer__activity">
                <i class="far fa-clock" aria-hidden="true"></i>
                最近动态 · {{ lastActivityLabel }}
              </span>
            </span>
          </div>
          <button type="button" class="modal__close detail-drawer__close" aria-label="关闭学员详情"
            @click="close">
            <i class="fas fa-xmark" aria-hidden="true"></i>
          </button>
        </header>

        <div class="detail-drawer__body">
          <div v-if="loading" class="detail-skeleton" aria-hidden="true">
            <span v-for="index in 4" :key="index" class="skeleton detail-skeleton__row"></span>
          </div>

          <div v-else-if="errorMessage" class="detail-state" role="alert">
            <p class="detail-error">
              <i class="fas fa-triangle-exclamation" aria-hidden="true"></i>
              <span>{{ errorMessage }}</span>
            </p>
            <button type="button" class="btn btn--soft btn--sm" @click="loadProfile()">
              <i class="fas fa-rotate-right" aria-hidden="true"></i> 重试
            </button>
          </div>

          <template v-else>
            <section class="detail-section" aria-labelledby="detail-points-title">
              <h3 id="detail-points-title" class="detail-section__title">
                <i class="fas fa-star" aria-hidden="true"></i> 积分与等级
              </h3>
              <div class="detail-stats">
                <div class="stat-card">
                  <span class="stat-value">{{ currentPoints }}</span>
                  <span class="stat-label">当前积分</span>
                </div>
                <div class="stat-card stat-card--amber">
                  <span class="stat-value">{{ lifetimePoints }}</span>
                  <span class="stat-label">累计积分</span>
                </div>
                <div class="stat-card detail-level">
                  <span class="chip chip--lime detail-level__chip">
                    {{ levelInfo.level }} 级 · {{ levelInfo.title }}
                  </span>
                  <span class="stat-label">当前等级</span>
                  <span class="stat-sub">距下一级还差 {{ levelInfo.toNext }} 分</span>
                </div>
              </div>
            </section>

            <section class="detail-section" aria-labelledby="detail-checkin-title">
              <h3 id="detail-checkin-title" class="detail-section__title">
                <i class="fas fa-calendar-check" aria-hidden="true"></i> 签到
              </h3>
              <p class="detail-checkin">
                近 30 天签到 <strong>{{ checkInDays }}</strong> 天
              </p>
            </section>

            <section class="detail-section" aria-labelledby="detail-subjects-title">
              <h3 id="detail-subjects-title" class="detail-section__title">
                <i class="fas fa-book-open" aria-hidden="true"></i> 分学科进度
              </h3>
              <p v-if="subjectProgress.length === 0" class="detail-empty">还没有学科进度数据</p>
              <ul v-else class="detail-subjects" aria-label="分学科进度列表">
                <li v-for="row in subjectProgress" :key="row.subject" class="detail-subject">
                  <div class="detail-subject__head">
                    <span class="detail-subject__name">{{ row.subject }}</span>
                    <span class="detail-subject__meta">
                      提交 {{ row.submitted }}/{{ row.total }} · 待批阅 {{ row.pending }}
                    </span>
                  </div>
                  <div class="progress" role="progressbar" :aria-valuenow="row.completion"
                    aria-valuemin="0" aria-valuemax="100" :aria-label="`${row.subject}完成率`">
                    <span class="progress__bar" :style="{ width: `${row.completion}%` }"></span>
                  </div>
                  <span class="detail-subject__percent">{{ row.completion }}% 完成</span>
                </li>
              </ul>
            </section>

            <section class="detail-section" aria-labelledby="detail-activity-title">
              <h3 id="detail-activity-title" class="detail-section__title">
                <i class="fas fa-chart-line" aria-hidden="true"></i> 30 天动态
              </h3>
              <p v-if="activityPoints.length === 0" class="detail-empty">最近 30 天还没有作答记录</p>
              <div v-else class="detail-chart">
                <svg class="detail-chart__svg" :viewBox="`0 0 ${CHART.width} ${CHART.height}`" role="img"
                  :aria-label="`近 30 天提交趋势，共 ${activityTotal} 次提交`">
                  <g class="detail-chart__grid" aria-hidden="true">
                    <line v-for="(line, index) in chartGrid" :key="`grid-${index}`" :x1="CHART.padding.left"
                      :x2="CHART.width - CHART.padding.right" :y1="line.y" :y2="line.y" />
                  </g>
                  <g class="detail-chart__axis" aria-hidden="true">
                    <text v-for="(line, index) in chartGrid" :key="`axis-${index}`"
                      :x="CHART.padding.left - 6" :y="line.y + 4" text-anchor="end">{{ line.label }}</text>
                    <text v-for="tick in chartTicks" :key="`tick-${tick.index}`" :x="tick.x"
                      :y="CHART.height - 6" text-anchor="middle">{{ tick.label }}</text>
                  </g>
                  <path class="detail-chart__line" :d="activityPath" aria-hidden="true" />
                </svg>
                <p class="detail-chart__foot">近 30 天提交 {{ activityTotal }} 次</p>
              </div>
            </section>

            <section class="detail-section" aria-labelledby="detail-submissions-title">
              <h3 id="detail-submissions-title" class="detail-section__title">
                <i class="fas fa-pen-nib" aria-hidden="true"></i> 最近作答
              </h3>
              <p v-if="recentSubmissions.length === 0" class="detail-empty">还没有作答记录</p>
              <ul v-else class="detail-submissions" aria-label="最近作答列表">
                <li v-for="item in recentSubmissions" :key="item.key" class="detail-submission">
                  <span class="detail-submission__main">
                    <span class="detail-submission__title">
                      文章 {{ item.articleId }} · 第 {{ item.questionId }} 题
                    </span>
                    <span class="detail-submission__time">
                      <i class="far fa-clock" aria-hidden="true"></i>
                      {{ formatRelativeActivity(item.submittedAt) }}
                    </span>
                  </span>
                  <span class="chip detail-submission__status" :class="item.tone">{{ item.status }}</span>
                </li>
              </ul>
            </section>
          </template>
        </div>

        <footer class="detail-drawer__foot">
          <button type="button" class="btn btn--primary btn--sm" @click="selectCurrent">
            <i class="fas fa-check" aria-hidden="true"></i> 切换为当前学员
          </button>
          <button type="button" class="btn btn--soft btn--sm" @click="goReviews">
            <i class="fas fa-pen-nib" aria-hidden="true"></i> 去批阅
          </button>
          <button type="button" class="btn btn--ghost btn--sm" @click="goCourseware">
            <i class="fas fa-book-open" aria-hidden="true"></i> 去课件
          </button>
          <button type="button" class="btn btn--ghost btn--sm" @click="close">关闭</button>
        </footer>
      </aside>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { learnerGateway } from '@/gateways/learnerGateway'
import { buildLinePath, formatCompactNumber, levelFromLifetime, maxValue } from '@/utils/analytics'
import { formatRelativeActivity } from '@/utils/adminStudents'

const props = defineProps({
  learnerId: { type: [String, Number], required: true },
  students: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'select'])

const router = useRouter()

const CHART = Object.freeze({
  width: 520,
  height: 160,
  padding: Object.freeze({ top: 14, right: 16, bottom: 24, left: 34 })
})

// 后端 reviewStatus 只有 pending/reviewed，正确性在 reviewResult（correct/partial/wrong）。
const REVIEW_RESULT_LABELS = {
  correct: { label: '正确', tone: 'chip--lime' },
  partial: { label: '部分正确', tone: 'chip--cyan' },
  partial_correct: { label: '部分正确', tone: 'chip--cyan' },
  wrong: { label: '错误', tone: 'chip--coral' },
  incorrect: { label: '错误', tone: 'chip--coral' }
}

const panelRef = ref(null)
const profile = ref(null)
const loading = ref(false)
const errorMessage = ref('')
let requestToken = 0
let savedOverflow = ''

const studentSummary = computed(() => props.students.find(
  student => String(student?.id) === String(props.learnerId)
) || null)

const username = computed(() =>
  profile.value?.username || studentSummary.value?.username || '学员')

const claimLabel = computed(() => {
  const claimedByAccountId = profile.value?.claimedByAccountId ?? studentSummary.value?.claimedByAccountId
  const claimedByUsername = profile.value?.claimedByUsername ?? studentSummary.value?.claimedByUsername
  if (claimedByAccountId === null || claimedByAccountId === undefined || claimedByAccountId === '') {
    return '未认领'
  }
  return claimedByUsername ? `由 ${claimedByUsername} 认领` : '已被认领'
})

const lastActivityLabel = computed(() => formatRelativeActivity(
  profile.value?.lastActivityAt ?? studentSummary.value?.lastActivityAt))

const currentPoints = computed(() => toCount(profile.value?.points))
const lifetimePoints = computed(() => toCount(profile.value?.lifetimePoints))
const levelInfo = computed(() => levelFromLifetime(lifetimePoints.value))
const checkInDays = computed(() => toCount(profile.value?.checkInDays30))

const subjectProgress = computed(() => {
  const rows = Array.isArray(profile.value?.subjectProgress) ? profile.value.subjectProgress : []
  return rows.map(row => {
    const total = toCount(row?.totalQuestions)
    const submitted = toCount(row?.submittedQuestions)
    const pending = toCount(row?.pendingReviews)
    const completion = total > 0 ? Math.max(0, Math.min(100, Math.round((submitted / total) * 100))) : 0
    return {
      subject: String(row?.subject ?? '未命名学科'),
      total,
      submitted,
      pending,
      completion
    }
  })
})

const recentSubmissions = computed(() => {
  const rows = Array.isArray(profile.value?.recentSubmissions) ? profile.value.recentSubmissions : []
  return rows.map((row, index) => {
    const status = reviewStatusMeta(row)
    return {
      key: `${row?.articleId ?? 'a'}-${row?.questionId ?? index}-${index}`,
      articleId: row?.articleId ?? '—',
      questionId: row?.questionId ?? '—',
      status: status.label,
      tone: status.tone,
      submittedAt: row?.submittedAt
    }
  })
})

function reviewStatusMeta(row) {
  const status = String(row?.reviewStatus ?? '').toLowerCase()
  const result = String(row?.reviewResult ?? '').toLowerCase()
  if (status === 'pending') return { label: '待批阅', tone: 'chip--amber' }
  if (REVIEW_RESULT_LABELS[result]) return REVIEW_RESULT_LABELS[result]
  if (status === 'reviewed') return { label: '已批阅', tone: 'chip--lime' }
  return { label: '未知状态', tone: '' }
}

const activityPoints = computed(() => {
  const rows = Array.isArray(profile.value?.activity) ? profile.value.activity : []
  return rows.map(row => ({
    date: typeof row?.date === 'string' ? row.date : '',
    submissions: toCount(row?.submissions)
  }))
})

const activityMax = computed(() =>
  Math.max(1, maxValue(activityPoints.value, ['submissions'])))

const chartGrid = computed(() => {
  const innerHeight = CHART.height - CHART.padding.top - CHART.padding.bottom
  const rows = []
  for (let step = 0; step <= 2; step += 1) {
    const ratio = step / 2
    rows.push({
      label: formatCompactNumber(Math.round(activityMax.value * ratio)),
      y: Math.round((CHART.padding.top + innerHeight * (1 - ratio)) * 100) / 100
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
  if (points.length === 0) return []
  const innerWidth = CHART.width - CHART.padding.left - CHART.padding.right
  const stepX = points.length > 1 ? innerWidth / (points.length - 1) : 0
  const indexes = points.length > 1 ? [0, points.length - 1] : [0]
  return indexes.map(index => ({
    index,
    x: Math.round((CHART.padding.left + (points.length === 1 ? innerWidth / 2 : stepX * index)) * 100) / 100,
    label: formatTickDate(points[index].date)
  }))
})

const activityPath = computed(() => buildLinePath(
  activityPoints.value,
  'submissions',
  CHART.width,
  CHART.height,
  CHART.padding
))

const activityTotal = computed(() =>
  activityPoints.value.reduce((total, point) => total + (Number(point.submissions) || 0), 0))

function toCount(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? Math.round(number) : 0
}

function initialOf(value) {
  return Array.from(String(value || '?'))[0]?.toUpperCase() || '?'
}

function profileErrorMessage(error) {
  if (error?.status === 404) return '未找到该学员，可能已被删除，请关闭后刷新列表。'
  if (error?.status === 401) return '登录状态已失效，请重新登录后再试。'
  if (error?.status === 403) return '当前账户无权查看该学员详情。'
  return '学员详情加载失败，请稍后重试。'
}

async function loadProfile(learnerId = props.learnerId) {
  if (learnerId === null || learnerId === undefined || learnerId === '') return
  const token = ++requestToken
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await learnerGateway.getLearnerProfile(learnerId)
    if (token !== requestToken) return
    profile.value = response
  } catch (error) {
    if (token !== requestToken) return
    profile.value = null
    errorMessage.value = profileErrorMessage(error)
  } finally {
    if (token === requestToken) loading.value = false
  }
}

function close() {
  emit('close')
}

function selectCurrent() {
  emit('select', props.learnerId)
}

function goReviews() {
  router.push('/admin/reviews')
}

function goCourseware() {
  router.push('/admin/content')
}

function onKeydown(event) {
  if (event.key === 'Escape') close()
}

watch(() => props.learnerId, (learnerId) => {
  loadProfile(learnerId)
}, { immediate: true })

onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  savedOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  await nextTick()
  panelRef.value?.focus()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = savedOverflow
})
</script>

<style scoped>
.drawer-backdrop {
  position: fixed;
  inset: 0;
  z-index: 230;
  display: flex;
  justify-content: flex-end;
  background: rgba(28, 26, 20, 0.42);
  animation: fade-in 0.2s var(--ease-out);
}

.detail-drawer {
  display: flex;
  flex-direction: column;
  width: min(560px, 100vw);
  height: 100%;
  max-height: 100vh;
  border-left: 2px solid var(--ink);
  background: var(--paper-0);
  box-shadow: -8px 0 0 rgba(28, 26, 20, 0.16);
  outline: none;
  animation: drawer-in 0.28s var(--ease-out);
}

@keyframes drawer-in {
  from {
    opacity: 0.6;
    transform: translateX(28px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.detail-drawer__head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 20px;
  border-bottom: 1.5px solid var(--ink);
  background: var(--paper-1);
}

.detail-drawer__avatar {
  background: var(--butter);
  flex-shrink: 0;
}

.detail-drawer__title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 auto;
  min-width: 0;
}

.detail-drawer__name {
  font-size: 1.3rem;
  overflow-wrap: anywhere;
}

.detail-drawer__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.detail-drawer__activity {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  color: var(--text-3);
}

.detail-drawer__close {
  flex-shrink: 0;
}

.detail-drawer__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 18px 20px 24px;
}

.detail-drawer__foot {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 14px 20px;
  border-top: 1.5px solid var(--ink);
  background: var(--paper-1);
}

/* ------------------------------------------------------------------ */
/* 加载 / 错误                                                         */
/* ------------------------------------------------------------------ */
.detail-skeleton {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-skeleton__row {
  display: block;
  width: 100%;
  height: 96px;
  border-radius: var(--r-md);
}

.detail-state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}

.detail-error {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
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
/* 区块                                                                */
/* ------------------------------------------------------------------ */
.detail-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-section__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.92rem;
}

.detail-section__title i {
  color: var(--vermilion);
}

.detail-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
}

.detail-level {
  display: flex;
  flex-direction: column;
  gap: 6px;
  justify-content: center;
}

.detail-level__chip {
  align-self: flex-start;
}

.detail-checkin {
  color: var(--text-2);
  font-size: 0.86rem;
}

.detail-checkin strong {
  font-family: var(--font-display);
  font-size: 1.3rem;
  color: var(--ink);
}

.detail-empty {
  padding: 16px;
  border: 2px dashed var(--line-strong);
  border-radius: var(--r-md);
  background: rgba(255, 253, 246, 0.55);
  color: var(--text-3);
  font-size: 0.8rem;
  text-align: center;
}

/* ------------------------------------------------------------------ */
/* 分学科进度                                                          */
/* ------------------------------------------------------------------ */
.detail-subjects {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-subject {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 12px 14px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-1);
  box-shadow: 2px 2px 0 var(--ink);
}

.detail-subject__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.detail-subject__name {
  font-size: 0.86rem;
  font-weight: 800;
  color: var(--ink);
}

.detail-subject__meta {
  font-family: var(--font-mono);
  font-size: 0.66rem;
  color: var(--text-3);
}

.detail-subject__percent {
  align-self: flex-end;
  font-family: var(--font-mono);
  font-size: 0.64rem;
  color: var(--text-3);
}

/* ------------------------------------------------------------------ */
/* 30 天动态折线                                                       */
/* ------------------------------------------------------------------ */
.detail-chart {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 12px 8px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-md);
  background: var(--paper-1);
  box-shadow: 2px 2px 0 var(--ink);
}

.detail-chart__svg {
  width: 100%;
  height: auto;
}

.detail-chart__grid line {
  stroke: var(--line);
  stroke-width: 1;
  stroke-dasharray: 4 5;
}

.detail-chart__axis text {
  fill: var(--text-3);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.02em;
}

.detail-chart__line {
  fill: none;
  stroke: var(--vermilion);
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}

.detail-chart__foot {
  font-family: var(--font-mono);
  font-size: 0.68rem;
  color: var(--text-3);
}

/* ------------------------------------------------------------------ */
/* 最近作答                                                            */
/* ------------------------------------------------------------------ */
.detail-submissions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-submission {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-1);
  box-shadow: 2px 2px 0 var(--ink);
}

.detail-submission__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.detail-submission__title {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--ink);
}

.detail-submission__time {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 0.64rem;
  color: var(--text-3);
}

.detail-submission__status {
  flex-shrink: 0;
  height: 26px;
  padding: 0 10px;
  font-size: 0.7rem;
}

@media (max-width: 760px) {
  .detail-drawer {
    width: 100vw;
    max-width: 100vw;
    border-left: 0;
  }

  .detail-drawer__head,
  .detail-drawer__body,
  .detail-drawer__foot {
    padding-left: 15px;
    padding-right: 15px;
  }

  .detail-drawer__foot .btn {
    flex: 1 1 auto;
  }
}
</style>
