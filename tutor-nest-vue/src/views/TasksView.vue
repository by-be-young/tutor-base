<!--
  TasksView.vue - 任务系统（演示版）
  ----------------------------------------------------------------------------
  功能说明：
    1. 仅超级管理员 young 可从导航栏进入
    2. 完成任务 +100 积分（演示模式：young 积分固定 5000，不会消耗也不会变多）
    3. 奖励进度条：每 200 积分获得一张普通卡片；每 1000 积分获得一张随机稀有卡片
       （稀有卡片随机抽取，可获得已有卡片）
    4. 演示模式：不涉及数据库与后端；领取状态仅保存在内存中，
       刷新页面后恢复为「未领取」，不会进入统计
    5. 状态颜色：待领取（金色发光） / 已领取（绿色） / 未达到条件（灰色锁定）
-->
<template>
    <div class="task-container">
        <!-- 非 young 访问提示 -->
        <div v-if="!isSuperAdmin" class="empty-tip">
            该功能仅超级管理员可见，当前账号无权访问。
        </div>

        <template v-else>
            <!-- 头部 -->
            <div class="task-header">
                <h1 class="task-title">任务中心</h1>
                <p class="task-subtitle">
                    完成任务赚取积分，积分可兑换卡片奖励：每 200 积分获得一张普通卡片，每 1000 积分获得一张随机稀有卡片。
                </p>
            </div>

            <!-- 统计栏 -->
            <div class="task-stats">
                <div class="task-stat-card is-points">
                    <div class="task-stat-number">{{ POINTS }}</div>
                    <div class="task-stat-label">当前积分 <span class="stat-badge">固定</span></div>
                </div>
                <div class="task-stat-card">
                    <div class="task-stat-number">{{ reachedCount }}/{{ milestones.length }}</div>
                    <div class="task-stat-label">已达成里程碑</div>
                </div>
                <div class="task-stat-card is-claimed">
                    <div class="task-stat-number">{{ claimedIds.size }}</div>
                    <div class="task-stat-label">本次会话已领取卡片</div>
                </div>
            </div>

            <!-- 任务列表（演示） -->
            <section class="task-section">
                <div class="section-head">
                    <h2 class="section-title"><i class="fas fa-list-check"></i> 任务列表</h2>
                    <span class="section-note">完成任务 +100 积分（演示模式：young 积分固定 5000，不会变化）</span>
                </div>
                <div class="task-list">
                    <div v-for="t in mockTasks" :key="t.id" class="task-item">
                        <span class="task-icon" :style="{ background: t.color + '22', color: t.color }">
                            <i :class="t.icon"></i>
                        </span>
                        <div class="task-info">
                            <span class="task-name">{{ t.name }}</span>
                            <span class="task-desc">{{ t.desc }}</span>
                        </div>
                        <span class="task-points">+100 积分</span>
                        <button class="task-done-btn" @click="handleCompleteTask">
                            <i class="fas fa-check"></i> 完成
                        </button>
                    </div>
                </div>
            </section>

            <!-- 奖励进度条 -->
            <section class="task-section">
                <div class="section-head">
                    <h2 class="section-title"><i class="fas fa-trophy"></i> 奖励进度条</h2>
                    <button class="view-claimed-btn" title="滚动查看左侧已领取的部分" @click="scrollToClaimed">
                        <i class="fas fa-arrow-left"></i> 查看已领取
                    </button>
                </div>

                <!-- 状态图例 -->
                <div class="legend">
                    <span class="legend-chip is-claimable"><i class="fas fa-gift"></i> 待领取</span>
                    <span class="legend-chip is-claimed"><i class="fas fa-check"></i> 已领取</span>
                    <span class="legend-chip is-locked"><i class="fas fa-lock"></i> 未达到条件</span>
                </div>

                <!-- 可横向滚动的进度条：已领取的部分位于左侧，大部分被隐藏，可滚动查看 -->
                <div class="reward-track-scroll" ref="trackScroll">
                    <div class="reward-track">
                        <template v-for="(m, i) in milestones" :key="m.pts">
                            <div v-if="i > 0" class="connector" :class="connectorClass(m)"></div>
                            <div class="milestone-cell" :class="milestoneClass(m)" @click="openClaim(m)">
                                <div class="milestone-card" :class="{ 'is-rare': m.isRare }">
                                    <i :class="milestoneIcon(m)"></i>
                                </div>
                                <span class="milestone-pts">{{ m.pts }}</span>
                                <span class="milestone-label">{{ milestoneLabel(m) }}</span>
                            </div>
                        </template>
                    </div>
                </div>
                <p class="scroll-hint">
                    <i class="fas fa-arrows-left-right"></i> 可左右滚动查看全部里程碑；已领取的部分位于左侧。
                </p>
            </section>
        </template>

        <!-- 领取卡片弹窗 -->
        <div v-if="claimVisible" class="claim-overlay" @click.self="closeClaim">
            <div class="claim-panel">
                <button class="claim-close" @click="closeClaim"><i class="fas fa-times"></i></button>

                <!-- 稀有卡片散落星光 -->
                <div v-if="flipped && currentReward?.milestone.isRare" class="sparkles">
                    <span v-for="(s, n) in sparklePositions" :key="n" class="sparkle"
                        :style="{ left: s.left, top: s.top, animationDelay: (n * 0.18) + 's' }"></span>
                </div>

                <!-- 卡片翻转动画 -->
                <div class="claim-scene" :class="{ 'is-flipped': flipped }">
                    <div class="claim-inner">
                        <!-- 背面（揭晓前） -->
                        <div class="claim-face claim-back">
                            <span class="back-question">?</span>
                            <span class="back-text">揭晓卡片中…</span>
                        </div>
                        <!-- 正面（卡片：纯色，无图片） -->
                        <div class="claim-face claim-front"
                            :class="{ 'is-rare': currentReward?.milestone.isRare }"
                            :style="{ background: cardGradient(currentReward?.card) }">
                            <span v-if="currentReward?.card" class="card-type"
                                :class="{ 'is-rare': currentReward.milestone.isRare }">
                                {{ currentReward.milestone.isRare ? '✦ 稀有卡片' : '普通卡片' }}
                            </span>
                            <span v-if="currentReward?.card" class="card-name">{{ currentReward.card.name }}</span>
                            <span class="card-points">达成 {{ currentReward?.milestone.pts }} 积分奖励</span>
                        </div>
                    </div>
                </div>

                <div class="claim-footer">
                    <button v-if="!flipped" class="claim-btn is-disabled" disabled>
                        <i class="fas fa-spinner fa-spin"></i> 揭晓中…
                    </button>
                    <button v-else class="claim-btn is-take" @click="confirmClaim">
                        <i class="fas fa-hand-holding-heart"></i> 收下卡片
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()

