<!--
  CollectionRoom.vue - 收藏室（任务中心内部，Riso Campus 拍立得卡片墙）
  ----------------------------------------------------------------------------
  展示当前用户获得的卡片：
    - 卡组、卡片、稀有度与总数全部来自 /api/v1/rewards/catalog，不再硬编码卡组/卡片数量
    - 总览：按服务端卡组陈列，统计使用 /api/v1/rewards 的 stats（不同卡片 / 累计获得 / 累计抽卡）
    - 卡组详情：左侧大稀有卡，右侧普通卡网格；未获得卡片以虚线相框 + 剪影锁显示
  所有卡图均为服务端返回的站内 /cards/*.png 相对路径，由目录 store 补全 BASE_URL。
-->
<template>
    <div class="collection-room">
        <!-- ========== 总览：卡组列表 ========== -->
        <template v-if="!activeSet">
            <div class="room-head">
                <div class="room-title-wrap">
                    <span class="eyebrow">CARD WALL</span>
                    <h2 class="section-title room-title">
                        <i class="fas fa-book-open"></i> 收藏室
                    </h2>
                    <p class="section-note">
                        共收集 {{ uniqueCount }} / {{ totalCatalogCards }} 张不同卡片 ·
                        累计获得 {{ totalObtained }} 张 · 累计抽卡 {{ totalDraws }} 次 ·
                        已集齐 {{ completeSets }} / {{ totalSets }} 套卡组
                    </p>
                </div>
                <div class="room-head-side">
                    <button type="button" class="btn btn--warm btn--sm" @click="goDraw">
                        <i class="fas fa-wand-magic-sparkles" aria-hidden="true"></i> 前往卡池
                    </button>
                    <div class="room-progress">
                        <span class="progress progress--cyan">
                            <span class="progress__bar" :style="{ width: totalPercent + '%' }"></span>
                        </span>
                        <span class="room-progress-label">{{ totalPercent }}%</span>
                    </div>
                </div>
            </div>

            <div v-if="catalogStore.loading && !catalogStore.loaded" class="loading-line">
                <span class="spinner" aria-hidden="true"></span> 正在同步卡池目录…
            </div>
            <div v-else-if="catalogStore.error && !catalogStore.loaded" class="empty room-error">
                <span class="empty__icon"><i class="fas fa-triangle-exclamation" aria-hidden="true"></i></span>
                <p class="empty__title">卡池目录加载失败</p>
                <p class="empty__text">{{ catalogStore.error }}</p>
                <button type="button" class="btn btn--soft btn--sm" @click="retryCatalog">重试</button>
            </div>
            <div v-else-if="sets.length === 0" class="empty room-error">
                <span class="empty__icon"><i class="fas fa-images" aria-hidden="true"></i></span>
                <p class="empty__title">卡池暂时没有可展示的卡组</p>
                <p class="empty__text">卡组会定期更新，稍后再来看看。</p>
                <button type="button" class="btn btn--soft btn--sm" @click="retryCatalog">刷新目录</button>
            </div>

            <template v-else>
                <div class="set-grid">
                    <button v-for="set in sets" :key="set.key" class="set-card"
                        :class="{ 'is-complete': isComplete(set), 'is-empty': obtainedIn(set).length === 0 }"
                        @click="openSet(set)">
                        <!-- 卡组封面：站内卡图拍立得照片墙 -->
                        <span class="set-photos" aria-hidden="true">
                            <img v-for="(card, i) in collage(set)" :key="`${set.key}-${i}`" :src="card.img" alt=""
                                loading="lazy" class="set-photo" :style="{ '--photo-tilt': photoTilt(i) }"
                                @error="onImageError" />
                        </span>

                        <span class="set-body">
                            <span class="set-top">
                                <span class="set-emblem" :style="{ background: set.colors[0] || 'var(--butter)' }">
                                    <i :class="set.emblem"></i>
                                </span>
                                <span class="set-headings">
                                    <span class="set-name">{{ set.name }}</span>
                                    <span class="set-theme">{{ set.theme || '卡组' }}<template v-if="set.style"> ·
                                        {{ set.style }}</template></span>
                                </span>
                                <span v-if="isComplete(set)" class="set-badge">
                                    <i class="fas fa-crown"></i> 集齐
                                </span>
                                <span v-else-if="rareObtained(set)" class="set-badge is-rare">
                                    <i class="fas fa-star"></i> 稀有入手
                                </span>
                            </span>

                            <span class="set-progress">
                                <span class="progress" :class="progressClass(set)">
                                    <span class="progress__bar" :style="{ width: progress(set) + '%' }"></span>
                                </span>
                                <span class="progress-count">{{ obtainedIn(set).length }}/{{ cardCount(set) }}</span>
                            </span>

                            <span class="set-foot">
                                <span class="set-foot-text">
                                    <i class="fas fa-star"></i> 1 稀有
                                    <span class="dot">·</span>
                                    <i class="fas fa-gift"></i> {{ set.commons.length }} 普通
                                </span>
                                <i class="fas fa-arrow-right set-arrow"></i>
                            </span>
                        </span>
                    </button>
                </div>

                <p v-if="collection.length === 0" class="room-hint">
                    <i class="fas fa-lightbulb"></i>
                    还没有收集到卡片。前往卡池抽卡即可开始收集；重复卡片会累计获得次数，但不重复计入收集进度。
                    <button type="button" class="btn btn--soft btn--sm room-hint__btn" @click="goDraw">
                        前往卡池
                    </button>
                </p>
            </template>
        </template>

        <!-- ========== 卡组详情 ========== -->
        <template v-else>
            <div class="detail-head">
                <button class="btn btn--ghost btn--sm back-btn" @click="closeSet">
                    <i class="fas fa-arrow-left"></i> 返回收藏室
                </button>

                <div class="detail-title">
                    <span class="set-emblem is-small" :style="{ background: activeSet.colors[0] || 'var(--butter)' }">
                        <i :class="activeSet.emblem"></i>
                    </span>
                    <h2 class="section-title">{{ activeSet.name }}</h2>
                    <span class="detail-progress">{{ obtainedIn(activeSet).length }}/{{ cardCount(activeSet) }}</span>
                    <span v-if="isComplete(activeSet)" class="set-badge">
                        <i class="fas fa-crown"></i> 已集齐
                    </span>
                </div>

                <span class="detail-theme">{{ activeSet.theme || '卡组' }}<template v-if="activeSet.style"> ·
                    {{ activeSet.style }}</template></span>
            </div>

            <div class="detail-progress-bar">
                <span class="progress progress--cyan">
                    <span class="progress__bar" :style="{ width: progress(activeSet) + '%' }"></span>
                </span>
            </div>

            <div class="set-detail-layout">
                <!-- 左侧：大稀有卡（奶油黄拍立得相框） -->
                <div class="rare-side">
                    <div class="rare-label">
                        <i class="fas fa-star"></i> 稀有卡片
                        <span v-if="rareObtained(activeSet)" class="chip chip--amber rare-chip">已获得</span>
                        <span v-else class="chip rare-chip">未获得</span>
                    </div>
                    <div class="rare-stage">
                        <span class="rare-aura" aria-hidden="true"></span>
                        <CardVisual :card="rareCard(activeSet)" rarity="rare" large class="rare-visual" />
                        <span v-if="activeSet.rare && cardCount(activeSet, activeSet.rare.cardKey) > 1"
                            class="dup-badge dup-badge--rare">×{{ cardCount(activeSet, activeSet.rare.cardKey) }}</span>
                    </div>
                    <span class="rare-hint">
                        {{ rareObtained(activeSet)
                            ? '恭喜！这张稀有卡已收入你的收藏室'
                            : `抽卡有 ${catalogStore.rareRatePercent} 概率获得稀有卡` }}
                    </span>
                </div>

                <!-- 右侧：普通卡网格 -->
                <div class="commons-side">
                    <div class="common-label">
                        <i class="fas fa-gift"></i> 普通卡片
                        <span class="common-count">
                            {{ obtainedIn(activeSet).filter(row => row.rarity === 'common').length }}/{{ activeSet.commons.length }}
                        </span>
                    </div>
                    <div class="commons-grid">
                        <div v-for="(common, i) in activeSet.commons" :key="common.cardKey" class="common-cell"
                            :style="{ '--card-index': i }">
                            <div class="cell-wrap">
                                <CardVisual :card="obtained(activeSet, common.cardKey) ? common : null"
                                    rarity="common" />
                                <span v-if="obtained(activeSet, common.cardKey) && cardCount(activeSet, common.cardKey) > 1"
                                    class="dup-badge">×{{ cardCount(activeSet, common.cardKey) }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </template>
    </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useCardCatalogStore } from '@/stores/cardCatalogStore'
import { useTasksStore } from '@/stores/tasksStore'
import CardVisual from './CardVisual.vue'

const emit = defineEmits(['go-draw'])

const catalogStore = useCardCatalogStore()
const tasksStore = useTasksStore()

const collection = computed(() => tasksStore.collection)
const sets = computed(() => catalogStore.sets)
const activeSetKey = ref('')
const activeSet = computed(() =>
    activeSetKey.value ? catalogStore.setOf(activeSetKey.value) : null)

/** 已收集的不同卡片 key 集合（去重，重复掉落不计入收集进度） */
const ownedKeys = computed(() => new Set(collection.value.map(row => row.cardKey)))
const uniqueCount = computed(() => {
    const serverUnique = Number(tasksStore.stats.uniqueCards)
    if (Number.isFinite(serverUnique) && serverUnique > 0) return serverUnique
    return ownedKeys.value.size
})
const totalCatalogCards = computed(() =>
    Number(catalogStore.totalCards) || Number(tasksStore.stats.totalCatalogCards) || 0)
