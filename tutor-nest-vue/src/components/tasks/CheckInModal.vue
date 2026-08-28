<!--
  CheckInModal.vue - 每日签到弹窗
  ----------------------------------------------------------------------------
  1. 整月大日历（周一开头），已签日期打勾、今日高亮、跨月空白
  2. 签到状态、月度次数、第 7/14 次额外奖励进度均来自后端 /api/v1/checkins
  3. 登录成功后由 checkinStore.openAfterLogin() 打开；任务列表「去签到」同样打开本弹窗
-->
<template>
    <div v-if="checkinStore.modalVisible" class="checkin-overlay" @click.self="checkinStore.closeModal()">
        <div class="checkin-panel">
            <button class="checkin-close" @click="checkinStore.closeModal()">
                <i class="fas fa-times"></i>
            </button>

            <header class="checkin-head">
                <h2 class="checkin-title"><i class="fas fa-calendar-check"></i> 每日签到</h2>
                <p class="checkin-subtitle">
                    每日可签到一次，每次 +{{ checkinStore.pointsPerCheckIn }} 积分；本月第 7、14 次签到额外
                    +{{ checkinStore.pointsPerCheckIn }} 积分
                </p>
            </header>

            <!-- 统计行 -->
            <div class="checkin-stats">
                <div class="checkin-stat">
                    <span class="checkin-stat-num">{{ tasksStore.points }}</span>
                    <span class="checkin-stat-label">当前积分</span>
                </div>
                <div class="checkin-stat">
                    <span class="checkin-stat-num">{{ checkinStore.monthCount }}</span>
                    <span class="checkin-stat-label">本月已签到</span>
                </div>
            </div>

            <!-- 月份导航 -->
            <div class="month-nav">
                <button class="month-nav-btn" :disabled="!checkinStore.year || checkinStore.month <= 1 && checkinStore.year <= 2000"
                    @click="goMonth(-1)">
                    <i class="fas fa-chevron-left"></i>
                </button>
                <span class="month-title">{{ checkinStore.year }} 年 {{ checkinStore.month }} 月</span>
                <button class="month-nav-btn" :disabled="!checkinStore.year || checkinStore.month >= 12 && checkinStore.year >= 2100"
                    @click="goMonth(1)">
                    <i class="fas fa-chevron-right"></i>
                </button>
                <button v-if="!isCurrentMonth" class="back-today" @click="goCurrentMonth">
                    <i class="fas fa-rotate-left"></i> 回到本月
                </button>
            </div>

            <!-- 日历网格（周一开头） -->
            <div class="calendar">
                <div class="weekdays">
                    <span v-for="w in WEEKDAYS" :key="w" class="weekday">{{ w }}</span>
                </div>
                <div class="days">
                    <div v-for="(d, i) in grid" :key="i" class="day-cell"
                        :class="{ 'is-blank': !d, 'is-today': d && isToday(d), 'is-checked': d && checkedDays.has(d) }">
                        <span class="day-num">{{ d || '' }}</span>
                        <span v-if="d && checkedDays.has(d)" class="day-dot"><i class="fas fa-check"></i></span>
                    </div>
                </div>
            </div>

            <!-- 奖励进度 -->
            <div class="bonus-progress">
                <div v-for="m in checkinStore.bonusMilestones" :key="m" class="bonus-item"
                    :class="{ 'is-reached': checkinStore.monthCount >= m }">
                    <i class="fas" :class="checkinStore.monthCount >= m ? 'fa-check-circle' : 'fa-circle'"></i>
                    <span class="bonus-text">第 {{ m }} 次签到额外 +{{ checkinStore.pointsPerCheckIn }} 积分</span>
                    <span v-if="checkinStore.monthCount < m" class="bonus-remain">还差 {{ m - checkinStore.monthCount }} 次</span>
                </div>
            </div>

            <!-- 加载失败提示 -->
            <p v-if="checkinStore.loadError" class="checkin-error">
                <i class="fas fa-triangle-exclamation"></i> {{ checkinStore.loadError }}
            </p>

            <!-- 签到按钮 -->
            <div class="checkin-footer">
                <button class="checkin-btn" :class="{
                        'is-done': checkinStore.todayCheckedIn,
                        'is-bonus': isCurrentMonth && nextIsBonus
                    }"
                    :disabled="submitting || checkinStore.todayCheckedIn || !isCurrentMonth" @click="handleCheckIn">
                    <i class="fas" :class="checkinStore.todayCheckedIn ? 'fa-check' : 'fa-hand-point-up'"></i>
                    {{ checkinBtnText }}
                </button>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useCheckinStore } from '@/stores/checkinStore'