// ========== 演示数据 ==========
// 超级管理员 young：固定 5000 积分，不会消耗也不会变多
const POINTS = 5000
const STEP = 200
const MAX_DISPLAY = 6000 // 进度条展示至 6000，超出 5000 的里程碑用于演示「未达到条件」

// 普通卡片（演示数据；正式版由管理员面板指定）
const FIXED_POOL = [
    { name: '翠叶徽章', colors: ['#8fcfb8', '#4e9e83'] },
    { name: '青玉书签', colors: ['#7bc8c4', '#3a8f8b'] },
    { name: '鎏金护符', colors: ['#e5c96b', '#b3923a'] },
    { name: '墨蓝锦囊', colors: ['#7ba3d9', '#45699f'] },
    { name: '珊瑚印章', colors: ['#e58f8f', '#b35656'] },
    { name: '紫藤徽记', colors: ['#c3a5e0', '#7d5ca8'] },
    { name: '琥珀宝珠', colors: ['#e0b072', '#a97a38'] },
    { name: '苔绿石片', colors: ['#9cc48a', '#5f8f4e'] },
    { name: '霜白羽笔', colors: ['#c9d8d4', '#7e9d95'] },
    { name: '赤陶火印', colors: ['#d99b6c', '#a06a3f'] }
]