const totalSets = computed(() =>
    Number(catalogStore.totalSets) || Number(tasksStore.stats.totalSets) || sets.value.length)
const totalDraws = computed(() => Number(tasksStore.stats.totalDraws) || 0)
const totalObtained = computed(() =>
    Number(tasksStore.stats.totalCards) || collection.value.length)
const totalPercent = computed(() =>
    totalCatalogCards.value > 0 ? Math.round((uniqueCount.value / totalCatalogCards.value) * 100) : 0)
const completeSets = computed(() =>
    sets.value.filter(set => isComplete(set)).length)

/** 卡组卡片总数（1 稀有 + N 普通），来自服务端目录 */
function cardCount(set, cardKey) {
    if (cardKey) {
        return collection.value.filter(row => row.cardKey === cardKey).length
    }
    return set ? catalogStore.cardsInSet(set).length : 0
}

/** 卡组封面拼贴：稀有卡 + 最多 3 张普通卡（站内图片），交替微旋转 */
function collage(set) {
    const cards = []
    if (set.rare) cards.push(set.rare)
    cards.push(...set.commons.slice(0, 3))
    return cards
}

function photoTilt(index) {
    return ['-3deg', '2.4deg', '-1.4deg', '3deg'][index % 4]
}

/** 某卡组已获得的不同卡片（每个 card_key 只算一次） */
function obtainedIn(set) {
    if (!set) return []
    const rows = []
    const seen = new Set()
    for (const row of collection.value) {
        if (row.setKey !== set.key) continue
        if (seen.has(row.cardKey)) continue
        seen.add(row.cardKey)
        rows.push(row)
    }
    return rows
}

