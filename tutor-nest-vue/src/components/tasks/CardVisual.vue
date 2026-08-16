<!--
  CardVisual.vue - 卡片视觉组件（收藏室共用）
  ----------------------------------------------------------------------------
  纯色渐变卡片：已获得 → 渐变色 + 图标 + 名称；未获得 → 灰色剪影 + 锁。
  props:
    card    - 卡片目录项 { name, icon, colors }；为空表示未获得
    rarity  - 'rare' | 'common'，控制稀有描边与角标
    large   - 是否大尺寸（收藏室左侧稀有卡展示）
-->
<template>
    <div class="card-visual"
        :class="{ 'is-locked': !card, 'is-rare': card && rarity === 'rare', 'is-large': large }"
        :style="card ? { background: gradient(card) } : undefined">
        <i v-if="card" :class="card.icon" class="card-icon"></i>
        <i v-else class="card-icon card-lock fas fa-lock"></i>
        <span class="card-name">{{ card ? card.name : '未获得' }}</span>
    </div>
</template>

<script setup>
defineProps({
    card: { type: Object, default: null },
    rarity: { type: String, default: 'common' },
    large: { type: Boolean, default: false }
})

function gradient(card) {
    return `linear-gradient(160deg, ${card.colors[0]}, ${card.colors[1]})`
}
</script>

<style scoped>
.card-visual {
    position: relative;
    width: 100%;
    aspect-ratio: 3 / 4;
    border-radius: 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    color: white;
    box-shadow: 0 8px 22px rgba(0, 0, 0, 0.18);
    overflow: hidden;
    text-align: center;
    padding: 10px;
}

.card-visual::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(115deg, transparent 35%, rgba(255, 255, 255, 0.22) 50%, transparent 65%);
    pointer-events: none;
}

/* 稀有卡：金色描边 + 光晕 */
.card-visual.is-rare {
    border: 3px solid rgba(242, 214, 107, 0.85);
    box-shadow: 0 0 24px rgba(242, 214, 107, 0.4), 0 8px 22px rgba(0, 0, 0, 0.2);
}

.card-icon {
    font-size: 2rem;
    text-shadow: 0 3px 10px rgba(0, 0, 0, 0.3);
}

.card-name {
    font-size: 0.82rem;
    font-weight: 700;
    line-height: 1.35;
    text-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
}

/* 未获得：灰色剪影 */
.card-visual.is-locked {
    background: rgba(120, 170, 155, 0.1);
    border: 1.5px dashed rgba(120, 170, 155, 0.35);
    box-shadow: none;
    color: rgba(90, 122, 106, 0.55);
}

.card-visual.is-locked::after {
    display: none;
}

.card-visual.is-locked .card-icon,
.card-visual.is-locked .card-name {
    text-shadow: none;
}

/* 大尺寸（稀有卡主展示） */
.card-visual.is-large {
    border-radius: 22px;
    gap: 16px;
}

.card-visual.is-large .card-icon {
    font-size: 3.4rem;
}

.card-visual.is-large .card-name {
    font-size: 1.1rem;
}
</style>