// 稀有卡片池：领取时随机抽取（可获得已有卡片）
const RARE_POOL = [
    { name: '金色传说·时光罗盘', colors: ['#f2d66b', '#c9a227'] },
    { name: '紫晶史诗·智慧之冠', colors: ['#b79aef', '#7a55c9'] },
    { name: '绯红神话·炼金之焰', colors: ['#f08a7a', '#c0483f'] },
    { name: '翠玉传奇·守护之灵', colors: ['#7fe0b0', '#2f9e76'] },
    { name: '冰蓝神话·极光之翼', colors: ['#8fd4f0', '#3f8fb8'] },
    { name: '曜黑史诗·永夜之眼', colors: ['#8f9bb3', '#3f4a63'] }
]

// 演示任务列表
const mockTasks = [
    { id: 1, name: '阅读一篇文章', desc: '完整阅读任意一篇学习资料', icon: 'fas fa-book-open', color: '#5BA8A4' },
    { id: 2, name: '完成一次英语训练', desc: '完成任意一组英语练习题', icon: 'fas fa-dumbbell', color: '#b6862a' },
    { id: 3, name: '收集错题', desc: '向错题本添加 3 道错题', icon: 'fas fa-book-medical', color: '#b65661' },
    { id: 4, name: '每日签到', desc: '登录并完成当日签到', icon: 'fas fa-calendar-check', color: '#7d5ca8' }
]

// ========== 里程碑 ==========
// 生成 200 ~ 6000 的里程碑；每 1000 积分为稀有卡片节点，其余为普通卡片节点
function buildMilestones() {
    const list = []
    for (let pts = STEP; pts <= MAX_DISPLAY; pts += STEP) {
        const isRare = pts % 1000 === 0
        list.push({
            pts,
            isRare,
            reached: pts <= POINTS,
            // 普通卡片预置（管理员面板未实现，先用演示池循环分配）
            card: isRare ? null : FIXED_POOL[((pts / STEP) - 1) % FIXED_POOL.length]
        })
    }
    return list
}

const milestones = buildMilestones()
const reachedCount = computed(() => milestones.filter(m => m.reached).length)

// 已领取的里程碑积分（仅内存保存，刷新后恢复未领取）
const claimedIds = ref(new Set())

// ========== 里程碑状态 ==========
function milestoneClass(m) {
    if (!m.reached) return 'is-locked'
    if (claimedIds.value.has(m.pts)) return 'is-claimed'
    return 'is-claimable'
}

function milestoneIcon(m) {
    if (!m.reached) return 'fas fa-lock'
    if (claimedIds.value.has(m.pts)) return 'fas fa-check'
    return m.isRare ? 'fas fa-star' : 'fas fa-gift'
}

function milestoneLabel(m) {
    if (!m.reached) return '未达到'
    if (claimedIds.value.has(m.pts)) return '已领取'
    return m.isRare ? '稀有·待领取' : '待领取'
}

function connectorClass(m) {
    return m.reached ? 'is-reached' : 'is-locked'
}

// ========== 进度条滚动 ==========
// 默认/领取后停靠右侧：已领取的部分被隐藏到左侧，可通过「查看已领取」或手动滚动查看
const trackScroll = ref(null)

function scrollToClaimed() {
    trackScroll.value?.scrollTo({ left: 0, behavior: 'smooth' })
}

function scrollToLatest() {
    nextTick(() => {
        trackScroll.value?.scrollTo({ left: trackScroll.value.scrollWidth, behavior: 'smooth' })
    })
}

// ========== 领取卡片 ==========
const claimVisible = ref(false)
const flipped = ref(false)
const currentReward = ref(null)
let flipTimer = null

function openClaim(m) {
    if (!m.reached || claimedIds.value.has(m.pts)) return
    // 普通卡片为预置卡；稀有卡片随机抽取（可获得已有卡片）
    const card = m.isRare
        ? RARE_POOL[Math.floor(Math.random() * RARE_POOL.length)]
        : m.card
    currentReward.value = { milestone: m, card }
    flipped.value = false
    claimVisible.value = true
    // 先展示背面，再翻转揭晓
    flipTimer = setTimeout(() => {
        flipped.value = true
    }, 900)
}

function confirmClaim() {
    if (!currentReward.value || !flipped.value) return
    const { milestone, card } = currentReward.value
    claimedIds.value.add(milestone.pts)
    claimVisible.value = false
    showToast(
        milestone.isRare
            ? `✨ 稀有卡片「${card.name}」已领取（演示：刷新后恢复未领取）`
            : `已领取卡片「${card.name}」（演示：刷新后恢复未领取）`,
        'success'
    )
    scrollToLatest()
}

