<!--
  CheckInModal.vue - 每日签到弹窗（Riso Campus 印章本）
  ----------------------------------------------------------------------------
  1. 纸白弹层 + 整月日历（周一开头）：纸白格、朱红勾选、今日高亮、跨月空白
  2. 签到状态、月度次数、第 7/14 次额外奖励进度均来自后端 /api/v1/checkins
  3. 登录成功后由 checkinStore.openAfterLogin() 打开；任务卡「去签到」同样打开本弹窗
  4. 签到成功：朱红「已签到」印章 + 红/蓝/黄绿纸屑 confetti，并 toast 反馈

  E2E 契约（不要改）：根节点 .checkin-panel、heading 含「每日签到」、
  按钮文案含「签到 +100 积分」、签到后按钮「今日已签到」且 disabled。
-->
<template>
    <div v-if="checkinStore.modalVisible" class="checkin-overlay" @click.self="checkinStore.closeModal()">
        <div class="checkin-panel" role="dialog" aria-modal="true" aria-labelledby="checkin-title">
            <span class="checkin-riso" aria-hidden="true"></span>

            <button type="button" class="modal__close checkin-close" aria-label="关闭签到面板"
                @click="checkinStore.closeModal()">
                <i class="fas fa-times"></i>
            </button>

            <header class="checkin-head">
                <span class="eyebrow">DAILY CHECK-IN</span>
                <h2 id="checkin-title" class="checkin-title">
                    <span class="checkin-icon" aria-hidden="true"><i class="fas fa-calendar-check"></i></span>
                    每日签到
                </h2>
                <p class="checkin-subtitle">
                    每日可签到一次，每次 +{{ checkinStore.pointsPerCheckIn }} 积分；本月第 7、14 次签到额外
                    +{{ checkinStore.pointsPerCheckIn }} 积分
                </p>
            </header>

            <div class="checkin-grid">
                <!-- 月历 -->
                <section class="calendar-card">
                    <div class="month-nav">
                        <button type="button" class="month-nav-btn" aria-label="上个月"
                            :disabled="!checkinStore.year || checkinStore.month <= 1 && checkinStore.year <= 2000"
                            @click="goMonth(-1)">
                            <i class="fas fa-chevron-left"></i>
                        </button>
                        <span class="month-title">{{ checkinStore.year }} 年 {{ checkinStore.month }} 月</span>
                        <button type="button" class="month-nav-btn" aria-label="下个月"
                            :disabled="!checkinStore.year || checkinStore.month >= 12 && checkinStore.year >= 2100"
                            @click="goMonth(1)">
                            <i class="fas fa-chevron-right"></i>
                        </button>
                    </div>
                    <button v-if="!isCurrentMonth" type="button" class="back-today" @click="goCurrentMonth">
                        <i class="fas fa-rotate-left"></i> 回到本月
                    </button>

                    <!-- 日历网格（周一开头） -->
                    <div class="calendar" :class="{ 'is-loading': checkinStore.loading }">
                        <div class="weekdays">
                            <span v-for="w in WEEKDAYS" :key="w" class="weekday">{{ w }}</span>
                        </div>
                        <div class="days">
                            <span v-for="(d, i) in grid" :key="i" class="day-cell"
                                :class="{ 'is-blank': !d, 'is-today': d && isToday(d), 'is-checked': d && checkedDays.has(d) }">
                                <span class="day-num">{{ d || '' }}</span>
                                <span v-if="d && checkedDays.has(d)" class="day-mark" aria-hidden="true">
                                    <i class="fas fa-check"></i>
                                </span>
                            </span>
                        </div>
                    </div>

                    <div class="month-foot">
                        <span><i class="fas fa-circle-check"></i> 本月已签到 <strong>{{ checkinStore.monthCount }}</strong> 天</span>
                        <span v-if="nextBonus" class="month-foot-next">距第 {{ nextBonus }} 次奖励还差 {{ nextBonus - checkinStore.monthCount }} 天</span>
                        <span v-else class="month-foot-next is-done">月度额外奖励已全部拿到</span>
                    </div>
                </section>

                <!-- 状态与签到 -->
                <aside class="checkin-side">
                    <div class="side-stats">
                        <div class="side-stat">
                            <span class="side-stat-label">当前积分</span>
                            <strong class="side-stat-value">{{ tasksStore.points }}</strong>
                        </div>
                        <div class="side-stat">
                            <span class="side-stat-label">连续签到</span>
                            <strong class="side-stat-value">{{ streak }}<small>天</small></strong>
                        </div>
                    </div>

                    <!-- 额外奖励进度 -->
                    <div class="bonus-progress">
                        <div v-for="m in checkinStore.bonusMilestones" :key="m" class="bonus-item"
                            :class="{ 'is-reached': checkinStore.monthCount >= m }">
                            <i class="fas" :class="checkinStore.monthCount >= m ? 'fa-circle-check' : 'fa-circle'"></i>
                            <span class="bonus-text">第 {{ m }} 次签到额外 +{{ checkinStore.pointsPerCheckIn }} 积分</span>
                            <span v-if="checkinStore.monthCount < m" class="bonus-remain">还差 {{ m - checkinStore.monthCount }} 次</span>
                        </div>
                    </div>

                    <!-- 加载失败提示 -->
                    <p v-if="checkinStore.loadError" class="field-error checkin-error">
                        <i class="fas fa-triangle-exclamation"></i> {{ checkinStore.loadError }}
                    </p>

                    <!-- 签到按钮（E2E：文案含「签到 +100 积分」；签到后「今日已签到」且 disabled） -->
                    <button type="button" class="btn checkin-btn"
                        :class="checkinStore.todayCheckedIn ? 'btn--soft' : 'btn--warm'"
                        :disabled="submitting || checkinStore.todayCheckedIn || !isCurrentMonth"
                        @click="handleCheckIn">
                        <i class="fas" :class="checkinStore.todayCheckedIn ? 'fa-circle-check' : 'fa-hand-point-up'"></i>
                        {{ checkinBtnText }}
                    </button>

                    <p class="checkin-tip">
                        <i class="fas fa-shield-halved"></i> 签到记录由服务端按 Asia/Shanghai 结算
                    </p>
                </aside>
            </div>

            <!-- 签到成功：盖章 + 纸屑庆祝（不遮挡交互） -->
            <div v-if="celebrating" class="checkin-celebration" aria-hidden="true">
                <span class="checkin-stamp">
                    <i class="fas fa-check"></i> 已签到
                </span>
                <span v-for="piece in confettiPieces" :key="piece.id" class="confetti" :style="piece.style"></span>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useCheckinStore } from '@/stores/checkinStore'
