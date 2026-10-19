<!--
  TasksView.vue - 今日任务仪表盘（任务中心 · Riso Campus 集点卡）
  ----------------------------------------------------------------------------
  业务行为（与后端契约）保持不变：
    1. 所有登录用户均可从导航栏进入任务中心
    2. 「每日签到」：签到记录、月度统计与积分发放由后端 /api/v1/checkins 承担；
       未签到时按钮显示「去签到」并可打开签到弹窗
    3. 奖励里程碑：每 200 积分一个节点，每 1000 积分为稀有卡节点；
       达到后点击领取，卡片写入 card_collection 并持久展示在收藏室
    4. 收藏室：按卡组陈列已获得卡片（每个卡组 1 稀有 + 6 普通）
    5. 里程碑三态：待领取（奶油黄印章）/ 已领取（黄绿印章 + 卡图）/ 未达成（灰色虚线锁定）
  UI：奶油纸张 + 墨黑文字 + 平涂印刷色块 + 墨线硬阴影 + 站内 /cards 拍立得照片墙。
-->
<template>
    <div class="page page--wide tasks-view">
        <!-- ========== 页头 ========== -->
        <header class="page-head page-head--row tasks-head">
            <div class="head-copy">
                <span class="eyebrow">NEST / MISSION CONTROL</span>
                <h1 class="page-title">任务中心</h1>
                <p class="page-sub">
                    完成每日、每周与限时任务，解锁分级成就，提升成长等级。
                </p>
            </div>

            <!-- 标签页：任务 / 成就 / 收藏室 -->
            <div class="seg tasks-tabs" role="tablist" aria-label="任务中心视图">
                <button type="button" class="seg__btn" role="tab" :aria-selected="activeTab === 'tasks'"
                    :class="{ 'is-active': activeTab === 'tasks' }" @click="switchTab('tasks')">
                    <i class="fas fa-list-check"></i> 任务
                </button>
                <button type="button" class="seg__btn" role="tab" :aria-selected="activeTab === 'achievements'"
                    :class="{ 'is-active': activeTab === 'achievements' }" @click="switchTab('achievements')">
                    <i class="fas fa-medal"></i> 成就
                </button>
                <button type="button" class="seg__btn" role="tab" :aria-selected="activeTab === 'collection'"
                    :class="{ 'is-active': activeTab === 'collection' }" @click="switchTab('collection')">
                    <i class="fas fa-images"></i> 收藏室
                    <span class="seg__count">{{ collectedCount }}</span>
                </button>
            </div>
        </header>

        <!-- ========== 加载骨架 ========== -->
        <div v-if="tasksStore.isLoading" class="tasks-skeletons" aria-busy="true" aria-label="正在加载任务数据">
            <div class="skeleton skeleton-hero"></div>
            <div class="grid grid--2">
                <div class="skeleton skeleton-card"></div>
                <div class="skeleton skeleton-card"></div>
            </div>
            <div class="skeleton skeleton-track"></div>
        </div>

        <template v-else>
            <!-- ========== 错误态 ========== -->
            <div v-if="tasksStore.loadError" class="empty tasks-error">
                <span class="empty__icon"><i class="fas fa-triangle-exclamation"></i></span>
                <p class="empty__title">任务数据加载失败</p>
                <p class="empty__text">{{ tasksStore.loadError }}</p>
                <div class="empty__actions">
                    <button type="button" class="btn btn--soft" @click="reload">
                        <i class="fas fa-rotate-right"></i> 重试
                    </button>
                </div>
            </div>

            <!-- ========== 成长等级横幅（不区分学科） ========== -->
            <section v-if="activeTab !== 'achievements'" class="level-banner panel" aria-label="成长等级">
                <LevelBadge :points="tasksStore.points" size="lg" />
                <div class="level-banner__side">
                    <div class="level-banner__stat">
                        <strong>{{ levelInfo.level }}</strong>
                        <span>当前等级</span>
                    </div>
                    <div class="level-banner__stat">
                        <strong>{{ levelInfo.toNext }}</strong>
                        <span>积分升级</span>
                    </div>
                    <button type="button" class="btn btn--soft btn--sm" @click="switchTab('achievements')">
                        <i class="fas fa-medal" aria-hidden="true"></i> 查看成就
                    </button>
                </div>
            </section>

            <!-- ========== 积分 Hero（集点卡票根） ========== -->
            <section v-if="activeTab !== 'achievements'" class="hero panel" aria-label="积分总览">
                <div class="hero-inner">
                    <div class="hero-main">
                        <span class="eyebrow">CURRENT POINTS</span>
                        <span class="hero-points-label">当前积分</span>
                        <div class="hero-number">
                            <CountUp :key="tasksStore.points" :value="tasksStore.points" :duration="900" />
                        </div>
                        <p v-if="nextMilestone" class="hero-next">
                            距 <strong>{{ nextMilestone.pts }}</strong> 积分里程碑还差
                            <strong>{{ pointsToNext }}</strong> 积分
                        </p>
                        <p v-else class="hero-next">全部里程碑已点亮，继续保持！</p>
                        <span class="progress progress--cyan hero-progress">
                            <span class="progress__bar" :style="{ width: nextProgressPercent + '%' }"></span>
                        </span>
                        <div class="hero-actions">
                            <button type="button" class="btn btn--soft btn--sm" @click="scrollToMilestones">
                                <i class="fas fa-trophy"></i> 里程碑轨迹
                            </button>
                            <button type="button" class="btn btn--ghost btn--sm" @click="switchTab('collection')">
                                <i class="fas fa-book-open"></i> 收藏室
                            </button>
                        </div>
                    </div>

                    <div class="hero-side">
                        <!-- 收藏进度环（票根虚线分隔） -->
                        <div class="ring-wrap" role="img"
                            :aria-label="`收藏进度 ${collectedPercent}%，已收集 ${collectedCount} / ${TOTAL_CARDS} 张卡片`">
                            <svg class="ring-svg" viewBox="0 0 120 120" aria-hidden="true">
                                <circle class="ring-track" cx="60" cy="60" r="50" />
                                <circle class="ring-bar" cx="60" cy="60" r="50" :stroke-dasharray="RING_CIRCUMFERENCE"
                                    :stroke-dashoffset="ringOffset" transform="rotate(-90 60 60)" />
                            </svg>
                            <div class="ring-center">
                                <strong>{{ collectedPercent }}%</strong>
                                <span>{{ collectedCount }} / {{ TOTAL_CARDS }}</span>
                            </div>
                        </div>

                        <ul class="hero-metrics">
                            <li>
                                <i class="fas fa-trophy" aria-hidden="true"></i>
                                <div><strong>{{ reachedCount }}</strong><span>已达成里程碑</span></div>
                            </li>
                            <li>
                                <i class="fas fa-gift" aria-hidden="true"></i>
                                <div><strong>{{ claimableCount }}</strong><span>待领取奖励</span></div>
                            </li>
                            <li>
                                <i class="fas fa-layer-group" aria-hidden="true"></i>
                                <div><strong>{{ completeSets }} / {{ CARD_SETS.length }}</strong><span>集齐卡组</span></div>
                            </li>
                        </ul>
                    </div>
                </div>

                <!-- 卡图拍立得条 -->
                <div class="hero-collage" aria-hidden="true">
                    <img v-for="(card, i) in heroCollage" :key="i" :src="card.img" alt="" loading="lazy"
                        @error="onImageError" />
                </div>
            </section>

            <!-- ========== 任务列表 ========== -->
            <template v-if="activeTab === 'tasks'">
                <TaskBoard />

                <!-- 签到卡 -->
                <section class="checkin-card panel" aria-label="每日签到状态">
                    <div class="panel-head">
                        <h2 class="panel-title">
                            <i class="fas fa-calendar-check checkin-title-icon" aria-hidden="true"></i> 每日签到
                        </h2>
                        <span class="chip" :class="checkinStore.todayCheckedIn ? 'chip--lime' : 'chip--amber'">
                            <i class="fas"
                                :class="checkinStore.todayCheckedIn ? 'fa-circle-check' : 'fa-hourglass-half'"
                                aria-hidden="true"></i>
                            {{ checkinStore.todayCheckedIn ? '今日已签到' : '今日未签到' }}
                        </span>
                    </div>

                    <div class="panel-body checkin-layout">
                        <div class="checkin-main">
                            <div class="streak">
                                <span class="streak-value">{{ streak }}</span>
                                <span class="streak-unit">天</span>
                                <span class="streak-label">连续签到</span>
                            </div>

                            <div class="checkin-facts">
                                <div class="fact">
                                    <strong>+{{ checkinStore.pointsPerCheckIn }}</strong>
                                    <span>每次签到</span>
                                </div>
                                <div class="fact">
                                    <strong>{{ checkinStore.monthCount }}</strong>
                                    <span>本月已签到</span>
                                </div>
                                <div class="fact">
                                    <strong>{{ nextBonus ? nextBonus - checkinStore.monthCount : 0 }}</strong>
                                    <span>{{ nextBonus ? `距第 ${nextBonus} 次奖励` : '月度奖励已拿满' }}</span>
                                </div>
                            </div>

                            <!-- 签到主按钮：E2E 通过可访问名定位 -->
                            <button v-if="!checkinStore.todayCheckedIn" type="button"
                                class="btn btn--warm checkin-cta" @click="checkinStore.openModal()">
                                <i class="fas fa-hand-point-up" aria-hidden="true"></i>
                                去签到
                                <span class="cta-bonus">+{{ checkinStore.pointsPerCheckIn }}</span>
                            </button>
                            <button v-else type="button" class="btn btn--soft checkin-cta"
                                @click="checkinStore.openModal()">
                                <i class="fas fa-calendar-days" aria-hidden="true"></i> 查看签到月历
                            </button>
                        </div>

                        <!-- 本月签到微缩日历 -->
                        <div class="mini-calendar" aria-label="本月签到日历">
                            <div class="mini-cal-head">
                                <span>{{ checkinStore.year || '—' }} 年 {{ checkinStore.month || '—' }} 月</span>
                                <span class="mini-cal-note">
                                    {{ checkinStore.loading ? '同步中…' : `${checkinStore.monthCount} 次签到` }}
                                </span>
                            </div>
                            <div v-if="miniCalendar.length" class="mini-cal-body">
                                <div class="mini-weekdays">
                                    <span v-for="w in WEEKDAYS" :key="w">{{ w }}</span>
                                </div>
                                <div class="mini-days">
                                    <span v-for="(cell, i) in miniCalendar" :key="i" class="mini-day"
                                        :class="{ 'is-blank': !cell.day, 'is-checked': cell.checked, 'is-today': cell.today }">
                                        {{ cell.day || '' }}
                                        <i v-if="cell.checked" class="fas fa-check mini-check" aria-hidden="true"></i>
                                    </span>
                                </div>
                            </div>
                            <div v-else class="loading-line">
                                <span class="spinner" aria-hidden="true"></span> 正在同步月历…
                            </div>
                        </div>
                    </div>
                </section>

                <!-- 里程碑轨迹 -->
                <section ref="milestoneSection" class="milestones-section panel" aria-label="里程碑轨迹">
                    <div class="panel-head">
                        <h2 class="panel-title">
                            <i class="fas fa-trophy milestone-title-icon" aria-hidden="true"></i> 里程碑轨迹
                        </h2>
                        <div class="legend">
                            <span class="chip chip--amber"><i class="fas fa-gift" aria-hidden="true"></i> 待领取</span>
                            <span class="chip chip--lime"><i class="fas fa-check" aria-hidden="true"></i> 已领取</span>
                            <span class="chip"><i class="fas fa-lock" aria-hidden="true"></i> 未达成</span>
                        </div>
                    </div>

                    <div class="panel-body">
                        <div class="track-bar">
                            <span class="section-note">
                                <i class="fas fa-route" aria-hidden="true"></i>
                                每 200 积分点亮一个节点 · 每 1000 积分为稀有卡节点
                            </span>
                            <div class="track-actions">
                                <button type="button" class="btn btn--sm btn--ghost" @click="scrollToClaimed">
                                    <i class="fas fa-arrow-left" aria-hidden="true"></i> 查看已领取
                                </button>
                                <button type="button" class="btn btn--sm btn--ghost" @click="scrollToLatest">
                                    <i class="fas fa-arrow-right" aria-hidden="true"></i> 回到最新
                                </button>
                            </div>
                        </div>

                        <div ref="trackScroll" class="reward-track-scroll">
                            <div class="reward-track">
                                <template v-for="(m, i) in milestones" :key="m.pts">
                                    <span v-if="i > 0" class="connector" :class="connectorClass(m)" aria-hidden="true"></span>
                                    <button type="button" class="milestone-node"
                                        :class="[milestoneClass(m), { 'is-rare': m.isRare }]"
                                        :data-pts="m.pts"
                                        :disabled="milestoneClass(m) !== 'is-claimable'"
                                        :aria-label="`${m.pts} 积分里程碑，${milestoneLabel(m)}`" @click="openClaim(m)">
                                        <span class="node-orb">
                                            <img v-if="claimedCard(m)" :src="claimedCard(m).img" alt="" loading="lazy"
                                                @error="onImageError" />
                                            <i v-else :class="milestoneIcon(m)" aria-hidden="true"></i>
                                            <span v-if="milestoneClass(m) === 'is-claimed'" class="node-check"
                                                aria-hidden="true"><i class="fas fa-check"></i></span>
                                        </span>
                                        <span class="node-pts">{{ m.pts }}</span>
                                        <span class="node-state">{{ milestoneLabel(m) }}</span>
                                    </button>
                                </template>
                            </div>
                        </div>

                        <p class="scroll-hint">
                            <i class="fas fa-arrows-left-right" aria-hidden="true"></i>
                            左右滚动查看全部 {{ milestones.length }} 个里程碑；点击亮色印章节点领取卡片。
                        </p>
                    </div>
                </section>

                <!-- 收藏室入口 -->
                <RevealOnScroll as="section" class="collection-entry panel" aria-label="收藏室入口">
                    <div class="entry-collage" aria-hidden="true">
                        <img v-for="(card, i) in entryCollage" :key="i" :src="card.img" alt="" loading="lazy"
                            @error="onImageError" />
                    </div>
                    <div class="entry-copy">
                        <span class="eyebrow">COLLECTION ROOM</span>
                        <h2 class="section-title entry-title">
                            <i class="fas fa-book-open" aria-hidden="true"></i> 收藏室
                        </h2>
                        <p class="entry-desc">
                            已收集 <strong>{{ collectedCount }}</strong> / {{ TOTAL_CARDS }} 张 ·
                            集齐 <strong>{{ completeSets }}</strong> / {{ CARD_SETS.length }} 套卡组
                        </p>
                        <span class="progress entry-progress">
                            <span class="progress__bar" :style="{ width: collectedPercent + '%' }"></span>
                        </span>
                        <div class="entry-actions">
                            <button type="button" class="btn btn--primary" @click="switchTab('collection')">
                                <i class="fas fa-arrow-right" aria-hidden="true"></i> 进入收藏室
                            </button>
                            <span class="entry-note">
                                <i class="fas fa-star" aria-hidden="true"></i> 每 1000 积分必得稀有卡
                            </span>
                        </div>
                    </div>
                </RevealOnScroll>
            </template>

            <!-- ========== 收藏室 / 成就 ========== -->
            <CollectionRoom v-else-if="activeTab === 'collection'" />
            <AchievementBoard v-else />
        </template>

        <!-- ========== 领取卡片弹窗（Teleport 到 body，避免被页面堆叠上下文裁切） ========== -->
        <Teleport to="body">
            <div v-if="claimVisible" class="backdrop" @click.self="closeClaim">
                <div class="modal claim-panel" role="dialog" aria-modal="true" aria-label="领取卡片">
                <button type="button" class="modal__close claim-close" aria-label="关闭领取弹窗" @click="closeClaim">
                    <i class="fas fa-times" aria-hidden="true"></i>
                </button>

                <!-- 稀有卡片散落纸屑 -->
                <div v-if="flipped && currentReward?.milestone.isRare" class="sparkles" aria-hidden="true">
                    <span v-for="(s, n) in sparklePositions" :key="n" class="sparkle"
                        :style="{ left: s.left, top: s.top, animationDelay: (n * 0.18) + 's' }"></span>
                </div>

                <!-- 卡片 3D 翻转 -->
                <div class="claim-scene" :class="{ 'is-flipped': flipped }">
                    <div class="claim-inner">
                        <!-- 背面（揭晓前） -->
                        <div class="claim-face claim-back">
                            <span class="back-glyph" aria-hidden="true"><i class="fas fa-gift"></i></span>
                            <span class="back-question" aria-hidden="true">?</span>
                            <span class="back-text">揭晓卡片中…</span>
                        </div>

                        <!-- 正面（拍立得卡图 + 名称） -->
                        <div class="claim-face claim-front" :class="{ 'is-rare': currentReward?.milestone.isRare }">
                            <div class="front-photo">
                                <img v-if="currentReward?.card" :src="currentReward.card.img"
                                    :alt="currentReward.card.name" class="front-img" @error="onImageError" />
                                <span v-else class="front-empty" aria-hidden="true"><i class="fas fa-gift"></i></span>
                            </div>
                            <div class="front-caption">
                                <span v-if="currentReward?.card" class="card-type"
                                    :class="{ 'is-rare': currentReward.milestone.isRare }">
                                    {{ currentReward.milestone.isRare ? '✦ 稀有卡片' : '普通卡片' }}
                                </span>
                                <h3 v-if="currentReward?.card" class="card-name">
                                    {{ currentReward.card.name }}
                                </h3>
                                <span v-if="currentReward?.duplicate" class="card-duplicate">
                                    <i class="fas fa-repeat" aria-hidden="true"></i> 该稀有度卡片已集齐，本次为重复卡
                                </span>
                                <span class="card-points">达成 {{ currentReward?.milestone.pts }} 积分奖励</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="claim-footer">
                    <button v-if="!flipped" type="button" class="btn claim-btn" disabled>
                        <i class="fas fa-spinner fa-spin" aria-hidden="true"></i> 揭晓中…
                    </button>
                    <button v-else type="button" class="btn btn--warm claim-btn" :disabled="claimingMilestone"
                        @click="confirmClaim">
                        <i class="fas fa-hand-holding-heart" aria-hidden="true"></i>
                        {{ claimingMilestone ? '收集中…' : '收下卡片' }}
                    </button>
                </div>
                </div>
            </div>
        </Teleport>
    </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useCheckinStore } from '@/stores/checkinStore'