import { useTasksStore } from '@/stores/tasksStore'
import { showToast } from '@/utils/toast'

const checkinStore = useCheckinStore()
const tasksStore = useTasksStore()

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
const MIN_YEAR = 2000
const MAX_YEAR = 2100

const submitting = ref(false)

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

/** 下一次签到是否命中额外奖励节点（第 7/14 次） */
const nextIsBonus = computed(() =>
    checkinStore.bonusMilestones.includes(checkinStore.monthCount + 1))

const checkinBtnText = computed(() => {
    if (submitting.value) return '签到中…'
    if (checkinStore.todayCheckedIn) return '今日已签到'
    if (!isCurrentMonth.value) return '请切换到本月签到'
    if (nextIsBonus.value) return `签到 +${checkinStore.pointsPerCheckIn * 2} 积分（第 ${checkinStore.monthCount + 1} 次奖励）`
    return `签到 +${checkinStore.pointsPerCheckIn} 积分`
})

async function handleCheckIn() {
    if (submitting.value || checkinStore.todayCheckedIn || !isCurrentMonth.value) return
    submitting.value = true
    try {
        const result = await checkinStore.checkInToday()
        const bonusText = result.bonusDays ? `（第 ${result.bonusDays} 次签到额外奖励）` : ''
        showToast(`签到成功，+${result.pointsAwarded} 积分${bonusText}`, 'success')
    } catch (e) {
        showToast(e.message || '签到失败，请稍后重试', 'error')
    } finally {
        submitting.value = false
    }
}
</script>

<style scoped>
.checkin-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.4);
    z-index: 2000;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
    animation: checkinFadeIn 0.25s ease;
}

@keyframes checkinFadeIn {
    from {
        opacity: 0;
    }
    to {
        opacity: 1;
    }
}

.checkin-panel {
    position: relative;
    width: 100%;
    max-width: 420px;
    max-height: 92vh;
    overflow-y: auto;
    background: #fbfaf6;
    border-radius: 24px;
    box-shadow: 0 24px 80px rgba(0, 0, 0, 0.25);
    padding: 32px 26px 26px;
    animation: checkinPopIn 0.3s ease;
}

@keyframes checkinPopIn {
    from {
        opacity: 0;
        transform: translateY(20px) scale(0.97);
    }
    to {
        opacity: 1;
        transform: translateY(0) scale(1);
    }
}

.checkin-close {
    position: absolute;
    top: 14px;
    right: 14px;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    border: none;
    background: rgba(120, 170, 155, 0.08);
    color: #4c6b5b;
    cursor: pointer;
    font-size: 0.95rem;
    transition: background 0.2s ease;
}

.checkin-close:hover {
    background: rgba(120, 170, 155, 0.2);
}

/* ========== 头部 ========== */
.checkin-head {
    text-align: center;
    margin-bottom: 16px;
}

.checkin-title {
    font-size: 1.6rem;
    font-weight: 700;
    color: #2d4a3a;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
}

.checkin-title i {
    color: #9782c8;
}

.checkin-subtitle {
    color: var(--gray);
    font-size: 0.88rem;
    margin-top: 6px;
}

/* ========== 统计行 ========== */
.checkin-stats {
    display: flex;
    gap: 14px;
    margin-bottom: 16px;
}

.checkin-stat {
    flex: 1;
    background: rgba(255, 255, 255, 0.55);
    border: 1px solid rgba(255, 255, 255, 0.6);
    border-radius: 14px;
    padding: 10px 8px;
    text-align: center;
}

.checkin-stat-num {
    display: block;
    font-size: 1.5rem;
    font-weight: 700;
    color: #b6862a;
    line-height: 1.2;
}

.checkin-stat:last-child .checkin-stat-num {
    color: #9782c8;
}

.checkin-stat-label {
    font-size: 0.8rem;
    color: var(--gray);
}

/* ========== 月份导航 ========== */
.month-nav {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-bottom: 10px;
}

.month-nav-btn {
    width: 30px;
    height: 30px;
    border-radius: 50%;
    border: 1px solid rgba(151, 130, 200, 0.3);
    background: rgba(255, 255, 255, 0.5);
    color: #5d4a8a;
    cursor: pointer;
    transition: background 0.2s ease, transform 0.2s ease;
    font-size: 0.8rem;
}

.month-nav-btn:hover:not(:disabled) {
    background: rgba(151, 130, 200, 0.15);
    transform: translateY(-1px);
}