function obtained(set, cardKey) {
    return ownedKeys.value.has(cardKey)
}

function rareObtained(set) {
    return !!set?.rare && ownedKeys.value.has(set.rare.cardKey)
}

function rareCard(set) {
    return rareObtained(set) ? set.rare : null
}

function progress(set) {
    const total = cardCount(set)
    return total > 0 ? Math.round((obtainedIn(set).length / total) * 100) : 0
}

function progressClass(set) {
    if (isComplete(set)) return 'progress--warm'
    return obtainedIn(set).length > 0 ? 'progress--cyan' : ''
}

function isComplete(set) {
    if (!set) return false
    return obtainedIn(set).length >= cardCount(set) && cardCount(set) > 0
}

function openSet(set) {
    activeSetKey.value = set.key
}

function closeSet() {
    activeSetKey.value = ''
}

function goDraw() {
    emit('go-draw')
}

async function retryCatalog() {
    await catalogStore.load(true)
}

function onImageError(event) {
    const img = event?.target
    if (img) img.style.opacity = '0'
}
</script>

<style scoped>
.collection-room {
    padding: 0 0 12px;
}

/* ========== 总览头部 ========== */
.room-head {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 20px;
    flex-wrap: wrap;
    margin-bottom: 24px;
}

.room-title-wrap {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.room-title {
    font-size: clamp(1.3rem, 2.2vw, 1.6rem);
}

.room-title i {
    color: var(--vermilion);
}

.room-head-side {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 10px;
}

.room-progress {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 200px;
    width: 300px;
    max-width: 100%;
}

.room-progress .progress {
    flex: 1;
    height: 12px;
}

.room-progress-label {
    font-family: var(--font-display);
    font-size: 1rem;
    font-weight: 800;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.room-error {
    margin: 0;
}

.room-hint__btn {
    margin-left: 8px;
    vertical-align: middle;
}

/* ========== 卡组网格（拍立得卡片墙） ========== */
.set-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 24px;
}

.set-card {
    position: relative;
    display: flex;
    flex-direction: column;
    padding: 0;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-1);
    color: var(--text-1);
    font-family: inherit;
    text-align: left;
    overflow: hidden;
    box-shadow: var(--shadow-card);
    transition: transform var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
}

