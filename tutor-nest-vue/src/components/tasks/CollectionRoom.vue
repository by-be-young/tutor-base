<!--
  CollectionRoom.vue - 收藏室（任务中心内部，Riso Campus 拍立得卡片墙）
  ----------------------------------------------------------------------------
  展示当前用户获得的卡片：
    - 总览：按卡组陈列（每个卡组 1 稀有 + 6 普通），用 /cards 卡图做拍立得照片墙，
      显示已收集进度、集齐徽章；平面印刷色块 + 墨线，不用发光
    - 卡组详情：左侧一张大稀有卡（奶油黄相框），右侧 2 行 3 列普通卡网格
  未获得的卡片以虚线相框 + 剪影锁显示；所有卡图均为站内 /cards/*.png。
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
                        共收集 {{ collection.length }} / {{ TOTAL_CARDS }} 张卡片 · 已集齐 {{ completeSets }} / {{ CARD_SETS.length }} 套卡组
                    </p>
                </div>
                <div class="room-progress">
                    <span class="progress progress--cyan">
                        <span class="progress__bar" :style="{ width: totalPercent + '%' }"></span>
                    </span>
                    <span class="room-progress-label">{{ totalPercent }}%</span>
                </div>
            </div>

            <div class="set-grid">
                <button v-for="set in CARD_SETS" :key="set.key" class="set-card"
                    :class="{ 'is-complete': isComplete(set), 'is-empty': obtainedIn(set).length === 0 }"
                    @click="openSet(set)">
                    <!-- 卡组封面：站内卡图拍立得照片墙 -->
                    <span class="set-photos" aria-hidden="true">
                        <img v-for="(img, i) in collage(set)" :key="i" :src="img" alt="" loading="lazy"
                            class="set-photo" :style="{ '--photo-tilt': photoTilt(i) }" @error="onImageError" />
                    </span>

                    <span class="set-body">
                        <span class="set-top">
                            <span class="set-emblem" :style="{ background: setColor(set.colors) }">
                                <i :class="set.emblem"></i>
                            </span>
                            <span class="set-headings">
                                <span class="set-name">{{ set.name }}</span>
                                <span class="set-theme">{{ set.theme }} · {{ set.style }}</span>
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
                            <span class="progress-count">{{ obtainedIn(set).length }}/7</span>
                        </span>

                        <span class="set-foot">
                            <span class="set-foot-text">
                                <i class="fas fa-star"></i> 1 稀有
                                <span class="dot">·</span>
                                <i class="fas fa-gift"></i> 6 普通
                            </span>
                            <i class="fas fa-arrow-right set-arrow"></i>
                        </span>
                    </span>
                </button>
            </div>

            <p v-if="collection.length === 0" class="room-hint">
                <i class="fas fa-lightbulb"></i>
                还没有收集到卡片。每 200 积分解锁一个里程碑，达到后即可领取卡片；整千积分的里程碑必得稀有卡。
            </p>
        </template>

        <!-- ========== 卡组详情 ========== -->
        <template v-else>
            <div class="detail-head">
                <button class="btn btn--ghost btn--sm back-btn" @click="activeSet = null">
                    <i class="fas fa-arrow-left"></i> 返回收藏室
                </button>

                <div class="detail-title">
                    <span class="set-emblem is-small" :style="{ background: setColor(activeSet.colors) }">
                        <i :class="activeSet.emblem"></i>
                    </span>
                    <h2 class="section-title">{{ activeSet.name }}</h2>
                    <span class="detail-progress">{{ obtainedIn(activeSet).length }}/7</span>
                    <span v-if="isComplete(activeSet)" class="set-badge">
                        <i class="fas fa-crown"></i> 已集齐
                    </span>
                </div>

                <span class="detail-theme">{{ activeSet.theme }} · {{ activeSet.style }}</span>
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
                    </div>
                    <span class="rare-hint">
                        {{ rareCard(activeSet) ? '恭喜！这张稀有卡已收入你的收藏室' : '每 1000 积分里程碑随机获得一张稀有卡' }}
                    </span>
                </div>

                <!-- 右侧：2 行 3 列普通卡网格 -->
                <div class="commons-side">
                    <div class="common-label">
                        <i class="fas fa-gift"></i> 普通卡片
                        <span class="common-count">{{ obtainedIn(activeSet).filter(r => r.rarity === 'common').length }}/6</span>
                    </div>
                    <div class="commons-grid">
                        <div v-for="(common, i) in activeSet.commons" :key="i" class="common-cell">
                            <CardVisual :card="obtained(activeSet, `${activeSet.key}-c${i}`) ? common : null"
                                rarity="common" />
                        </div>
                    </div>
                </div>
            </div>
        </template>
    </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { CARD_SETS } from '@/data/cardCatalog'
import { useTasksStore } from '@/stores/tasksStore'
import CardVisual from './CardVisual.vue'

const tasksStore = useTasksStore()

const TOTAL_CARDS = CARD_SETS.reduce((sum, set) => sum + 1 + set.commons.length, 0)

const collection = computed(() => tasksStore.collection)
const activeSet = ref(null)

const completeSets = computed(() => CARD_SETS.filter(set => isComplete(set)).length)
const totalPercent = computed(() => Math.round((collection.value.length / TOTAL_CARDS) * 100))

/** 卡组主色：使用目录里的第一个印刷色，平涂不渐变 */
function setColor(colors) {
    return colors[0]
}