import { useTasksStore } from '@/stores/tasksStore'
import { CARD_SETS, buildMilestones, findCard } from '@/data/cardCatalog'
import { showToast } from '@/utils/toast'
import { levelFromPoints } from '@/utils/level'
import CountUp from '@/components/ui/CountUp.vue'
import LevelBadge from '@/components/ui/LevelBadge.vue'
import RevealOnScroll from '@/components/ui/RevealOnScroll.vue'
import CollectionRoom from '@/components/tasks/CollectionRoom.vue'
import TaskBoard from '@/components/tasks/TaskBoard.vue'
import AchievementBoard from '@/components/tasks/AchievementBoard.vue'

const authStore = useAuthStore()
const checkinStore = useCheckinStore()
const tasksStore = useTasksStore()
const route = useRoute()
const router = useRouter()

// ========== 常量 ==========
const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
const MAX_DISPLAY = 6000 // 进度条展示至 6000，超出当前积分的里程碑用于演示「未达到条件」
const RING_RADIUS = 50
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS
const TOTAL_CARDS = CARD_SETS.reduce((sum, set) => sum + 1 + set.commons.length, 0)

// ========== 里程碑 ==========
// 生成 200 ~ 6000 的里程碑；每 1000 积分为稀有卡片节点，其余为普通卡片节点
const milestones = buildMilestones(MAX_DISPLAY)

