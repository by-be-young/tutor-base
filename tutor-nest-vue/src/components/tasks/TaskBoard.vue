<!-- src/components/tasks/TaskBoard.vue -->
<template>
    <div class="task-board">
        <section class="tasks-panel panel" aria-label="周期任务">
            <div class="panel-head">
                <h2 class="panel-title">
                    <i class="fas fa-clipboard-check" aria-hidden="true"></i> 周期任务
                </h2>
                <span class="panel-note">
                    每日 <strong>{{ dailyResetLabel }}</strong> 刷新 · 每周一 <strong>00:00</strong> 刷新
                </span>
            </div>

            <div v-if="loading" class="loading-line">
                <span class="spinner" aria-hidden="true"></span> 正在加载任务…
            </div>
            <div v-else-if="error" class="empty tasks-error">
                <span class="empty__icon"><i class="fas fa-triangle-exclamation" aria-hidden="true"></i></span>
                <p class="empty__title">任务加载失败</p>
                <p class="empty__text">{{ error }}</p>
                <button type="button" class="btn btn--soft btn--sm" @click="load">重试</button>
            </div>

            <template v-else>
                <div class="task-group">
                    <h3 class="task-group__title">
                        <span class="type-dot type-dot--daily"></span> 每日任务
                        <span class="task-group__hint">每日 00:00 重置</span>
                    </h3>
                    <div class="task-grid">
                        <article v-for="task in board.daily" :key="task.key" class="task-card"
                            :class="{ 'is-claimed': task.status === 'claimed' }">
                            <span class="task-card__icon" :class="`task-card__icon--${task.tone}`">
                                <i :class="task.icon" aria-hidden="true"></i>
                            </span>
                            <div class="task-card__body">
                                <div class="task-card__head">
                                    <h4>{{ task.title }}</h4>
                                    <span class="task-reward">+{{ task.rewardPoints }}</span>
                                </div>
                                <p class="task-card__desc">{{ task.description }}</p>
                                <div class="progress task-card__bar">
                                    <span class="progress__bar" :style="{ width: percent(task) + '%' }"
                                        :class="{ 'is-done': task.status === 'claimed' }"></span>
                                </div>
                                <div class="task-card__foot">
                                    <span class="mono">{{ task.progress }} / {{ task.target }}</span>
                                    <button v-if="task.status === 'claimable'" type="button"
                                        class="btn btn--soft btn--sm" :disabled="acting === task.key"
                                        @click="claim(task)">
                                        {{ acting === task.key ? '领取中…' : '领取奖励' }}
                                    </button>
                                    <span v-else-if="task.status === 'claimed'" class="task-done">
                                        <i class="fas fa-check" aria-hidden="true"></i> 已领取
                                    </span>
                                    <span v-else class="task-progress-label">进行中</span>
                                </div>
                            </div>
                        </article>
                    </div>
                </div>

                <div class="task-group">
                    <h3 class="task-group__title">
                        <span class="type-dot type-dot--weekly"></span> 每周任务
                        <span class="task-group__hint">每周一 00:00 重置</span>
                    </h3>
                    <div class="task-grid">
                        <article v-for="task in board.weekly" :key="task.key" class="task-card"
                            :class="{ 'is-claimed': task.status === 'claimed' }">
                            <span class="task-card__icon" :class="`task-card__icon--${task.tone}`">
                                <i :class="task.icon" aria-hidden="true"></i>
                            </span>
                            <div class="task-card__body">
                                <div class="task-card__head">
                                    <h4>{{ task.title }}</h4>
                                    <span class="task-reward">+{{ task.rewardPoints }}</span>
                                </div>
                                <p class="task-card__desc">{{ task.description }}</p>
                                <div class="progress task-card__bar">
                                    <span class="progress__bar" :style="{ width: percent(task) + '%' }"
                                        :class="{ 'is-done': task.status === 'claimed' }"></span>
                                </div>
                                <div class="task-card__foot">
                                    <span class="mono">{{ task.progress }} / {{ task.target }}</span>
                                    <button v-if="task.status === 'claimable'" type="button"
                                        class="btn btn--soft btn--sm" :disabled="acting === task.key"
                                        @click="claim(task)">
                                        {{ acting === task.key ? '领取中…' : '领取奖励' }}
                                    </button>
                                    <span v-else-if="task.status === 'claimed'" class="task-done">
                                        <i class="fas fa-check" aria-hidden="true"></i> 已领取
                                    </span>
                                    <span v-else class="task-progress-label">进行中</span>
                                </div>
                            </div>
                        </article>
                    </div>
                </div>

                <div class="task-group">
                    <h3 class="task-group__title">
                        <span class="type-dot type-dot--limited"></span> 限时任务
                        <span class="task-group__hint">接取后按截止时间完成，期限已按上学作息放宽</span>
                    </h3>
                    <div class="task-grid">
                        <article v-for="task in board.limited" :key="task.key" class="task-card"
                            :class="{ 'is-claimed': task.status === 'claimed', 'is-expired': task.status === 'expired' }">
                            <span class="task-card__icon" :class="`task-card__icon--${task.tone}`">
                                <i :class="task.icon" aria-hidden="true"></i>
                            </span>
                            <div class="task-card__body">
                                <div class="task-card__head">
                                    <h4>{{ task.title }}</h4>
                                    <span class="task-reward">+{{ task.rewardPoints }}</span>
                                </div>
                                <p class="task-card__desc">{{ task.description }}</p>
                                <div v-if="task.status !== 'available'" class="progress task-card__bar">
                                    <span class="progress__bar" :style="{ width: percent(task) + '%' }"
                                        :class="{ 'is-done': task.status === 'claimed' }"></span>
                                </div>
                                <div class="task-card__foot">
                                    <span v-if="task.status === 'available'" class="task-progress-label">
                                        接取后开始计时
                                    </span>
                                    <span v-else class="mono">{{ task.progress }} / {{ task.target }}</span>
                                    <template v-if="task.status === 'available'">
                                        <button type="button" class="btn btn--warm btn--sm"
                                            :disabled="acting === task.key" @click="accept(task)">
                                            {{ acting === task.key ? '接取中…' : '接取任务' }}
                                        </button>
                                    </template>
                                    <template v-else-if="task.status === 'claimable'">
                                        <button type="button" class="btn btn--soft btn--sm"
                                            :disabled="acting === task.key" @click="claim(task)">
                                            {{ acting === task.key ? '领取中…' : '领取奖励' }}
                                        </button>
                                    </template>
                                    <template v-else-if="task.status === 'expired'">
                                        <button type="button" class="btn btn--ghost btn--sm"
                                            :disabled="acting === task.key" @click="accept(task)">
                                            重新接取
                                        </button>
                                    </template>
                                    <span v-else-if="task.status === 'claimed'" class="task-done">
                                        <i class="fas fa-check" aria-hidden="true"></i> 已领取
                                    </span>
                                    <span v-else class="task-deadline">
                                        截止 {{ formatDeadline(task.deadlineAt) }}
                                    </span>
                                </div>
                                <span v-if="task.status === 'expired'" class="task-expired-note">
                                    已超过截止时间，可重新接取
                                </span>
                            </div>
                        </article>
                    </div>
                </div>
            </template>
        </section>
    </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useTasksStore } from '@/stores/tasksStore'