function closeClaim() {
    if (!flipped.value) return // 揭晓动画进行中不允许关闭
    claimVisible.value = false
}

function cardGradient(card) {
    if (!card) return 'linear-gradient(160deg, #5ba8a4, #3a7d79)'
    return `linear-gradient(160deg, ${card.colors[0]}, ${card.colors[1]})`
}

// 稀有卡片周围散落的星光位置
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

// ========== 任务（演示） ==========
function handleCompleteTask() {
    showToast('演示模式：完成任务 +100 积分，但 young 积分固定为 5000，不会变化', 'info', 3500)
}

// ========== Toast（与错题本保持一致） ==========
let toastTimer = null
function showToast(message, type = 'info', duration = 3000) {
    const existing = document.querySelector('.custom-toast')
    if (existing) {
        existing.remove()
        if (toastTimer) {
            clearTimeout(toastTimer)
            toastTimer = null
        }
    }

    const toast = document.createElement('div')
    toast.className = `custom-toast toast-${type}`
    toast.textContent = message
    document.body.appendChild(toast)

    requestAnimationFrame(() => toast.classList.add('toast-visible'))

    toastTimer = setTimeout(() => {
        toast.classList.remove('toast-visible')
        setTimeout(() => {
            if (toast.parentNode) toast.parentNode.removeChild(toast)
            toastTimer = null
        }, 300)
    }, duration)
}

// ========== 生命周期 ==========
const isSuperAdmin = computed(() => authStore.isAdministrator)

onMounted(() => {
    // 初始停靠右侧：左侧已领取区域不可见，需滚动查看
    scrollToLatest()
})

onBeforeUnmount(() => {
    if (flipTimer) clearTimeout(flipTimer)
    if (toastTimer) clearTimeout(toastTimer)
})
</script>

<style scoped>
.task-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 30px 32px 100px;
    width: 100%;
}

.empty-tip {
    text-align: center;
    padding: 60px 20px;
    color: var(--gray);
    background: rgba(255, 255, 255, 0.3);
    border-radius: 24px;
}

/* ========== 头部 ========== */
.task-header {
    margin-bottom: 24px;
}

.task-title {
    font-size: 2.2rem;
    font-weight: 700;
    color: #2d4a3a;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 12px;
}

.task-title::before {
    content: '\f0ae';
    font-family: 'Font Awesome 6 Free';
    font-weight: 900;
    font-size: 1.8rem;
    color: #9782c8;
}

.task-subtitle {
    color: var(--gray);
    font-size: 0.95rem;
}

/* ========== 统计栏 ========== */
.task-stats {
    display: flex;
    gap: 20px;
    margin-bottom: 20px;
}

.task-stat-card {
    flex: 1;
    background: rgba(255, 255, 255, 0.45);
    backdrop-filter: blur(8px);
    border-radius: 18px;
    padding: 18px 24px;
    text-align: center;
    border: 1px solid rgba(255, 255, 255, 0.5);
    box-shadow: 0 4px 16px var(--shadow);
    transition: transform 0.25s ease;
}

.task-stat-card:hover {
    transform: translateY(-2px);
}

.task-stat-number {
    font-size: 2.1rem;
    font-weight: 700;
    color: #2d4a3a;
    line-height: 1.2;
}

.task-stat-card.is-points .task-stat-number {
    color: #b6862a;
}

.task-stat-card.is-claimed .task-stat-number {
    color: #2f7b57;
}

.task-stat-label {
    font-size: 0.9rem;
    color: var(--gray);
    margin-top: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
}

.stat-badge {
    font-size: 0.7rem;
    font-weight: 600;
    color: #8a6d1a;
    background: rgba(217, 186, 75, 0.18);
    border: 1px solid rgba(217, 186, 75, 0.3);
    padding: 0 8px;
    border-radius: 999px;
}

/* ========== 区块 ========== */
.task-section {
    margin-bottom: 24px;
    padding: 20px 22px;
    background: rgba(255, 255, 255, 0.3);
    backdrop-filter: blur(8px);
    border-radius: 20px;
    border: 1px solid rgba(255, 255, 255, 0.4);
}

