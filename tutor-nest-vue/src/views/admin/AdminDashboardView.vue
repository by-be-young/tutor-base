<!-- src/views/admin/AdminDashboardView.vue -->
<template>
  <div class="admin-page">
    <!-- 欢迎语 + 紧凑指标 -->
    <section class="panel dash-head">
      <div class="dash-head__copy">
        <span class="eyebrow">{{ todayLabel }} / 管理工作台</span>
        <h2 class="dash-head__title">{{ authStore.username }}，<span class="text-gradient">今天先看谁</span></h2>
        <p class="dash-head__sub">学员动态、待办批阅与课件认领一屏掌握，点击任意学员即可进入管理。</p>
      </div>

      <ul class="dash-metrics" aria-label="管理概览指标">
        <li class="dash-metric">
          <span class="dash-metric__value">{{ myStudentCount }}</span>
          <span class="dash-metric__label">我的学员</span>
        </li>
        <li class="dash-metric dash-metric--amber">
          <span class="dash-metric__value">{{ unclaimedStudentCount }}</span>
          <span class="dash-metric__label">未认领学员</span>
        </li>
        <li class="dash-metric dash-metric--coral">
          <span class="dash-metric__value">{{ unclaimedCoursewareCount }}</span>
          <span class="dash-metric__label">待认领课件</span>
        </li>
        <li class="dash-metric dash-metric--lime">
          <span class="dash-metric__value">{{ mySubjectCount }}</span>
          <span class="dash-metric__label">在学学科</span>
        </li>
      </ul>

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

    <!-- 学员动态 -->
    <section class="panel panel--pad activity-panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">ACTIVITY / 学员动态</span>
          <h2 class="section-title">最近活跃的学员</h2>
        </div>
        <span class="section-note">按最近动态倒序 · 前 {{ recentStudents.length }} 名</span>
      </div>

      <div v-if="recentStudents.length === 0" class="empty empty--inline">
        <span class="empty__icon"><i class="fas fa-user-plus" aria-hidden="true"></i></span>
        <p class="empty__title">还没有学员</p>
        <p class="empty__text">到「学员」页新增第一位学员，认领后即可配置内容授权与批阅。</p>
        <router-link to="/admin/learners" class="btn btn--soft btn--sm">去新增学员</router-link>
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
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useAdminStore } from '@/stores/adminStore'
import { useCoursewareStore } from '@/stores/coursewareStore'
import { useAdminWorkspace } from '@/composables/useAdminWorkspace'
import { claimState, formatRelativeActivity, sortStudentsByRecentActivity } from '@/utils/adminStudents'
import { activeSubjects, buildSubjectScope } from '@/utils/adminSubjects'

const router = useRouter()
const authStore = useAuthStore()
const adminStore = useAdminStore()
const coursewareStore = useCoursewareStore()
const { blogData, accountId, handleSelectStudent, loadWorkspace } = useAdminWorkspace()

const WEEK_DAYS = 7
const DAY_MS = 24 * 60 * 60 * 1000

const todayLabel = computed(() => new Intl.DateTimeFormat('zh-CN', {
  month: 'long', day: 'numeric', weekday: 'long'
}).format(new Date()))

const myStudentCount = computed(() =>
  adminStore.students.filter(student => claimState(student, accountId.value) === 'mine').length)

const unclaimedStudentCount = computed(() =>
  adminStore.students.filter(student => claimState(student, accountId.value) === 'unclaimed').length)

const unclaimedCoursewareCount = computed(() => coursewareStore.unclaimedCount ?? 0)
const mySubjectCount = computed(() => (coursewareStore.visibleSubjects || []).length)

const recentStudents = computed(() =>
  sortStudentsByRecentActivity(adminStore.students).slice(0, 8))

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

onMounted(loadWorkspace)
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ------------------------------------------------------------------ */
/* 欢迎语 + 指标                                                       */
/* ------------------------------------------------------------------ */
.dash-head {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 26px;
  border-radius: var(--r-lg);
}

.dash-head__copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dash-head__title {
  font-size: clamp(1.5rem, 2.6vw, 2.1rem);
}

.dash-head__sub {
  font-size: 0.88rem;
  color: var(--text-2);
}

.dash-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.dash-metric {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 14px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  background: var(--paper-2);
  min-width: 0;
}

.dash-metric--amber { background: var(--butter); }
.dash-metric--coral { background: var(--coral); }
.dash-metric--lime { background: var(--lime); }

.dash-metric__value {
  font-family: var(--font-display);
  font-size: 1.24rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.03em;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.dash-metric__label {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dash-quick {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .dash-head {
    padding: 18px;
    gap: 14px;
  }

  .dash-metrics {
    gap: 8px;
  }

  .dash-metric {
    padding: 8px 11px;
    gap: 6px;
  }

  .dash-metric__value {
    font-size: 1.08rem;
  }

  .dash-metric__label {
    font-size: 0.66rem;
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