import { useTasksStore } from '@/stores/tasksStore'
import { showToast } from '@/utils/toast'

const checkinStore = useCheckinStore()
const tasksStore = useTasksStore()

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
const MIN_YEAR = 2000
const MAX_YEAR = 2100
const CONFETTI_COLORS = ['#ed4b2a', '#1d4ed8', '#c9ef4a', '#ffc53d', '#ff9ebb', '#7cc5f0']

const submitting = ref(false)
const celebrating = ref(false)
const confettiPieces = ref([])
let celebrateTimer = null

/** 服务端「今日」（Asia/Shanghai）；未加载到时回退客户端本地日期 */
const serverToday = computed(() => {
    if (checkinStore.today) {
        const [year, month, day] = checkinStore.today.split('-').map(Number)
        return { year, month, day }
    }
    const now = new Date()
    return { year: now.getFullYear(), month: now.getMonth() + 1, day: now.getDate() }
})

const isCurrentMonth = computed(() =>
    checkinStore.year === serverToday.value.year && checkinStore.month === serverToday.value.month)

/** 日历网格：周一开头，前置空位补 null，尾部补齐整周 */
const grid = computed(() => {
    const { year, month } = checkinStore
    if (!year || !month) return []
    const firstWeekday = (new Date(year, month - 1, 1).getDay() + 6) % 7
    const daysInMonth = new Date(year, month, 0).getDate()
    const cells = [
        ...Array(firstWeekday).fill(null),
        ...Array.from({ length: daysInMonth }, (_, i) => i + 1)
    ]
    while (cells.length % 7 !== 0) cells.push(null)
    return cells
})

/** 当月已签到日期集合（后端只返回当月日期） */
const checkedDays = computed(() => new Set(checkinStore.dates.map(d => Number(d.slice(8, 10)))))