/** 卡组封面拼贴：稀有卡 + 3 张普通卡（站内图片），交替微旋转 */
function collage(set) {
    return [set.rare.img, ...set.commons.slice(0, 3).map(common => common.img)]
}

function photoTilt(index) {
    return ['-3deg', '2.4deg', '-1.4deg', '3deg'][index % 4]
}

function obtainedIn(set) {
    return collection.value.filter(row => row.set_key === set.key)
}

function obtained(set, cardKey) {
    return obtainedIn(set).some(row => row.card_key === cardKey)
}

function rareObtained(set) {
    return obtainedIn(set).some(row => row.rarity === 'rare')
}

function rareCard(set) {
    const row = obtainedIn(set).find(r => r.rarity === 'rare')
    return row ? set.rare : null
}

function progress(set) {
    return Math.round((obtainedIn(set).length / 7) * 100)
}

function progressClass(set) {
    if (isComplete(set)) return 'progress--warm'
    return obtainedIn(set).length > 0 ? 'progress--cyan' : ''
}

function isComplete(set) {
    return obtainedIn(set).length >= 7
}

function openSet(set) {
    activeSet.value = set
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

.room-progress {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 200px;
    flex: 0 1 300px;
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
    align-items: flex-start;
    gap: 10px;
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

/* 右侧：2 行 3 列普通卡网格（拍立得微旋转） */
.commons-side {
    display: flex;
    flex-direction: column;
    gap: 14px;
    min-width: 0;
}

.commons-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    grid-template-rows: repeat(2, auto);
    gap: 22px;
}

.common-cell {
    min-width: 0;
}

.common-cell:nth-child(1) .card-visual { --card-tilt: -1.8deg; }
.common-cell:nth-child(2) .card-visual { --card-tilt: 1.4deg; }
.common-cell:nth-child(3) .card-visual { --card-tilt: -1deg; }
.common-cell:nth-child(4) .card-visual { --card-tilt: 1.8deg; }
.common-cell:nth-child(5) .card-visual { --card-tilt: -1.4deg; }
.common-cell:nth-child(6) .card-visual { --card-tilt: 1deg; }

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

    .commons-grid .common-cell:nth-child(n + 5) {
        padding-top: 6px;
    }

    .room-progress {
        flex: 1 1 100%;
    }
}
</style>