.section-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 14px;
}

.section-title {
    font-size: 1.15rem;
    font-weight: 700;
    color: #2d4a3a;
    display: flex;
    align-items: center;
    gap: 8px;
}

.section-title i {
    color: #9782c8;
}

.section-note {
    font-size: 0.8rem;
    color: var(--gray);
}

/* ========== 任务列表 ========== */
.task-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.task-item {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 12px 16px;
    background: rgba(255, 255, 255, 0.5);
    border-radius: 14px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.task-item:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 18px var(--shadow);
}

.task-icon {
    width: 42px;
    height: 42px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.1rem;
    flex-shrink: 0;
}

.task-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1;
    min-width: 0;
}

.task-name {
    font-size: 0.98rem;
    font-weight: 600;
    color: #2d4a3a;
}

.task-desc {
    font-size: 0.82rem;
    color: var(--gray);
}

.task-points {
    font-size: 0.85rem;
    font-weight: 700;
    color: #b6862a;
    background: rgba(217, 186, 75, 0.15);
    border: 1px solid rgba(217, 186, 75, 0.25);
    padding: 3px 12px;
    border-radius: 999px;
    white-space: nowrap;
}

.task-done-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 18px;
    border-radius: 999px;
    border: 1px solid rgba(80, 176, 122, 0.25);
    background: rgba(205, 237, 221, 0.6);
    color: #2f7b57;
    font-size: 0.85rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
}

.task-done-btn:hover {
    background: rgba(205, 237, 221, 0.9);
    transform: translateY(-1px);
}

/* ========== 奖励进度条 ========== */
.view-claimed-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 16px;
    border-radius: 999px;
    border: 1px solid rgba(151, 130, 200, 0.25);
    background: rgba(151, 130, 200, 0.12);
    color: #5d4a8a;
    font-size: 0.85rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
}

.view-claimed-btn:hover {
    background: rgba(151, 130, 200, 0.24);
    transform: translateY(-1px);
}

.legend {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 12px;
}

.legend-chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.78rem;
    font-weight: 600;
    padding: 3px 12px;
    border-radius: 999px;
}

.legend-chip.is-claimable {
    background: rgba(217, 186, 75, 0.18);
    color: #8a6d1a;
    border: 1px solid rgba(217, 186, 75, 0.3);
}

.legend-chip.is-claimed {
    background: rgba(205, 237, 221, 0.6);
    color: #2f7b57;
    border: 1px solid rgba(80, 176, 122, 0.25);
}

.legend-chip.is-locked {
    background: rgba(120, 170, 155, 0.1);
    color: #7c9086;
    border: 1px solid rgba(120, 170, 155, 0.2);
}

/* 滚动容器：已领取的里程碑位于左侧，超出可视区域（被隐藏），可滚动查看 */
.reward-track-scroll {
    overflow-x: auto;
    padding: 14px 10px;
    background: rgba(255, 255, 255, 0.35);
    border-radius: 16px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    scrollbar-width: thin;
    scrollbar-color: rgba(91, 168, 164, 0.5) rgba(255, 255, 255, 0.3);
}

.reward-track {
    display: flex;
    align-items: center;
    width: max-content;
    padding: 6px 4px;
}

.connector {
    width: 44px;
    height: 5px;
    border-radius: 3px;
    flex-shrink: 0;
}

.connector.is-reached {
    background: rgba(91, 168, 164, 0.45);
}

.connector.is-locked {
    background: rgba(120, 170, 155, 0.15);
}

.milestone-cell {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    width: 100px;
    flex-shrink: 0;
    cursor: default;
}

.milestone-cell.is-claimable {
    cursor: pointer;
}

.milestone-card {
    width: 64px;
    height: 64px;
    border-radius: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.3rem;
    color: white;
    position: relative;
    transition: transform 0.2s ease;
}

