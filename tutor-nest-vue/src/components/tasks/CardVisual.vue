<!--
  CardVisual.vue - 卡片视觉组件（收藏室共用）
  ----------------------------------------------------------------------------
  图片卡片：已获得 → 展示卡图 + 名称；未获得 → 灰色剪影 + 锁。
  props:
    card    - 卡片目录项 { name, img }；为空表示未获得
    rarity  - 'rare' | 'common'，控制稀有描边与角标
    large   - 是否大尺寸（收藏室左侧稀有卡展示）
-->
<template>
    <div class="card-visual"
        :class="{ 'is-locked': !card, 'is-rare': card && rarity === 'rare', 'is-large': large }">
        <template v-if="card">
            <img :src="card.img" :alt="card.name" class="card-img" loading="lazy" />
            <span class="card-shade"></span>
            <span class="card-name">{{ card.name }}</span>
        </template>
        <template v-else>
            <i class="card-icon card-lock fas fa-lock"></i>
            <span class="card-name">未获得</span>
        </template>
    </div>
</template>

<script setup>
defineProps({
    card: { type: Object, default: null },
    rarity: { type: String, default: 'common' },
    large: { type: Boolean, default: false }
})
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
    background: rgba(120, 170, 155, 0.12);
}

/* 已获得：卡图铺满 */
.card-img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
}

/* 底部渐变遮罩，保证卡名可读 */
.card-shade {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 46%;
    background: linear-gradient(to top, rgba(0, 0, 0, 0.55), rgba(0, 0, 0, 0));
    pointer-events: none;
}

.card-name {
    position: relative;
    z-index: 2;
    margin-top: auto;
    margin-bottom: 9px;
    font-size: 0.82rem;
    font-weight: 700;
    line-height: 1.35;
    text-shadow: 0 2px 6px rgba(0, 0, 0, 0.45);
    padding: 0 8px;
}

/* 稀有卡：金色描边 + 光晕 */
.card-visual.is-rare {
    border: 3px solid rgba(242, 214, 107, 0.85);
    box-shadow: 0 0 24px rgba(242, 214, 107, 0.4), 0 8px 22px rgba(0, 0, 0, 0.2);
}

/* 未获得：灰色剪影 */
.card-visual.is-locked {
    border: 1.5px dashed rgba(120, 170, 155, 0.35);
    box-shadow: none;
    color: rgba(90, 122, 106, 0.55);
}

.card-icon {
    font-size: 2rem;
    text-shadow: 0 3px 10px rgba(0, 0, 0, 0.15);
}

.card-visual.is-locked .card-name {
    margin-top: 0;
    margin-bottom: 0;
    font-size: 0.78rem;
}

/* 大尺寸（稀有卡主展示） */
.card-visual.is-large {
    border-radius: 22px;
}

.card-visual.is-large .card-name {
    font-size: 1.05rem;
    margin-bottom: 14px;
}
</style>