/** 本月连续签到天数：从今日（未签则从昨日）向前连续计数，仅在已加载月份内计算 */
const streak = computed(() => {
    if (!checkinStore.today || !checkinStore.dates.length) return 0
    const daySet = new Set(checkinStore.dates)
    const cursor = new Date(`${checkinStore.today}T00:00:00`)
    if (!daySet.has(checkinStore.today)) cursor.setDate(cursor.getDate() - 1)
    let count = 0
    while (count < 31) {
        if (cursor.getFullYear() !== checkinStore.year || cursor.getMonth() + 1 !== checkinStore.month) break
        const iso = `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, '0')}-${String(cursor.getDate()).padStart(2, '0')}`
        if (!daySet.has(iso)) break
        count += 1
        cursor.setDate(cursor.getDate() - 1)
    }
    return count
})

/** 下一次签到是否命中额外奖励节点（第 7/14 次） */
const nextBonus = computed(() =>
    checkinStore.bonusMilestones.find(m => m > checkinStore.monthCount) ?? null)

function isToday(day) {
    const t = serverToday.value
    return t.year === checkinStore.year && t.month === checkinStore.month && t.day === day
}

function goMonth(delta) {
    let { year, month } = checkinStore
    if (!year || !month) return
    month += delta
    if (month < 1) {
        month = 12
        year -= 1
    } else if (month > 12) {
        month = 1
        year += 1
    }
    if (year < MIN_YEAR || year > MAX_YEAR) return
    checkinStore.loadMonth(year, month)
}

function goCurrentMonth() {
    checkinStore.loadMonth(serverToday.value.year, serverToday.value.month)
}

const nextIsBonus = computed(() =>
    checkinStore.bonusMilestones.includes(checkinStore.monthCount + 1))

const checkinBtnText = computed(() => {
    if (submitting.value) return '签到中…'
    if (checkinStore.todayCheckedIn) return '今日已签到'
    if (!isCurrentMonth.value) return '请切换到本月签到'
    if (nextIsBonus.value) return `签到 +${checkinStore.pointsPerCheckIn * 2} 积分（第 ${checkinStore.monthCount + 1} 次奖励）`
    return `签到 +${checkinStore.pointsPerCheckIn} 积分`
})

/** 签到成功庆祝：朱红印章 + 印刷色纸屑（纯 CSS 动画，不拦截点击） */
function celebrate() {
    if (celebrateTimer) {
        clearTimeout(celebrateTimer)
        celebrateTimer = null
    }
    celebrating.value = true
    confettiPieces.value = Array.from({ length: 26 }, (_, i) => ({
        id: i,
        style: {
            left: `${Math.round(Math.random() * 100)}%`,
            '--confetti-x': `${Math.round((Math.random() - 0.5) * 260)}px`,
            '--confetti-rotate': `${Math.round(Math.random() * 360)}deg`,
            animationDelay: `${(Math.random() * 0.45).toFixed(2)}s`,
            animationDuration: `${(1.7 + Math.random() * 0.9).toFixed(2)}s`,
            background: CONFETTI_COLORS[i % CONFETTI_COLORS.length]
        }
    }))
    celebrateTimer = setTimeout(() => {
        celebrating.value = false
        confettiPieces.value = []
        celebrateTimer = null
    }, 3200)
}

async function handleCheckIn() {
    if (submitting.value || checkinStore.todayCheckedIn || !isCurrentMonth.value) return
    submitting.value = true
    try {
        const result = await checkinStore.checkInToday()
        const bonusText = result.bonusDays ? `（第 ${result.bonusDays} 次签到额外奖励）` : ''
        celebrate()
        showToast(`签到成功，+${result.pointsAwarded} 积分${bonusText}`, 'success')
    } catch (e) {
        showToast(e.message || '签到失败，请稍后重试', 'error')
    } finally {
        submitting.value = false
    }
}

function onKeydown(event) {
    if (event.key === 'Escape' && checkinStore.modalVisible) checkinStore.closeModal()
}

onMounted(() => {
    // 挂载时若弹层已处于打开状态（例如登录后打开），刷新当前月签到数据
    if (checkinStore.modalVisible) checkinStore.loadMonth()
    window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
    window.removeEventListener('keydown', onKeydown)
    if (celebrateTimer) clearTimeout(celebrateTimer)
})
</script>

<style scoped>
/* ========== 浮层 ========== */
.checkin-overlay {
    position: fixed;
    inset: 0;
    z-index: 260;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 22px;
    background: rgba(28, 26, 20, 0.42);
    animation: fade-in 0.2s var(--ease-out);
}