import { growthGateway } from '@/gateways/growthGateway'
import { showToast } from '@/utils/toast'

const authStore = useAuthStore()
const tasksStore = useTasksStore()

const board = ref({ daily: [], weekly: [], limited: [], dailyResetAt: null, weeklyResetAt: null })
const loading = ref(true)
const error = ref('')
const acting = ref('')

const dailyResetLabel = computed(() => {
    if (!board.value.dailyResetAt) return '00:00'
    const date = new Date(board.value.dailyResetAt)
    return `${date.getMonth() + 1}月${date.getDate()}日 00:00`
})

function percent(task) {
    return Math.min(100, Math.round((task.progress / task.target) * 100))
}

function formatDeadline(value) {
    if (!value) return '—'
    const date = new Date(value)
    return new Intl.DateTimeFormat('zh-CN', {
        month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(date)
}

async function load() {
    loading.value = true
    error.value = ''
    try {
        board.value = await growthGateway.getTaskBoard()
        if (typeof board.value.points === 'number') tasksStore.syncPoints(board.value.points)
    } catch (cause) {
        error.value = cause.message || '任务加载失败'
    } finally {
        loading.value = false
    }
}

async function accept(task) {
    if (acting.value) return
    acting.value = task.key
    try {
        await growthGateway.acceptTask(task.key)
        showToast(`已接取「${task.title}」，按截止时间完成即可`, 'success')
        await load()
    } catch (cause) {
        showToast(cause.message || '接取任务失败', 'error')
    } finally {
        acting.value = ''
    }
}

async function claim(task) {
    if (acting.value) return
    acting.value = task.key
    try {
        const result = await growthGateway.claimTask(task.key)
        tasksStore.syncPoints(result.totalPoints)
        showToast(`任务完成，积分 +${result.awardedPoints}`, 'success')
        await load()
    } catch (cause) {
        showToast(cause.message || '领取奖励失败', 'error')
    } finally {
        acting.value = ''
    }
}

onMounted(load)
</script>

<style scoped>
.task-board {
    display: flex;
    flex-direction: column;
    gap: 18px;
}

.panel-note {
    font-size: 0.74rem;
    color: var(--text-3);
}

.panel-note strong {
    color: var(--ink);
}

.task-group + .task-group {
    margin-top: 18px;
}

.task-group {
    padding: 0 20px 20px;
}

.task-group__title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 0.86rem;
    font-weight: 800;
    color: var(--ink);
    padding: 6px 0 12px;
}

.task-group__hint {
    font-family: var(--font-mono);
    font-size: 0.64rem;
    font-weight: 400;
    color: var(--text-3);
}

.type-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    border: 1.5px solid var(--ink);
    flex-shrink: 0;
}

