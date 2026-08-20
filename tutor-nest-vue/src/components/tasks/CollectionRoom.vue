<!--
  CollectionRoom.vue - 收藏室（任务中心内部）
  ----------------------------------------------------------------------------
  展示当前用户获得的卡片：
    - 总览：按卡组陈列（每个卡组 1 稀有 + 6 普通），显示已收集进度
    - 卡组详情：左侧一张大稀有卡，右侧 2 行 3 列网格陈列普通卡
  未获得的卡片以灰色剪影显示。
-->
<template>
    <div class="collection-room">
        <!-- ========== 总览：卡组列表 ========== -->
        <template v-if="!activeSet">
            <div class="section-head">
                <h2 class="section-title"><i class="fas fa-book-open"></i> 收藏室</h2>
                <span class="section-note">
                    共收集 {{ collection.length }} 张卡片，卡组收集进度如下
                </span>
            </div>

            <div class="set-grid">
                <button v-for="set in CARD_SETS" :key="set.key" class="set-card" @click="openSet(set)">
                    <span class="set-emblem" :style="{ background: gradient(set.colors) }">
                        <i :class="set.emblem"></i>
                    </span>
                    <span class="set-info">
                        <span class="set-name">{{ set.name }}</span>
                        <span class="set-theme">{{ set.theme }} · 1 稀有 + 6 普通</span>
                        <span class="set-progress">
                            <span class="progress-bar">
                                <span class="progress-fill" :style="{ width: progress(set) + '%' }"></span>
                            </span>
                            <span class="progress-count">{{ obtainedIn(set).length }}/7</span>
                        </span>
                    </span>
                    <span v-if="isComplete(set)" class="set-badge is-complete">
                        <i class="fas fa-check"></i> 集齐
                    </span>
                    <i v-else class="fas fa-chevron-right set-arrow"></i>
                </button>
            </div>

            <p v-if="collection.length === 0" class="empty-hint">
                <i class="fas fa-lightbulb"></i>
                还没有收集到卡片。管理员完成任务获得积分后，达到里程碑即可领取卡片。
            </p>
        </template>

        <!-- ========== 卡组详情 ========== -->
        <template v-else>
            <div class="set-detail-head">
                <button class="back-btn" @click="activeSet = null">
                    <i class="fas fa-arrow-left"></i> 返回收藏室
                </button>
                <h2 class="section-title">
                    <span class="set-emblem is-small" :style="{ background: gradient(activeSet.colors) }">
                        <i :class="activeSet.emblem"></i>
                    </span>
                    {{ activeSet.name }}
                    <span class="detail-progress">{{ obtainedIn(activeSet).length }}/7</span>
                </h2>
                <span v-if="isComplete(activeSet)" class="set-badge is-complete">
                    <i class="fas fa-check"></i> 已集齐
                </span>
            </div>

            <div class="set-detail-layout">
                <!-- 左侧：大稀有卡 -->
                <div class="rare-side">
                    <div class="rare-label">
                        <i class="fas fa-star"></i> 稀有卡片
                    </div>
                    <CardVisual :card="rareCard(activeSet)" rarity="rare" large
                        class="rare-visual" />
                    <span class="rare-hint">
                        {{ rareCard(activeSet) ? '已获得' : '每 1000 积分里程碑获得一张稀有卡，按卡组轮换' }}
                    </span>
                </div>

                <!-- 右侧：2 行 3 列普通卡网格 -->
                <div class="commons-side">
                    <div class="common-label">
                        <i class="fas fa-gift"></i> 普通卡片
                    </div>
                    <div class="commons-grid">
                        <div v-for="(common, i) in activeSet.commons" :key="i" class="common-cell">
                            <CardVisual :card="obtained(activeSet, `${activeSet.key}-c${i}`) ? common : null"
                                :rarity="'common'" />
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

const collection = computed(() => tasksStore.collection)
const activeSet = ref(null)

function gradient(colors) {
    return `linear-gradient(160deg, ${colors[0]}, ${colors[1]})`
}

function obtainedIn(set) {
    return collection.value.filter(row => row.set_key === set.key)
}

function obtained(set, cardKey) {
    return obtainedIn(set).some(row => row.card_key === cardKey)
}

function rareCard(set) {
    const row = obtainedIn(set).find(r => r.rarity === 'rare')
    return row ? set.rare : null
}

function progress(set) {
    return Math.round((obtainedIn(set).length / 7) * 100)
}