/* ========== 纸白弹层 ========== */
.checkin-panel {
    position: relative;
    width: min(760px, 100%);
    max-height: 92vh;
    overflow-y: auto;
    padding: 34px 30px 26px;
    border: 2px solid var(--ink);
    border-radius: var(--r-lg);
    background: var(--paper-1);
    box-shadow: var(--shadow-float);
    animation: pop-in 0.32s var(--ease-spring);
}

/* 顶部印刷条纹 */
.checkin-riso {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 10px;
    background: repeating-linear-gradient(135deg, var(--vermilion) 0 10px, var(--butter) 10px 20px);
    border-bottom: 1.5px solid var(--ink);
    border-radius: var(--r-lg) var(--r-lg) 0 0;
    pointer-events: none;
}

.checkin-close {
    position: absolute;
    top: 18px;
    right: 16px;
    z-index: 5;
}

/* ========== 头部 ========== */
.checkin-head {
    position: relative;
    z-index: 2;
    text-align: center;
    margin-bottom: 22px;
}

.checkin-title {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-top: 8px;
    font-size: clamp(1.5rem, 3vw, 1.9rem);
}

.checkin-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 46px;
    height: 46px;
    border: 1.5px solid var(--ink);
    border-radius: 13px;
    background: var(--butter);
    color: var(--ink);
    font-size: 1.1rem;
    box-shadow: 3px 3px 0 var(--ink);
}

.checkin-subtitle {
    margin-top: 10px;
    color: var(--text-2);
    font-size: 0.84rem;
    line-height: 1.6;
}

/* ========== 主体双栏 ========== */
.checkin-grid {
    position: relative;
    z-index: 2;
    display: grid;
    grid-template-columns: minmax(0, 1.18fr) minmax(0, 0.82fr);
    gap: 22px;
    align-items: start;
}

/* ========== 月历 ========== */
.calendar-card {
    padding: 16px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-0);
    box-shadow: 3px 3px 0 var(--ink);
}

.month-nav {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-bottom: 10px;
}

.month-nav-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border: 1.5px solid var(--ink);
    border-radius: 8px;
    background: var(--paper-1);
    color: var(--ink);
    font-size: 0.78rem;
    box-shadow: 2px 2px 0 var(--ink);
    transition: transform var(--dur-fast) var(--ease-out), background var(--dur-fast);
}

.month-nav-btn:hover:not(:disabled) {
    transform: translate(-1px, -1px);
    background: var(--butter);
}

.month-nav-btn:disabled {
    opacity: 0.4;
    box-shadow: none;
}

.month-title {
    font-family: var(--font-display);
    font-size: 1rem;
    font-weight: 800;
    min-width: 112px;
    text-align: center;
    font-variant-numeric: tabular-nums;
    color: var(--ink);
}

.back-today {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin: 0 auto 10px;
    padding: 4px 12px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-1);
    color: var(--cobalt);
    font-size: 0.72rem;
    font-weight: 700;
    transition: background var(--dur);
}

.back-today:hover {
    background: var(--sky);
}

.calendar {
    padding: 10px;
    border: 1.5px solid var(--ink);
    border-radius: 10px;
    background: var(--paper-1);
    transition: opacity var(--dur);
}

.calendar.is-loading {
    opacity: 0.55;
}

.weekdays {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    margin-bottom: 6px;
}

.weekday {
    text-align: center;
    padding: 3px 0;
    font-family: var(--font-mono);
    font-size: 0.66rem;
    font-weight: 600;
    letter-spacing: 0.06em;
    color: var(--text-3);
}

.days {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 5px;
}

/* 纸白格 + 朱红勾选 */
.day-cell {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    aspect-ratio: 1;
    border: 1px solid var(--line);
    border-radius: 6px;
    background: var(--paper-1);
    color: var(--text-2);
    font-size: 0.85rem;
    font-weight: 600;
    transition: background var(--dur-fast), border-color var(--dur-fast), transform var(--dur-fast);
}

.day-cell:hover {
    transform: translateY(-1px);
    border-color: var(--ink);
}

.day-cell.is-blank {
    visibility: hidden;
}

.day-cell.is-checked {
    background: var(--paper-2);
    border-color: var(--ink);
    color: var(--ink);
    font-weight: 800;
}

.day-cell.is-today {
    border: 2px solid var(--ink);
    background: var(--butter);
    color: var(--ink);
    font-weight: 800;
}