/* 待领取：金色发光 + 呼吸动画 */
.milestone-cell.is-claimable .milestone-card {
    background: linear-gradient(160deg, #e5c96b, #b3923a);
    box-shadow: 0 0 14px rgba(217, 186, 75, 0.45);
    animation: claimPulse 1.8s ease-in-out infinite;
}

.milestone-cell.is-claimable:hover .milestone-card {
    transform: scale(1.08);
}

/* 待领取的稀有卡片：更强的金色光芒 */
.milestone-cell.is-claimable.is-rare .milestone-card {
    background: linear-gradient(160deg, #f2d66b, #c9a227);
    box-shadow: 0 0 22px rgba(242, 214, 107, 0.65);
    animation: rarePulse 1.4s ease-in-out infinite;
}

.milestone-cell.is-claimable.is-rare .milestone-card i {
    color: #fff6d8;
    text-shadow: 0 0 8px rgba(255, 255, 255, 0.8);
}

@keyframes claimPulse {
    0%,
    100% {
        box-shadow: 0 0 10px rgba(217, 186, 75, 0.35);
    }

    50% {
        box-shadow: 0 0 20px rgba(217, 186, 75, 0.6);
    }
}

@keyframes rarePulse {
    0%,
    100% {
        box-shadow: 0 0 14px rgba(242, 214, 107, 0.5);
        transform: scale(1);
    }

    50% {
        box-shadow: 0 0 30px rgba(242, 214, 107, 0.9);
        transform: scale(1.05);
    }
}

/* 已领取：绿色 */
.milestone-cell.is-claimed .milestone-card {
    background: linear-gradient(160deg, #b8e0d4, #7fb8a8);
    color: #2f6b50;
}

/* 未达到条件：灰色锁定 */
.milestone-cell.is-locked .milestone-card {
    background: rgba(120, 170, 155, 0.12);
    color: rgba(90, 122, 106, 0.55);
    border: 1px dashed rgba(120, 170, 155, 0.35);
}

.milestone-pts {
    font-size: 0.82rem;
    font-weight: 700;
    color: #345143;
}

.milestone-cell.is-locked .milestone-pts {
    color: #93a89d;
}

.milestone-label {
    font-size: 0.7rem;
    font-weight: 600;
}

.milestone-cell.is-claimable .milestone-label {
    color: #8a6d1a;
}

.milestone-cell.is-claimed .milestone-label {
    color: #2f7b57;
}

.milestone-cell.is-locked .milestone-label {
    color: #93a89d;
}

.scroll-hint {
    margin-top: 10px;
    font-size: 0.78rem;
    color: var(--gray);
    display: flex;
    align-items: center;
    gap: 6px;
}

/* ========== 领取弹窗 ========== */
.claim-overlay {
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
    animation: fadeInOverlay 0.25s ease;
}

@keyframes fadeInOverlay {
    from {
        opacity: 0;
    }

    to {
        opacity: 1;
    }
}

.claim-panel {
    position: relative;
    width: 100%;
    max-width: 400px;
    background: #fbfaf6;
    border-radius: 24px;
    box-shadow: 0 24px 80px rgba(0, 0, 0, 0.25);
    padding: 36px 24px 24px;
    animation: fadeInPanel 0.3s ease;
}

@keyframes fadeInPanel {
    from {
        opacity: 0;
        transform: translateY(20px) scale(0.97);
    }

    to {
        opacity: 1;
        transform: translateY(0) scale(1);
    }
}

.claim-close {
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

.claim-close:hover {
    background: rgba(120, 170, 155, 0.2);
}

/* 卡片 3D 翻转 */
.claim-scene {
    position: relative;
    width: 230px;
    height: 300px;
    margin: 4px auto 0;
    perspective: 1000px;
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
    backface-visibility: hidden;
    -webkit-backface-visibility: hidden;
    border-radius: 20px;
    overflow: hidden;
}

/* 背面：揭晓前 */
.claim-back {
    background: linear-gradient(160deg, #5ba8a4, #3a7d79);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 14px;
    box-shadow: 0 14px 40px rgba(0, 0, 0, 0.2);
}

.claim-back::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(115deg, transparent 30%, rgba(255, 255, 255, 0.18) 45%, transparent 60%);
    transform: translateX(-120%);
    animation: sweep 1.8s ease-in-out infinite;
}

@keyframes sweep {
    to {
        transform: translateX(120%);
    }
}

.back-question {
    font-size: 4rem;
    font-weight: 700;
    color: rgba(255, 255, 255, 0.9);
    text-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.back-text {
    font-size: 0.9rem;
    color: rgba(255, 255, 255, 0.85);
    letter-spacing: 2px;
}

/* 正面：卡片（纯色，无图片） */
.claim-front {
    transform: rotateY(180deg);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 14px;
    color: white;
    box-shadow: 0 14px 40px rgba(0, 0, 0, 0.25);
    padding: 20px;
    text-align: center;
}

.claim-front::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(115deg, transparent 30%, rgba(255, 255, 255, 0.25) 45%, transparent 60%);
    transform: translateX(-120%);
    animation: sweep 1.6s ease-in-out 0.8s infinite;
}

/* 稀有卡片：金色描边 + 光晕 */
.claim-front.is-rare {
    border: 3px solid rgba(242, 214, 107, 0.9);
    box-shadow: 0 0 30px rgba(242, 214, 107, 0.5), 0 14px 40px rgba(0, 0, 0, 0.25);
}

.card-type {
    font-size: 0.8rem;
    font-weight: 700;
    letter-spacing: 1px;
    background: rgba(0, 0, 0, 0.22);
    padding: 4px 14px;
    border-radius: 999px;
}

.card-type.is-rare {
    background: rgba(0, 0, 0, 0.18);
    color: #ffe9a8;
    border: 1px solid rgba(242, 214, 107, 0.7);
}

.card-name {
    font-size: 1.15rem;
    font-weight: 700;
    line-height: 1.4;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}

.card-points {
    font-size: 0.8rem;
    color: rgba(255, 255, 255, 0.9);
}

/* 稀有卡片散落的星光 */
.sparkles {
    position: absolute;
    top: 50%;
    left: 50%;
    width: 0;
    height: 0;
    z-index: 1;
    pointer-events: none;
}

.sparkle {
    position: absolute;
    width: 10px;
    height: 10px;
    background: #ffe9a8;
    border-radius: 2px;
    transform: rotate(45deg);
    box-shadow: 0 0 10px rgba(255, 233, 168, 0.9);
    animation: sparkleFloat 1.4s ease-in-out infinite;
}

@keyframes sparkleFloat {
    0% {
        opacity: 0;
        transform: rotate(45deg) scale(0.4);
    }

    50% {
        opacity: 1;
        transform: rotate(45deg) scale(1.2);
    }

    100% {
        opacity: 0;
        transform: rotate(45deg) scale(0.5);
    }
}

.claim-footer {
    display: flex;
    justify-content: center;
    margin-top: 22px;
}

.claim-btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 10px 34px;
    border-radius: 30px;
    border: none;
    font-size: 0.98rem;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
}

.claim-btn.is-disabled {
    background: rgba(120, 170, 155, 0.12);
    color: #7c9086;
    cursor: not-allowed;
}

.claim-btn.is-take {
    background: linear-gradient(135deg, rgba(255, 248, 214, 0.98), rgba(243, 227, 162, 0.92));
    color: #705d13;
    box-shadow: 0 6px 16px rgba(217, 186, 75, 0.3);
}

.claim-btn.is-take:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 22px rgba(217, 186, 75, 0.4);
}

/* ========== Toast（与错题本保持一致） ========== */
:global(.custom-toast) {
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

:global(.custom-toast.toast-visible) {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
}

:global(.custom-toast.toast-success) {
    border: 4px solid #4caf50;
    color: #1e4a2a;
}

:global(.custom-toast.toast-error) {
    border: 4px solid #ef5350;
    color: #7a2a2a;
}

:global(.custom-toast.toast-info) {
    border: 4px solid #42a5f5;
    color: #1a3a5a;
}

/* ========== 响应式 ========== */
@media (min-width: 1024px) {
    .task-container {
        padding: 50px 60px 120px;
    }

    .task-title {
        font-size: 2.6rem;
    }
}

@media (max-width: 640px) {
    .task-container {
        padding: 16px 14px 90px;
    }

    .task-title {
        font-size: 1.6rem;
    }

    .task-stats {
        gap: 10px;
    }

    .task-stat-card {
        padding: 14px 8px;
    }

    .task-stat-number {
        font-size: 1.5rem;
    }

    .task-item {
        flex-wrap: wrap;
    }

    .task-points {
        margin-left: 56px;
    }
}
</style>