.set-card:hover {
    transform: translate(-3px, -3px) rotate(-0.5deg);
    box-shadow: var(--shadow-float);
}

.set-card:active {
    transform: translate(1px, 1px);
    box-shadow: 2px 2px 0 var(--ink);
}

/* 卡组封面：纸色横条 + 微旋转拍立得 */
.set-photos {
    display: flex;
    gap: 10px;
    padding: 16px 16px 12px;
    background: var(--paper-2);
    border-bottom: 1.5px solid var(--ink);
    overflow: hidden;
}

.set-photo {
    flex: 1;
    width: 0;
    aspect-ratio: 3 / 4;
    object-fit: cover;
    border: 4px solid var(--paper-1);
    outline: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 rgba(28, 26, 20, 0.85);
    transform: rotate(var(--photo-tilt, 0deg));
    transition: transform var(--dur) var(--ease-out);
}

.set-card:hover .set-photo {
    transform: rotate(calc(var(--photo-tilt, 0deg) * 1.5)) translateY(-2px);
}

/* 卡片内容 */
.set-body {
    display: flex;
    flex-direction: column;
    gap: 14px;
    flex: 1;
    padding: 16px;
}

.set-top {
    display: flex;
    align-items: center;
    gap: 12px;
}

.set-emblem {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    border: 1.5px solid var(--ink);
    border-radius: 12px;
    color: var(--ink);
    font-size: 1.15rem;
    box-shadow: 3px 3px 0 var(--ink);
}

.set-emblem.is-small {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    font-size: 0.92rem;
    box-shadow: none;
}

.set-emblem i {
    text-shadow: none;
}

.set-headings {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
    flex: 1;
}

.set-name {
    font-family: var(--font-display);
    font-size: 1.1rem;
    font-weight: 800;
    line-height: 1.22;
    color: var(--ink);
}

.set-theme {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    color: var(--text-3);
}

/* 集齐 / 稀有入手：贴纸徽章 */
.set-badge {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 4px 10px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--butter);
    color: var(--ink);
    font-size: 0.7rem;
    font-weight: 800;
    white-space: nowrap;
    transform: rotate(-4deg);
    box-shadow: 2px 2px 0 var(--ink);
}

.set-badge.is-rare {
    background: var(--vermilion);
    color: var(--paper-1);
}

.set-progress {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: auto;
}

.set-progress .progress {
    flex: 1;
}

.progress-count {
    font-family: var(--font-mono);
    font-size: 0.8rem;
    font-weight: 700;
    color: var(--ink);
}

.set-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding-top: 10px;
    border-top: 1.5px dashed var(--line-strong);
    color: var(--text-3);
    font-size: 0.72rem;
}