.day-cell.is-today.is-checked {
    background: var(--butter);
}

.day-mark {
    position: absolute;
    right: 3px;
    bottom: 0;
    color: var(--vermilion);
    font-size: 0.52rem;
}

.month-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
    margin-top: 14px;
    font-size: 0.76rem;
    color: var(--text-2);
}

.month-foot i {
    margin-right: 4px;
    color: var(--vermilion);
}

.month-foot strong {
    font-family: var(--font-mono);
    color: var(--ink);
}

.month-foot-next {
    color: var(--cobalt);
    font-weight: 700;
}

.month-foot-next.is-done {
    color: var(--green);
}

/* ========== 右侧状态 ========== */
.checkin-side {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.side-stats {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
}

.side-stat {
    padding: 12px 14px 10px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
}

.side-stat:nth-child(1) {
    background: var(--butter);
}

.side-stat:nth-child(2) {
    background: var(--sky);
}

.side-stat-label {
    display: block;
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--ink-soft);
    letter-spacing: 0.04em;
}

.side-stat-value {
    display: block;
    margin-top: 2px;
    font-family: var(--font-display);
    font-size: 1.6rem;
    font-weight: 800;
    line-height: 1.05;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.side-stat-value small {
    margin-left: 2px;
    font-size: 0.7rem;
}

.bonus-progress {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.bonus-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 9px 12px;
    border: 1.5px solid var(--ink);
    border-radius: 8px;
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
    font-size: 0.78rem;
    color: var(--text-2);
}

.bonus-item i {
    color: var(--text-4);
    font-size: 0.8rem;
}

.bonus-item.is-reached {
    background: var(--lime);
    color: var(--ink);
}

.bonus-item.is-reached i {
    color: var(--ink);
}

.bonus-text {
    flex: 1;
    min-width: 0;
}

.bonus-remain {
    font-family: var(--font-mono);
    font-size: 0.7rem;
    color: var(--vermilion);
    white-space: nowrap;
}

.checkin-error {
    text-align: center;
}

/* ========== 签到按钮 ========== */
.checkin-btn {
    width: 100%;
    height: 52px;
    font-size: 1rem;
    font-weight: 800;
}

.checkin-btn .fa-circle-check {
    color: var(--green);
}

.checkin-tip {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    font-size: 0.72rem;
    color: var(--text-3);
}

/* ========== 签到成功庆祝 ========== */
.checkin-celebration {
    position: absolute;
    inset: 0;
    z-index: 4;
    overflow: hidden;
    pointer-events: none;
    border-radius: inherit;
}

/* 朱红印章 */
.checkin-stamp {
    position: absolute;
    top: 18px;
    left: 20px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    border: 3px solid var(--vermilion);
    border-radius: 10px;
    background: var(--paper-1);
    color: var(--vermilion);
    font-family: var(--font-display);
    font-size: 1.05rem;
    font-weight: 800;
    letter-spacing: 0.12em;
    transform: rotate(-8deg);
    animation: pop-in 0.4s var(--ease-spring);
}

.confetti {
    position: absolute;
    top: -12%;
    width: 9px;
    height: 13px;
    border: 1px solid rgba(28, 26, 20, 0.45);
    border-radius: 1px;
    opacity: 0.95;
    animation-name: confetti-fall;
    animation-timing-function: cubic-bezier(0.25, 0.6, 0.5, 1);
    animation-fill-mode: forwards;
}

/* ========== 响应式 ========== */
@media (max-width: 760px) {
    .checkin-overlay {
        padding: 12px;
        align-items: flex-start;
    }

    .checkin-panel {
        max-height: 94vh;
        padding: 30px 16px 18px;
        border-radius: var(--r-md);
    }

    .checkin-grid {
        grid-template-columns: minmax(0, 1fr);
        gap: 18px;
    }

    .checkin-head {
        margin-bottom: 18px;
    }

    .checkin-subtitle {
        font-size: 0.78rem;
    }

    .checkin-stamp {
        top: 14px;
        left: 14px;
        padding: 6px 12px;
        font-size: 0.9rem;
    }

    .day-cell {
        font-size: 0.8rem;
        border-radius: 5px;
    }
}

@media (max-width: 380px) {
    .days {
        gap: 4px;
    }

    .day-cell {
        font-size: 0.74rem;
    }
}
</style>
