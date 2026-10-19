<!-- src/components/tasks/AchievementBoard.vue -->
<template>
    <div class="achievement-board">
        <section class="achievement-summary panel" aria-label="成就总览">
            <div class="achievement-summary__main">
                <span class="eyebrow">ACHIEVEMENTS / 成就档案</span>
                <LevelBadge :points="points" size="lg" />
            </div>
            <div class="achievement-summary__stats">
                <div class="summary-stat">
                    <strong>{{ summary.claimedTiers }}<small>/{{ summary.totalTiers }}</small></strong>
                    <span>已领取成就等级</span>
                </div>
                <div class="summary-stat">
                    <strong>{{ summary.earnedPoints }}</strong>
                    <span>成就积分累计</span>
                </div>
            </div>
        </section>

        <div v-if="loading" class="loading-line">
            <span class="spinner" aria-hidden="true"></span> 正在加载成就…
        </div>
        <div v-else-if="error" class="empty achievement-error">
            <span class="empty__icon"><i class="fas fa-triangle-exclamation" aria-hidden="true"></i></span>
            <p class="empty__title">成就加载失败</p>
            <p class="empty__text">{{ error }}</p>
            <button type="button" class="btn btn--soft btn--sm" @click="load">重试</button>
        </div>

        <div v-else class="achievement-grid">
            <article v-for="achievement in summary.achievements" :key="achievement.key" class="achievement-card panel">
                <header class="achievement-head">
                    <span class="achievement-icon" :class="`achievement-icon--${achievement.tone}`">
                        <i :class="achievement.icon" aria-hidden="true"></i>
                    </span>
                    <div class="achievement-head__text">
                        <h2>{{ achievement.title }}</h2>
                        <p>{{ achievement.description }}</p>
                    </div>
                    <span class="achievement-current mono">
                        当前 {{ achievement.currentValue }}
                    </span>
                </header>

                <div class="tier-ladder" aria-label="成就等级">
                    <span v-for="tier in achievement.tiers" :key="tier.level" class="tier-chip"
                        :class="`is-${tier.state}`" :title="`${roman(tier.level)} 级 · 达成 ${tier.requirement} · 奖励 ${tier.rewardPoints} 积分`">
                        <strong>{{ roman(tier.level) }}</strong>
                        <small>{{ tier.requirement }}</small>
                        <span v-if="tier.state === 'claimed'" class="tier-check">
                            <i class="fas fa-check" aria-hidden="true"></i>
                        </span>
                    </span>
                </div>

                <div class="achievement-progress">
                    <template v-if="achievement.nextTier">
                        <div class="achievement-progress__head">
                            <span>
                                下一级 {{ roman(achievement.nextTier.level) }} · 需要
                                {{ achievement.nextTier.requirement }}
                            </span>
                            <span class="mono">{{ achievement.currentValue }} / {{ achievement.nextTier.requirement }}</span>
                        </div>
                        <div class="progress">
                            <span class="progress__bar"
                                :style="{ width: nextPercent(achievement) + '%' }"></span>
                        </div>
                    </template>
                    <span v-else class="achievement-max">
                        <i class="fas fa-crown" aria-hidden="true"></i> 已达到最高等级
                    </span>
                </div>

                <footer class="achievement-foot">
                    <span v-if="achievement.claimableLevel" class="claim-hint">
                        可领取 {{ roman(achievement.claimableLevel) }} 级成就奖励
                    </span>
                    <span v-else class="claim-hint is-muted">
                        {{ achievement.nextTier ? '继续积累以提升等级' : '已全部领取' }}
                    </span>
                    <button v-if="achievement.claimableLevel" type="button" class="btn btn--soft btn--sm"
                        :disabled="acting === achievement.key" @click="claim(achievement)">
                        {{ acting === achievement.key ? '领取中…' : `领取 +${claimReward(achievement)}` }}
                    </button>
                </footer>
            </article>
        </div>
    </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useTasksStore } from '@/stores/tasksStore'
import { growthGateway } from '@/gateways/growthGateway'
import { showToast } from '@/utils/toast'
import LevelBadge from '@/components/ui/LevelBadge.vue'

const authStore = useAuthStore()
const tasksStore = useTasksStore()

const summary = ref({
    points: 0,
    earnedPoints: 0,
    claimedTiers: 0,
    totalTiers: 0,
    achievements: []
})
const loading = ref(true)
const error = ref('')
const acting = ref('')

const points = computed(() => summary.value.points || tasksStore.points)

const ROMAN = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X']

function roman(level) {
    return ROMAN[level - 1] || String(level)
}

function nextPercent(achievement) {
    if (!achievement.nextTier) return 100
    return Math.min(100, Math.round((achievement.currentValue / achievement.nextTier.requirement) * 100))
}