.month-nav-btn:disabled {
    opacity: 0.35;
    cursor: not-allowed;
}

.month-title {
    font-size: 1.05rem;
    font-weight: 600;
    color: #2d4a3a;
    min-width: 110px;
    text-align: center;
}

.back-today {
    border: none;
    background: none;
    color: #5d4a8a;
    font-size: 0.8rem;
    cursor: pointer;
    font-family: inherit;
}

.back-today:hover {
    text-decoration: underline;
}

/* ========== 日历 ========== */
.calendar {
    background: rgba(255, 255, 255, 0.55);
    border: 1px solid rgba(255, 255, 255, 0.6);
    border-radius: 16px;
    padding: 10px;
}

.weekdays {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    margin-bottom: 4px;
}

.weekday {
    text-align: center;
    font-size: 0.78rem;
    color: #8aa398;
    padding: 4px 0;
}

.days {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 4px;
}

.day-cell {
    position: relative;
    aspect-ratio: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    font-size: 0.9rem;
    color: #3d5a4a;
}

.day-cell.is-blank {
    visibility: hidden;
}

.day-cell.is-today {
    background: rgba(151, 130, 200, 0.16);
    border: 1.5px solid rgba(151, 130, 200, 0.55);
    font-weight: 700;
}

.day-cell.is-checked {
    background: linear-gradient(135deg, rgba(95, 168, 164, 0.22), rgba(95, 168, 164, 0.32));
    color: #1f6b66;
    font-weight: 600;
}

.day-cell.is-today.is-checked {
    background: linear-gradient(135deg, rgba(151, 130, 200, 0.25), rgba(95, 168, 164, 0.35));
}

.day-dot {
    position: absolute;
    bottom: 2px;
    font-size: 0.55rem;
    color: #2f7b57;
}

/* ========== 奖励进度 ========== */
.bonus-progress {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 14px;
}

.bonus-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.85rem;
    color: var(--gray);
    background: rgba(255, 255, 255, 0.45);
    border-radius: 12px;
    padding: 8px 12px;
}

.bonus-item i {
    color: #b9c9c2;
}

.bonus-item.is-reached {
    color: #2f7b57;
    background: rgba(143, 207, 184, 0.14);
}

.bonus-item.is-reached i {
    color: #4caf50;
}

.bonus-text {
    flex: 1;
}

.bonus-remain {
    font-size: 0.78rem;
    color: #b6862a;
}

/* ========== 错误提示 ========== */
.checkin-error {
    margin-top: 12px;
    font-size: 0.85rem;
    color: #a03c3c;
    text-align: center;
}

/* ========== 签到按钮 ========== */
.checkin-footer {
    margin-top: 18px;
}

.checkin-btn {
    width: 100%;
    padding: 13px 20px;
    border: none;
    border-radius: 14px;
    font-size: 1.05rem;
    font-weight: 700;
    font-family: inherit;
    cursor: pointer;
    color: #fff;
    background: linear-gradient(135deg, #5ba8a4, #3a7d79);
    box-shadow: 0 6px 18px rgba(58, 125, 121, 0.3);
    transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

.checkin-btn:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 10px 24px rgba(58, 125, 121, 0.4);
}

.checkin-btn:disabled {
    cursor: not-allowed;
    opacity: 0.6;
}

.checkin-btn.is-done {
    background: linear-gradient(135deg, #8fcfb8, #5aa886);
    box-shadow: none;
}

.checkin-btn.is-bonus {
    background: linear-gradient(135deg, #b6862a, #8f6a1f);
    box-shadow: 0 6px 18px rgba(182, 134, 42, 0.35);
}
</style>

<!-- Toast 样式（与 TasksView / WrongQuestionsView 保持一致；非 scoped 以全局生效） -->
<style>
.custom-toast {
    position: fixed;
    top: 30px;
    left: 50%;
    transform: translateX(-50%) translateY(-20px);
    padding: 8px 25px;
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(12px);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
    font-size: 1.05rem;
    font-weight: 500;
    color: #2d4a3a;
    z-index: 9999;
    opacity: 0;
    transition: opacity 0.3s ease, transform 0.3s ease;
    border: 1px solid rgba(255, 255, 255, 0.6);
}

.custom-toast.toast-visible {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
}

.custom-toast.toast-success {
    border: 4px solid #4caf50;
    color: #1e4a2a;
}

.custom-toast.toast-error {
    border: 4px solid #ef5350;
    color: #7a2a2a;
}

.custom-toast.toast-info {
    border: 4px solid #42a5f5;
    color: #1a3a5a;
}
</style>