// ========== 标签页 ==========
const activeTab = ref('tasks')
const levelInfo = computed(() => levelFromPoints(tasksStore.points))
const VALID_TABS = ['tasks', 'achievements', 'collection']

function applyTabFromQuery() {
    const tab = String(route.query.tab || '')
    if (VALID_TABS.includes(tab)) activeTab.value = tab
}

watch(() => route.query.tab, applyTabFromQuery)

const collection = computed(() => tasksStore.collection)
const collectedCount = computed(() => collection.value.length)
const collectedPercent = computed(() => Math.round((collectedCount.value / TOTAL_CARDS) * 100))
const reachedCount = computed(() => milestones.filter(m => m.pts <= tasksStore.points).length)
const claimableCount = computed(() =>
    milestones.filter(m => m.pts <= tasksStore.points && !tasksStore.claimedMilestones.has(m.pts)).length)

const completeSets = computed(() => CARD_SETS.filter(set => {
    const owned = collection.value.filter(row => row.set_key === set.key).length
    return owned >= 7
}).length)

const nextMilestone = computed(() => milestones.find(m => m.pts > tasksStore.points) || null)
const pointsToNext = computed(() => nextMilestone.value ? nextMilestone.value.pts - tasksStore.points : 0)
const nextProgressPercent = computed(() => {
    if (!nextMilestone.value) return 100
    return Math.min(100, Math.round((tasksStore.points / nextMilestone.value.pts) * 100))
})
const ringOffset = computed(() =>
    RING_CIRCUMFERENCE * (1 - Math.min(100, collectedPercent.value) / 100))