function isComplete(set) {
    return obtainedIn(set).length >= 7
}

function openSet(set) {
    activeSet.value = set
}
</script>

<style scoped>
.collection-room {
    padding: 4px 2px 20px;
}

.section-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 16px;
}

.section-title {
    font-size: 1.15rem;
    font-weight: 700;
    color: #2d4a3a;
    display: flex;
    align-items: center;
    gap: 10px;
}

.section-title i {
    color: #9782c8;
}

.section-note {
    font-size: 0.82rem;
    color: var(--gray);
}

/* ========== 卡组网格（总览） ========== */
.set-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 14px;
}

.set-card {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 16px 18px;
    background: rgba(255, 255, 255, 0.5);
    border-radius: 18px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    cursor: pointer;
    font-family: inherit;
    text-align: left;
    transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.set-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 22px var(--shadow);
}

.set-emblem {
    width: 52px;
    height: 52px;
    border-radius: 15px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 1.4rem;
    flex-shrink: 0;
    box-shadow: 0 6px 14px rgba(0, 0, 0, 0.15);
}

.set-emblem.is-small {
    width: 34px;
    height: 34px;
    font-size: 0.95rem;
    border-radius: 10px;
    box-shadow: none;
}

.set-info {
    display: flex;
    flex-direction: column;
    gap: 4px;
    flex: 1;
    min-width: 0;
}

.set-name {
    font-size: 1rem;
    font-weight: 700;
    color: #2d4a3a;
}

.set-theme {
    font-size: 0.78rem;
    color: var(--gray);
}

.set-progress {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 2px;
}

.progress-bar {
    flex: 1;
    height: 7px;
    border-radius: 4px;
    background: rgba(120, 170, 155, 0.14);
    overflow: hidden;
}

.progress-fill {
    display: block;
    height: 100%;
    border-radius: 4px;
    background: linear-gradient(90deg, #7bc8c4, #5ba8a4);
    transition: width 0.4s ease;
}

.progress-count {
    font-size: 0.78rem;
    font-weight: 700;
    color: #345143;
}

.set-arrow {
    color: rgba(90, 122, 106, 0.45);
    font-size: 0.9rem;
}

.set-badge {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 0.75rem;
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 999px;
    white-space: nowrap;
}

.set-badge.is-complete {
    background: rgba(205, 237, 221, 0.7);
    color: #2f7b57;
    border: 1px solid rgba(80, 176, 122, 0.3);
}

.empty-hint {
    margin-top: 16px;
    font-size: 0.85rem;
    color: var(--gray);
    background: rgba(255, 255, 255, 0.4);
    border-radius: 12px;
    padding: 12px 16px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.empty-hint i {
    color: #9782c8;
}

/* ========== 卡组详情 ========== */
.set-detail-head {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
    margin-bottom: 18px;
}

.back-btn {
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

.back-btn:hover {
    background: rgba(151, 130, 200, 0.24);
    transform: translateY(-1px);
}

.detail-progress {
    font-size: 0.85rem;
    font-weight: 700;
    color: #345143;
    background: rgba(120, 170, 155, 0.12);
    padding: 2px 12px;
    border-radius: 999px;
}

.set-detail-layout {
    display: grid;
    grid-template-columns: 280px 1fr;
    gap: 24px;
    align-items: start;
}

/* 左侧：稀有卡 */
.rare-side {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.rare-label,
.common-label {
    font-size: 0.82rem;
    font-weight: 700;
    color: #345143;
    display: flex;
    align-items: center;
    gap: 6px;
}

.rare-label i {
    color: #c9a227;
}

.common-label i {
    color: #9782c8;
}

.rare-visual {
    max-width: 260px;
}

.rare-hint {
    font-size: 0.78rem;
    color: var(--gray);
}

/* 右侧：2 行 3 列普通卡网格 */
.commons-side {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-width: 0;
}

.commons-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    grid-template-rows: repeat(2, auto);
    gap: 14px;
}

.common-cell {
    max-width: 180px;
}

/* ========== 响应式 ========== */
@media (max-width: 760px) {
    .set-detail-layout {
        grid-template-columns: 1fr;
    }

    .rare-side {
        align-items: center;
    }

    .rare-visual {
        width: 60%;
        max-width: 220px;
    }

    .commons-grid {
        grid-template-columns: repeat(2, 1fr);
        grid-template-rows: repeat(3, auto);
    }
}
</style>