function claimReward(achievement) {
    const tier = achievement.tiers.find(item => item.level === achievement.claimableLevel)
    return tier ? tier.rewardPoints : 0
}

async function load() {
    loading.value = true
    error.value = ''
    try {
        summary.value = await growthGateway.getAchievements()
        if (typeof summary.value.points === 'number') tasksStore.syncPoints(summary.value.points)
    } catch (cause) {
        error.value = cause.message || '成就加载失败'
    } finally {
        loading.value = false
    }
}

async function claim(achievement) {
    if (acting.value) return
    acting.value = achievement.key
    try {
        const result = await growthGateway.claimAchievement(achievement.key)
        tasksStore.syncPoints(result.totalPoints)
        showToast(`成就升级！积分 +${result.awardedPoints}`, 'success')
        await load()
    } catch (cause) {
        showToast(cause.message || '领取成就失败', 'error')
    } finally {
        acting.value = ''
    }
}

onMounted(load)
</script>

<style scoped>
.achievement-board {
    display: flex;
    flex-direction: column;
    gap: 18px;
}

.achievement-summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    flex-wrap: wrap;
    padding: 24px 26px;
}

.achievement-summary__main {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.achievement-summary__stats {
    display: flex;
    align-items: center;
    gap: 22px;
    flex-wrap: wrap;
}

.summary-stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding-left: 20px;
    border-left: 1.5px solid var(--line-strong);
}

.summary-stat strong {
    font-family: var(--font-display);
    font-size: 1.7rem;
    font-weight: 800;
    line-height: 1;
    color: var(--ink);
}

.summary-stat strong small {
    font-size: 0.8rem;
    color: var(--text-3);
    font-weight: 600;
}

.summary-stat span {
    font-size: 0.72rem;
    color: var(--text-3);
}

.achievement-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
}

.achievement-card {
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.achievement-head {
    display: flex;
    align-items: flex-start;
    gap: 12px;
}

.achievement-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 46px;
    height: 46px;
    border-radius: 13px;
    border: 1.5px solid var(--ink);
    color: var(--ink);
    font-size: 1rem;
    flex-shrink: 0;
    box-shadow: 2px 2px 0 var(--ink);
}

.achievement-icon--butter { background: var(--butter); }
.achievement-icon--coral { background: var(--coral); }
.achievement-icon--lime { background: var(--lime); }
.achievement-icon--sky { background: var(--sky); }
.achievement-icon--pink { background: var(--pink); }

.achievement-head__text {
    flex: 1;
    min-width: 0;
}

.achievement-head__text h2 {
    font-size: 1.02rem;
    color: var(--ink);
}

.achievement-head__text p {
    margin-top: 3px;
    font-size: 0.74rem;
    color: var(--text-3);
}

.achievement-current {
    font-size: 0.66rem;
    color: var(--text-3);
    white-space: nowrap;
    padding: 3px 8px;
    border-radius: var(--r-full);
    border: 1px solid var(--line-strong);
}

.tier-ladder {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
}

.tier-chip {
    position: relative;
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0;
    width: 44px;
    height: 48px;
    border-radius: 9px;
    border: 1.5px solid var(--line-strong);
    background: var(--paper-1);
    color: var(--text-4);
}

.tier-chip strong {
    font-family: var(--font-display);
    font-size: 0.78rem;
    line-height: 1.1;
}

.tier-chip small {
    font-family: var(--font-mono);
    font-size: 0.58rem;
}

.tier-chip.is-claimable {
    border-color: var(--ink);
    background: var(--butter);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

.tier-chip.is-claimed {
    border-color: var(--ink);
    background: var(--lime);
    color: var(--ink);
}

.tier-check {
    position: absolute;
    top: -5px;
    right: -5px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    font-size: 0.5rem;
    color: var(--ink);
}

.achievement-progress {
    display: flex;
    flex-direction: column;
    gap: 5px;
}

.achievement-progress__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    font-size: 0.7rem;
    color: var(--text-3);
}

.achievement-progress .progress {
    height: 8px;
}

.achievement-max {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.74rem;
    font-weight: 700;
    color: var(--ink);
}

.achievement-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-top: auto;
    padding-top: 12px;
    border-top: 1.5px dashed var(--line-strong);
}

.claim-hint {
    font-size: 0.7rem;
    font-weight: 600;
    color: var(--ink);
}

.claim-hint.is-muted {
    color: var(--text-4);
    font-weight: 400;
}

.achievement-error {
    margin: 0;
}

@media (max-width: 1100px) {
    .achievement-grid {
        grid-template-columns: minmax(0, 1fr);
    }
}

@media (max-width: 768px) {
    .achievement-summary {
        padding: 18px;
    }

    .achievement-card {
        padding: 16px;
    }

    .achievement-head__text h2 {
        font-size: 0.95rem;
    }
}
</style>