// ========== 签到推导信息 ==========
const nextBonus = computed(() =>
    checkinStore.bonusMilestones.find(m => m > checkinStore.monthCount) ?? null)

/** 本月连续签到天数（已加载月份内、从服务端今日/昨日向前连续计数） */
const streak = computed(() => {
    if (!checkinStore.today || !checkinStore.dates.length) return 0
    const daySet = new Set(checkinStore.dates)
    const cursor = new Date(`${checkinStore.today}T00:00:00`)
    if (!daySet.has(checkinStore.today)) cursor.setDate(cursor.getDate() - 1)
    let count = 0
    while (count < 31) {
        if (!checkinStore.year || !checkinStore.month) break
        if (cursor.getFullYear() !== checkinStore.year || cursor.getMonth() + 1 !== checkinStore.month) break
        const iso = formatDate(cursor)
        if (!daySet.has(iso)) break
        count += 1
        cursor.setDate(cursor.getDate() - 1)
    }
    return count
})

function formatDate(date) {
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
}

/** 本月签到微缩日历：周一开头，已签到 / 今日高亮 */
const miniCalendar = computed(() => {
    const { year, month } = checkinStore
    if (!year || !month) return []
    const checked = new Set(checkinStore.dates.map(d => Number(d.slice(8, 10))))
    const today = checkinStore.today
    const firstWeekday = (new Date(year, month - 1, 1).getDay() + 6) % 7
    const daysInMonth = new Date(year, month, 0).getDate()
    const cells = [
        ...Array(firstWeekday).fill(null),
        ...Array.from({ length: daysInMonth }, (_, i) => i + 1)
    ]
    while (cells.length % 7 !== 0) cells.push(null)
    return cells.map(day => ({
        day,
        checked: day ? checked.has(day) : false,
        today: day && today
            ? today === `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
            : false
    }))
})

// ========== 卡组拼贴（站内 /cards 图片） ==========
const heroCollage = computed(() =>
    CARD_SETS.flatMap(set => [set.rare, ...set.commons.slice(0, 2)]).slice(0, 9))
const entryCollage = computed(() => [
    ...CARD_SETS.map(set => set.rare),
    CARD_SETS[0].commons[0]
])

/** 图片加载失败时隐藏 img，露出纸色兜底 */
function onImageError(event) {
    const img = event?.target
    if (img) img.style.opacity = '0'
}

// ========== 已领取里程碑 → 已获得卡图 ==========
const claimedByPoints = computed(() => {
    const map = new Map()
    for (const row of tasksStore.collection) {
        map.set(Number(row.milestone_points), findCard(row.set_key, row.card_key))
    }
    return map
})

function claimedCard(m) {
    return claimedByPoints.value.get(m.pts) || null
}

// ========== 里程碑状态 ==========
function milestoneClass(m) {
    if (m.pts > tasksStore.points) return 'is-locked'
    if (tasksStore.claimedMilestones.has(m.pts)) return 'is-claimed'
    return 'is-claimable'
}

function milestoneIcon(m) {
    if (m.pts > tasksStore.points) return 'fas fa-lock'
    if (tasksStore.claimedMilestones.has(m.pts)) return 'fas fa-check'
    return m.isRare ? 'fas fa-star' : 'fas fa-gift'
}

function milestoneLabel(m) {
    if (m.pts > tasksStore.points) return '未达成'
    if (tasksStore.claimedMilestones.has(m.pts)) return '已领取'
    return m.isRare ? '稀有·待领取' : '待领取'
}

function connectorClass(m) {
    return m.pts <= tasksStore.points ? 'is-reached' : 'is-locked'
}

// ========== 里程碑轨迹滚动 ==========
const trackScroll = ref(null)
const milestoneSection = ref(null)

function scrollToClaimed() {
    trackScroll.value?.scrollTo({ left: 0, behavior: 'smooth' })
}

function scrollToLatest() {
    nextTick(() => {
        const el = trackScroll.value
        if (!el) return
        // 定位到当前进度附近的节点，而不是整条轨迹的最右端
        const nextIndex = milestones.findIndex(m => m.pts > tasksStore.points)
        const targetIndex = nextIndex === -1 ? milestones.length - 1 : Math.max(0, nextIndex)
        const node = el.querySelectorAll('.milestone-node')[targetIndex]
        if (!node) {
            el.scrollTo({ left: el.scrollWidth, behavior: 'smooth' })
            return
        }
        const left = node.offsetLeft - el.clientWidth / 2 + node.clientWidth / 2
        el.scrollTo({ left: Math.max(0, left), behavior: 'smooth' })
    })
}

function scrollToMilestones() {
    milestoneSection.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function switchTab(tab) {
    activeTab.value = tab
    router.replace({ path: '/tasks', query: tab === 'tasks' ? {} : { tab } })
    if (tab === 'tasks') scrollToLatest()
    if (tab === 'achievements') window.scrollTo({ top: 0, behavior: 'smooth' })
}

// ========== 领取卡片 ==========
const claimVisible = ref(false)
const flipped = ref(false)
const currentReward = ref(null)
const claimingMilestone = ref(false)
let flipTimer = null

async function openClaim(m) {
    if (m.pts > tasksStore.points || tasksStore.claimedMilestones.has(m.pts) || claimingMilestone.value) return
    currentReward.value = { milestone: m, card: null, duplicate: false }
    flipped.value = false
    claimVisible.value = true
    claimingMilestone.value = true
    try {
        const reward = await tasksStore.claimMilestone(authStore.currentUser, m)
        currentReward.value = { milestone: m, ...reward }
        flipTimer = setTimeout(() => {
            flipped.value = true
        }, 900)
    } catch (e) {
        claimVisible.value = false
        showToast(e.message, 'error')
    } finally {
        claimingMilestone.value = false
    }
}

function confirmClaim() {
    if (!currentReward.value || !flipped.value || claimingMilestone.value) return
    const { card, duplicate } = currentReward.value
    claimVisible.value = false
    showToast(
        duplicate ? `卡片「${card.name}」为重复卡，已计入收藏` : `卡片「${card.name}」已收入收藏室`,
        duplicate ? 'info' : 'success'
    )
    scrollToLatest()
}

function closeClaim() {
    // 揭晓动画进行中不允许关闭，避免状态错乱
    if (!flipped.value || claimingMilestone.value) return
    claimVisible.value = false
}

// 稀有卡片周围散落的纸屑位置
const sparklePositions = [
    { left: '-10%', top: '-16%' },
    { left: '86%', top: '-10%' },
    { left: '-6%', top: '66%' },
    { left: '90%', top: '58%' },
    { left: '38%', top: '-24%' },
    { left: '56%', top: '102%' },
    { left: '4%', top: '24%' },
    { left: '84%', top: '28%' }
]

// ========== 数据加载 ==========
async function reload() {
    await tasksStore.load(authStore.currentUser)
    await nextTick()
    scrollToLatest()
    if (tasksStore.loadError) showToast(tasksStore.loadError, 'error')
}

onMounted(() => {
    applyTabFromQuery()
    checkinStore.loadMonth() // 首屏即显示今日签到状态（去签到 / 今日已签到）
    tasksStore.load(authStore.currentUser).then(async () => {
        await nextTick()
        // 初始停靠当前进度附近
        if (activeTab.value === 'tasks') scrollToLatest()
        if (tasksStore.loadError) showToast(tasksStore.loadError, 'error')
    })
})

onBeforeUnmount(() => {
    if (flipTimer) clearTimeout(flipTimer)
})
</script>

<style scoped>
/* ========== 成长等级横幅 ========== */
.level-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    flex-wrap: wrap;
    padding: 22px 26px;
    margin-bottom: 18px;
}

.level-banner__side {
    display: flex;
    align-items: center;
    gap: 20px;
    flex-wrap: wrap;
}

.level-banner__stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding-left: 18px;
    border-left: 1.5px solid var(--line-strong);
}

.level-banner__stat strong {
    font-family: var(--font-display);
    font-size: 1.5rem;
    font-weight: 800;
    line-height: 1;
    color: var(--ink);
}

.level-banner__stat span {
    font-size: 0.72rem;
    color: var(--text-3);
}

@media (max-width: 768px) {
    .level-banner {
        padding: 18px;
    }

    .level-banner__side {
        width: 100%;
        justify-content: space-between;
    }
}
.tasks-view {
    position: relative;
}

/* ========== 页头 / 标签 ========== */
.tasks-head {
    align-items: flex-end;
}

.head-copy {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.tasks-tabs {
    flex-shrink: 0;
}

.tasks-tabs .seg__btn {
    height: 38px;
}

/* ========== 加载骨架 ========== */
.tasks-skeletons {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.skeleton-hero {
    height: 300px;
    border-radius: var(--r-lg);
}

.skeleton-card {
    height: 172px;
    border-radius: var(--r-lg);
}

.skeleton-track {
    height: 196px;
    border-radius: var(--r-lg);
}

.tasks-error {
    margin-bottom: 24px;
}

/* ========== Hero（集点卡票根） ========== */
.hero {
    position: relative;
    margin-bottom: 26px;
    padding: clamp(22px, 3.2vw, 34px);
    overflow: hidden;
}

.hero-inner {
    position: relative;
    z-index: 2;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 32px;
    align-items: center;
}

.hero-main {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
}

.hero-points-label {
    margin-top: 10px;
    font-family: var(--font-mono);
    font-size: 0.78rem;
    font-weight: 600;
    letter-spacing: 0.16em;
    color: var(--text-3);
}

.hero-number {
    margin-top: 2px;
    font-family: var(--font-display);
    font-size: clamp(3.6rem, 8vw, 5.8rem);
    font-weight: 800;
    line-height: 0.9;
    letter-spacing: -0.05em;
    font-variant-numeric: tabular-nums;
    color: var(--ink);
    text-shadow: 6px 6px 0 var(--butter);
}

.hero-next {
    margin-top: 18px;
    color: var(--text-2);
    font-size: 0.9rem;
}

.hero-next strong {
    color: var(--vermilion);
    font-family: var(--font-display);
    font-size: 1.08em;
    font-variant-numeric: tabular-nums;
}

.hero-progress {
    width: min(440px, 100%);
    height: 12px;
    margin-top: 16px;
}

.hero-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    margin-top: 20px;
}

.hero-side {
    display: flex;
    align-items: center;
    gap: 26px;
    padding-left: 28px;
    border-left: 2px dashed var(--line-strong);
}

.ring-wrap {
    position: relative;
    width: 150px;
    height: 150px;
    flex-shrink: 0;
}

.ring-svg {
    width: 100%;
    height: 100%;
}

.ring-track {
    fill: none;
    stroke: var(--paper-2);
    stroke-width: 12;
}

.ring-bar {
    fill: none;
    stroke: var(--vermilion);
    stroke-width: 12;
    stroke-linecap: butt;
    transition: stroke-dashoffset 1s var(--ease-out);
}

.ring-center {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
}

.ring-center strong {
    font-family: var(--font-display);
    font-size: 1.9rem;
    font-weight: 800;
    line-height: 1;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.ring-center span {
    font-family: var(--font-mono);
    font-size: 0.68rem;
    color: var(--text-3);
}

.hero-metrics {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.hero-metrics li {
    display: flex;
    align-items: center;
    gap: 10px;
}

.hero-metrics i {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    flex-shrink: 0;
    border: 1.5px solid var(--ink);
    border-radius: 9px;
    background: var(--butter);
    color: var(--ink);
    font-size: 0.84rem;
    box-shadow: 2px 2px 0 var(--ink);
}

.hero-metrics li:nth-child(2) i {
    background: var(--lime);
}

.hero-metrics li:nth-child(3) i {
    background: var(--sky);
}

.hero-metrics strong {
    display: block;
    font-family: var(--font-display);
    font-size: 1rem;
    font-weight: 800;
    line-height: 1.15;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.hero-metrics span {
    display: block;
    font-size: 0.72rem;
    color: var(--text-3);
}

/* 卡图拍立得条 */
.hero-collage {
    position: relative;
    z-index: 2;
    display: flex;
    gap: 14px;
    margin-top: 26px;
    padding-top: 18px;
    border-top: 2px dashed var(--line-strong);
    overflow: hidden;
}

.hero-collage img {
    flex: 0 0 auto;
    width: 84px;
    aspect-ratio: 3 / 4;
    object-fit: cover;
    border: 5px solid var(--paper-1);
    outline: 1.5px solid var(--ink);
    background: var(--paper-2);
    box-shadow: 4px 4px 0 rgba(28, 26, 20, 0.85);
    transform: rotate(var(--tilt, 0deg));
    transition: transform var(--dur) var(--ease-out);
}

.hero-collage img:nth-child(1) { --tilt: -4deg; }
.hero-collage img:nth-child(2) { --tilt: 3deg; }
.hero-collage img:nth-child(3) { --tilt: -2deg; }
.hero-collage img:nth-child(4) { --tilt: 2.4deg; }
.hero-collage img:nth-child(5) { --tilt: -3.2deg; }
.hero-collage img:nth-child(6) { --tilt: 1.8deg; }
.hero-collage img:nth-child(7) { --tilt: -1.6deg; }
.hero-collage img:nth-child(8) { --tilt: 3.4deg; }
.hero-collage img:nth-child(9) { --tilt: -2.6deg; }

.hero-collage img:hover {
    transform: rotate(0deg) translateY(-4px);
}

/* ========== 签到卡 ========== */
.checkin-card {
    margin-bottom: 26px;
}

.checkin-title-icon {
    color: var(--vermilion);
}

.checkin-layout {
    display: grid;
    grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
    gap: 28px;
    align-items: center;
}

.checkin-main {
    display: flex;
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
}

.streak {
    display: flex;
    align-items: baseline;
    gap: 8px;
}

.streak-value {
    font-family: var(--font-display);
    font-size: clamp(2.6rem, 5vw, 3.6rem);
    font-weight: 800;
    line-height: 1;
    letter-spacing: -0.03em;
    color: var(--vermilion);
    font-variant-numeric: tabular-nums;
}

.streak-unit {
    font-family: var(--font-display);
    font-size: 1rem;
    font-weight: 800;
    color: var(--ink);
}

.streak-label {
    margin-left: 4px;
    font-size: 0.82rem;
    color: var(--text-3);
}

.checkin-facts {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    width: 100%;
}

.fact {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 12px 14px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
}

.fact:nth-child(1) {
    background: var(--butter);
}

.fact:nth-child(2) {
    background: var(--sky);
}

.fact:nth-child(3) {
    background: var(--lime);
}

.fact strong {
    font-family: var(--font-display);
    font-size: 1.18rem;
    font-weight: 800;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.fact span {
    font-size: 0.72rem;
    font-weight: 600;
    color: var(--ink-soft);
}

.checkin-cta {
    height: 50px;
    padding: 0 26px;
    font-size: 0.96rem;
    font-weight: 800;
}

.cta-bonus {
    padding: 2px 10px;
    border-radius: var(--r-full);
    background: var(--ink);
    color: var(--paper-1);
    font-family: var(--font-mono);
    font-size: 0.82rem;
}

.mini-calendar {
    padding: 16px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-1);
    box-shadow: var(--shadow-card);
}

.mini-cal-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;
    font-family: var(--font-display);
    font-size: 0.84rem;
    font-weight: 800;
    color: var(--ink);
}

.mini-cal-note {
    font-family: var(--font-mono);
    font-size: 0.66rem;
    font-weight: 400;
    color: var(--text-3);
}

.mini-weekdays {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    margin-bottom: 5px;
}

.mini-weekdays span {
    text-align: center;
    font-family: var(--font-mono);
    font-size: 0.6rem;
    color: var(--text-3);
}

.mini-days {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 3px;
}

.mini-day {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    aspect-ratio: 1;
    border: 1px solid var(--line);
    border-radius: 5px;
    background: var(--paper-1);
    color: var(--text-2);
    font-size: 0.72rem;
    font-weight: 600;
}

.mini-day.is-blank {
    visibility: hidden;
}

.mini-day.is-checked {
    border-color: var(--ink);
    background: var(--paper-2);
    color: var(--ink);
    font-weight: 800;
}

.mini-check {
    position: absolute;
    right: 2px;
    bottom: 0;
    color: var(--vermilion);
    font-size: 0.42rem;
}

.mini-day.is-today {
    border: 2px solid var(--ink);
    background: var(--butter);
    color: var(--ink);
    font-weight: 800;
}

/* ========== 里程碑轨迹（印章本） ========== */
.milestones-section {
    margin-bottom: 26px;
}

.milestone-title-icon {
    color: var(--vermilion);
}

.legend {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.track-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 16px;
}

.track-actions {
    display: flex;
    gap: 8px;
}

.reward-track-scroll {
    overflow-x: auto;
    padding: 20px 16px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    background-color: var(--paper-2);
    background-image: radial-gradient(rgba(28, 26, 20, 0.16) 1px, transparent 1.2px);
    background-size: 16px 16px;
    box-shadow: inset 0 0 0 6px var(--paper-1), 3px 3px 0 var(--ink);
    scrollbar-width: thin;
    scrollbar-color: rgba(28, 26, 20, 0.55) transparent;
}

.reward-track {
    display: flex;
    align-items: center;
    width: max-content;
    padding: 6px 2px;
}

.connector {
    width: 38px;
    height: 2px;
    flex-shrink: 0;
}

.connector.is-reached {
    background: var(--ink);
}

.connector.is-locked {
    background: repeating-linear-gradient(90deg, var(--text-4) 0 5px, transparent 5px 10px);
}

.milestone-node {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    width: 98px;
    flex-shrink: 0;
    padding: 0;
    border: 0;
    background: none;
    color: inherit;
    font-family: inherit;
    cursor: default;
}

.milestone-node.is-claimable {
    cursor: pointer;
}

.milestone-node:focus-visible {
    outline: none;
}

.milestone-node:focus-visible .node-orb {
    outline: 2.5px solid var(--cobalt);
    outline-offset: 3px;
}

/* 彩色圆章：墨线 + 错位硬阴影 */
.node-orb {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 64px;
    height: 64px;
    border: 2px solid var(--ink);
    border-radius: 50%;
    background: var(--paper-1);
    color: var(--ink);
    font-size: 1.2rem;
    overflow: visible;
    box-shadow: 3px 3px 0 var(--ink);
    transition: transform var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
}

.node-orb img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 50%;
}

.node-check {
    position: absolute;
    right: -4px;
    bottom: -2px;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border: 1.5px solid var(--ink);
    border-radius: 50%;
    background: var(--lime);
    color: var(--ink);
    font-size: 0.55rem;
}

/* 未达成：灰色虚线锁定 */
.milestone-node.is-locked .node-orb {
    border-style: dashed;
    border-color: var(--text-4);
    background: var(--paper-2);
    color: var(--text-4);
    box-shadow: none;
}

/* 待领取：奶油黄印章 */
.milestone-node.is-claimable .node-orb {
    background: var(--butter);
    box-shadow: 4px 4px 0 var(--ink);
}

/* 待领取稀有：朱红印章 */
.milestone-node.is-claimable.is-rare .node-orb {
    background: var(--vermilion);
    color: var(--paper-1);
}

.milestone-node.is-claimable:hover .node-orb {
    transform: translate(-2px, -2px) rotate(-5deg);
    box-shadow: 6px 6px 0 var(--ink);
}

/* 已领取：黄绿印章 + 卡图 */
.milestone-node.is-claimed .node-orb {
    background: var(--lime);
}

.node-pts {
    font-family: var(--font-mono);
    font-size: 0.76rem;
    font-weight: 700;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.milestone-node.is-locked .node-pts {
    color: var(--text-4);
}

.node-state {
    font-family: var(--font-mono);
    font-size: 0.62rem;
    font-weight: 600;
    color: var(--text-3);
    white-space: nowrap;
}

.milestone-node.is-claimable .node-state {
    color: var(--vermilion);
    font-weight: 700;
}

.milestone-node.is-claimed .node-state {
    color: var(--green);
}

.milestone-node.is-locked .node-state {
    color: var(--text-4);
}

.scroll-hint {
    margin-top: 12px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.72rem;
    color: var(--text-3);
}

/* ========== 收藏室入口（拍立得照片墙） ========== */
.collection-entry {
    display: grid;
    grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
    min-height: 224px;
    overflow: hidden;
}

.entry-collage {
    position: relative;
    display: flex;
    flex-wrap: wrap;
    align-content: center;
    justify-content: center;
    gap: 10px;
    padding: 22px;
    background: var(--paper-2);
    border-right: 1.5px solid var(--ink);
}

.entry-collage img {
    width: 76px;
    aspect-ratio: 3 / 4;
    object-fit: cover;
    border: 4px solid var(--paper-1);
    outline: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 rgba(28, 26, 20, 0.85);
    transform: rotate(var(--tilt, 0deg));
}

.entry-collage img:nth-child(1) { --tilt: -4deg; }
.entry-collage img:nth-child(2) { --tilt: 3deg; }
.entry-collage img:nth-child(3) { --tilt: -2deg; }
.entry-collage img:nth-child(4) { --tilt: 2.4deg; }
.entry-collage img:nth-child(5) { --tilt: -3deg; }
.entry-collage img:nth-child(6) { --tilt: 1.8deg; }

.entry-copy {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 12px;
    padding: 30px;
    background: var(--paper-1);
}

.entry-title {
    font-size: 1.36rem;
}

.entry-title i {
    color: var(--cobalt);
}

.entry-desc {
    color: var(--text-2);
    font-size: 0.85rem;
}

.entry-desc strong {
    color: var(--ink);
    font-family: var(--font-mono);
}

.entry-progress {
    height: 12px;
    max-width: 420px;
}

.entry-actions {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
    margin-top: 4px;
}

.entry-note {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.74rem;
    color: var(--text-3);
}

.entry-note i {
    color: var(--vermilion);
}

/* ========== 领取卡片弹窗 ========== */
.claim-panel {
    width: min(440px, 100%);
    padding: 26px 22px 22px;
    border-radius: var(--r-lg);
    background: var(--paper-1);
}

.claim-close {
    position: absolute;
    top: 14px;
    right: 14px;
    z-index: 6;
}

.claim-scene {
    position: relative;
    width: 240px;
    height: 330px;
    margin: 6px auto 0;
    perspective: 1100px;
    z-index: 2;
}

.claim-inner {
    position: relative;
    width: 100%;
    height: 100%;
    transform-style: preserve-3d;
    transition: transform 0.8s cubic-bezier(0.4, 1.1, 0.5, 1);
}

.claim-scene.is-flipped .claim-inner {
    transform: rotateY(180deg);
}

.claim-face {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    border: 2px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-1);
    box-shadow: var(--shadow-card);
    overflow: hidden;
    backface-visibility: hidden;
    -webkit-backface-visibility: hidden;
}

/* 背面：印点纸 + 大问号 */
.claim-back {
    align-items: center;
    justify-content: center;
    gap: 12px;
    background-color: var(--paper-2);
    background-image: radial-gradient(rgba(28, 26, 20, 0.16) 1.2px, transparent 1.4px);
    background-size: 14px 14px;
}

.back-glyph {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    border: 2px solid var(--ink);
    border-radius: 50%;
    background: var(--butter);
    color: var(--ink);
    font-size: 1.5rem;
    box-shadow: 3px 3px 0 var(--ink);
}

.back-question {
    font-family: var(--font-display);
    font-size: 3.6rem;
    font-weight: 800;
    line-height: 1;
    color: var(--ink);
    text-shadow: 5px 5px 0 var(--lime);
}

.back-text {
    font-family: var(--font-mono);
    font-size: 0.74rem;
    letter-spacing: 0.22em;
    color: var(--text-3);
}

/* 正面：奶油纸拍立得 */
.claim-front {
    transform: rotateY(180deg);
    background: var(--paper-1);
}

.claim-front.is-rare {
    background: var(--butter);
}

.front-photo {
    position: relative;
    flex: 1;
    min-height: 0;
    margin: 10px 10px 0;
    border: 1.5px solid var(--ink);
    border-radius: 4px;
    overflow: hidden;
    background: var(--paper-2);
}

.claim-front.is-rare .front-photo {
    border-width: 2px;
}

.front-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.front-empty {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-4);
    font-size: 2rem;
}

.front-caption {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: 12px 14px 16px;
    text-align: center;
}

.card-type {
    padding: 3px 12px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-1);
    color: var(--ink);
    font-size: 0.68rem;
    font-weight: 800;
    box-shadow: 2px 2px 0 var(--ink);
}

.card-type.is-rare {
    background: var(--vermilion);
    color: var(--paper-1);
}

.card-name {
    font-family: var(--font-display);
    font-size: 1.1rem;
    font-weight: 800;
    line-height: 1.3;
    color: var(--ink);
}

.card-duplicate {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 3px 10px;
    border: 1.5px dashed var(--ink-soft);
    border-radius: var(--r-full);
    background: var(--paper-1);
    color: var(--ink-soft);
    font-size: 0.68rem;
}

.card-points {
    font-family: var(--font-mono);
    font-size: 0.7rem;
    color: var(--text-3);
}

.claim-footer {
    position: relative;
    z-index: 2;
    display: flex;
    justify-content: center;
    margin-top: 20px;
}

.claim-btn {
    min-width: 210px;
    height: 50px;
    font-size: 0.96rem;
    font-weight: 800;
}

/* 稀有卡片散落纸屑 */
.sparkles {
    position: absolute;
    inset: 0;
    z-index: 3;
    pointer-events: none;
}

.sparkle {
    position: absolute;
    width: 10px;
    height: 10px;
    border: 1.5px solid var(--ink);
    background: var(--butter);
    transform: translate(-50%, -50%) rotate(45deg);
    animation: sparkle-pop 1.5s ease-in-out infinite;
}

.sparkle:nth-child(3n + 1) {
    background: var(--vermilion);
}

.sparkle:nth-child(3n + 2) {
    background: var(--cobalt);
}

.sparkle:nth-child(3n) {
    background: var(--lime);
}

@keyframes sparkle-pop {
    0% {
        opacity: 0;
        transform: translate(-50%, -50%) rotate(45deg) scale(0.4);
    }

    45% {
        opacity: 1;
        transform: translate(-50%, -50%) rotate(45deg) scale(1.15);
    }

    100% {
        opacity: 0;
        transform: translate(-50%, -50%) rotate(45deg) scale(0.5);
    }
}

/* ========== 响应式 ========== */
@media (max-width: 1080px) {
    .hero-inner {
        grid-template-columns: minmax(0, 1fr);
        gap: 26px;
    }

    .hero-side {
        border-left: 0;
        padding-left: 0;
        gap: 22px;
    }

    .checkin-layout {
        grid-template-columns: minmax(0, 1fr);
        gap: 22px;
    }

    .mini-calendar {
        max-width: 420px;
    }
}

@media (max-width: 760px) {
    .tasks-head {
        align-items: flex-start;
    }

    .tasks-tabs {
        width: 100%;
    }

    .tasks-tabs .seg__btn {
        flex: 1;
        justify-content: center;
    }

    .hero {
        padding: 22px 18px;
    }

    .hero-number {
        font-size: clamp(3rem, 16vw, 4.2rem);
        text-shadow: 4px 4px 0 var(--butter);
    }

    .hero-side {
        flex-direction: column;
        align-items: flex-start;
        gap: 18px;
    }

    .ring-wrap {
        width: 126px;
        height: 126px;
    }

    .hero-metrics {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 10px;
        width: 100%;
    }

    .hero-metrics li {
        flex-direction: column;
        align-items: flex-start;
        gap: 6px;
    }

    .hero-collage {
        gap: 10px;
        margin-top: 20px;
        padding-top: 14px;
    }

    .hero-collage img {
        width: 64px;
    }

    .checkin-facts {
        grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .fact {
        padding: 10px 12px;
    }

    .checkin-cta {
        width: 100%;
    }

    .track-bar {
        align-items: flex-start;
        flex-direction: column;
    }

    .collection-entry {
        grid-template-columns: minmax(0, 1fr);
    }

    .entry-collage {
        border-right: 0;
        border-bottom: 1.5px solid var(--ink);
        padding: 18px;
        gap: 8px;
    }

    .entry-collage img {
        width: 64px;
    }

    .entry-copy {
        padding: 22px 18px;
    }

    .claim-scene {
        width: 220px;
        height: 312px;
    }
}

@media (max-width: 400px) {
    .checkin-facts {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .hero-metrics {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}
</style>