.set-foot-text {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.set-foot-text i {
    font-size: 0.7rem;
    color: var(--vermilion);
}

.set-foot-text i.fa-gift {
    color: var(--cobalt);
}

.set-foot-text .dot {
    color: var(--text-4);
}

.set-arrow {
    color: var(--text-3);
    transition: transform var(--dur) var(--ease-out);
}

.set-card:hover .set-arrow {
    transform: translateX(3px);
    color: var(--ink);
}

.room-hint {
    margin-top: 20px;
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    padding: 14px 16px;
    border: 1.5px dashed var(--ink-soft);
    border-radius: var(--r-md);
    background: var(--paper-2);
    color: var(--text-2);
    font-size: 0.84rem;
    line-height: 1.6;
}

.room-hint i {
    margin-top: 2px;
    color: var(--vermilion);
}

/* ========== 卡组详情 ========== */
.detail-head {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
    margin-bottom: 14px;
}

.back-btn {
    flex-shrink: 0;
}

.detail-title {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    flex: 1;
    min-width: 0;
}

.detail-title .section-title {
    font-size: clamp(1.15rem, 2vw, 1.4rem);
}

.detail-progress {
    font-family: var(--font-mono);
    font-size: 0.78rem;
    font-weight: 700;
    color: var(--ink);
    padding: 3px 10px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.detail-theme {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    color: var(--text-3);
}

.detail-progress-bar {
    margin-bottom: 26px;
}

.detail-progress-bar .progress {
    height: 12px;
}

.set-detail-layout {
    display: grid;
    grid-template-columns: minmax(220px, 320px) minmax(0, 1fr);
    gap: 34px;
    align-items: start;
}

/* 左侧：稀有卡 */
.rare-side {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.rare-label,
.common-label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.82rem;
    font-weight: 800;
    color: var(--ink);
}

.rare-label i {
    color: var(--vermilion);
}

.common-label i {
    color: var(--cobalt);
}

.rare-chip {
    margin-left: auto;
    height: 24px;
    font-size: 0.68rem;
}

.common-count {
    margin-left: auto;
    font-family: var(--font-mono);
    font-size: 0.76rem;
    color: var(--text-3);
}

/* 纸色底 + 奶油黄拍立得相框 + 虚线内框，取代发光 */
.rare-stage {
    position: relative;
    padding: 18px;
    background: var(--paper-2);
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    box-shadow: var(--shadow-card);
    transform: rotate(-1.4deg);
}

.rare-aura {
    position: absolute;
    inset: 8px;
    border: 2px dashed rgba(28, 26, 20, 0.4);
    border-radius: var(--r-sm);
    pointer-events: none;
}

.rare-visual {
    position: relative;
    z-index: 1;
}

.rare-hint {
    font-size: 0.78rem;
    color: var(--text-3);
    line-height: 1.55;
}

/* 右侧：普通卡网格（拍立得微旋转） */
.commons-side {
    display: flex;
    flex-direction: column;
    gap: 14px;
    min-width: 0;
}

.commons-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 22px;
}

.common-cell {
    min-width: 0;
}

.cell-wrap {
    position: relative;
}

.dup-badge {
    position: absolute;
    top: 6px;
    right: 6px;
    padding: 2px 8px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--butter);
    color: var(--ink);
    font-family: var(--font-mono);
    font-size: 0.72rem;
    font-weight: 800;
    box-shadow: 2px 2px 0 var(--ink);
    transform: rotate(3deg);
    z-index: 2;
}

.dup-badge--rare {
    top: 20px;
    right: 20px;
    background: var(--vermilion);
    color: var(--paper-1);
}

.common-cell:nth-child(6n + 1) .card-visual { --card-tilt: -1.8deg; }
.common-cell:nth-child(6n + 2) .card-visual { --card-tilt: 1.4deg; }
.common-cell:nth-child(6n + 3) .card-visual { --card-tilt: -1deg; }
.common-cell:nth-child(6n + 4) .card-visual { --card-tilt: 1.8deg; }
.common-cell:nth-child(6n + 5) .card-visual { --card-tilt: -1.4deg; }
.common-cell:nth-child(6n) .card-visual { --card-tilt: 1deg; }

/* ========== 响应式 ========== */
@media (max-width: 900px) {
    .set-detail-layout {
        grid-template-columns: minmax(200px, 270px) minmax(0, 1fr);
        gap: 24px;
    }

    .commons-grid {
        gap: 16px;
    }
}

@media (max-width: 760px) {
    .room-head {
        align-items: flex-start;
    }

    .room-head-side {
        width: 100%;
        align-items: flex-start;
    }

    .set-grid {
        grid-template-columns: minmax(0, 1fr);
    }

    .set-detail-layout {
        grid-template-columns: minmax(0, 1fr);
        gap: 26px;
    }

    .rare-side {
        align-items: center;
        text-align: center;
    }

    .rare-stage {
        width: 66%;
        max-width: 250px;
        padding: 14px;
    }

    .rare-label {
        width: 100%;
        justify-content: center;
    }

    .rare-hint {
        text-align: center;
    }

    .commons-grid {
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 12px;
    }
}

@media (max-width: 420px) {
    .commons-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .room-progress {
        min-width: 0;
        width: 100%;
    }
}
</style>