.type-dot--daily { background: var(--butter); }
.type-dot--weekly { background: var(--lime); }
.type-dot--limited { background: var(--coral); }

.task-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
}

.task-card {
    display: flex;
    gap: 12px;
    padding: 14px;
    border-radius: var(--r-md);
    border: 1.5px solid var(--line-strong);
    background: var(--paper-1);
    transition: border-color var(--dur-fast), box-shadow var(--dur-fast), transform var(--dur-fast);
}

.task-card:hover {
    border-color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
    transform: translate(-1px, -1px);
}

.task-card.is-claimed {
    background: rgba(201, 239, 74, 0.16);
    border-color: var(--ink);
}

.task-card.is-expired {
    opacity: 0.72;
}

.task-card__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 11px;
    border: 1.5px solid var(--ink);
    color: var(--ink);
    font-size: 0.9rem;
    flex-shrink: 0;
}

.task-card__icon--butter { background: var(--butter); }
.task-card__icon--coral { background: var(--coral); }
.task-card__icon--lime { background: var(--lime); }
.task-card__icon--sky { background: var(--sky); }

.task-card__body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 5px;
}

.task-card__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
}

.task-card__head h4 {
    font-size: 0.9rem;
    font-weight: 800;
    color: var(--ink);
}

.task-reward {
    font-family: var(--font-mono);
    font-size: 0.66rem;
    font-weight: 600;
    color: var(--ink);
    padding: 2px 7px;
    border-radius: var(--r-full);
    border: 1px solid var(--ink);
    background: var(--butter);
    white-space: nowrap;
}

.task-card__desc {
    font-size: 0.72rem;
    color: var(--text-3);
}

.task-card__bar {
    height: 7px;
    margin-top: 2px;
}

.task-card__bar .progress__bar.is-done {
    background: var(--ink);
}

.task-card__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-top: 3px;
    min-height: 28px;
}

.task-card__foot .mono {
    font-size: 0.68rem;
    color: var(--text-3);
}

.task-progress-label {
    font-size: 0.66rem;
    color: var(--text-4);
}

.task-done {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 0.68rem;
    font-weight: 700;
    color: var(--ink);
}

.task-deadline {
    font-size: 0.66rem;
    color: var(--text-3);
    font-family: var(--font-mono);
}

.task-expired-note {
    font-size: 0.66rem;
    color: #b32d14;
}

.tasks-error {
    margin: 0 20px 20px;
}

@media (max-width: 1100px) {
    .task-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}

@media (max-width: 768px) {
    .task-grid {
        grid-template-columns: minmax(0, 1fr);
    }

    .task-group {
        padding: 0 16px 16px;
    }
}
</style>
