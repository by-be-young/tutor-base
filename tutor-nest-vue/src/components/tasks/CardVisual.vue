<!--
  CardVisual.vue - 卡片视觉组件（收藏室共用，Riso Campus 拍立得风）
  ----------------------------------------------------------------------------
  已获得 → 纸白拍立得相框 + 卡图 + 名称；未获得 → 虚线相框 + 剪影锁。
  稀有卡 → 奶油黄（butter）相框 + 墨线，取代发光；微旋转由父级 --card-tilt 控制。
  props:
    card    - 卡片目录项 { name, img }；为空表示未获得
    rarity  - 'rare' | 'common'，控制稀有相框颜色与角标
    large   - 是否大尺寸（收藏室左侧稀有卡展示）
-->
<template>
    <div class="card-visual" :class="{
        'is-locked': !card,
        'is-rare': !!card && rarity === 'rare',
        'is-rare-slot': !card && rarity === 'rare',
        'is-large': large
    }">
        <div class="card-frame">
            <div class="card-photo">
                <img v-if="card" :src="card.img" :alt="card.name" class="card-img" loading="lazy"
                    @error="onImageError" />
                <span v-else class="card-silhouette" aria-hidden="true">
                    <i class="fas fa-lock"></i>
                </span>

                <span v-if="card && rarity === 'rare'" class="card-rarity">
                    <i class="fas fa-star" aria-hidden="true"></i> 稀有
                </span>
                <span v-else-if="!card && rarity === 'rare'" class="card-rarity is-muted">稀有槽位</span>
            </div>

            <span class="card-name">{{ card ? card.name : '未获得' }}</span>
        </div>
    </div>
</template>

<script setup>
defineProps({
    card: { type: Object, default: null },
    rarity: { type: String, default: 'common' },
    large: { type: Boolean, default: false }
})

/** 站内图片加载失败时隐藏 img，露出纸色兜底（不引入外链） */
function onImageError(event) {
    const img = event?.target
    if (img) img.style.opacity = '0'
}
</script>

<style scoped>
/* 拍立得相框：纸白边 + 墨线 + 错位硬阴影 */
.card-visual {
    position: relative;
    width: 100%;
    aspect-ratio: 3 / 4;
    transform: rotate(var(--card-tilt, 0deg));
    transition: transform var(--dur) var(--ease-out);
}

.card-frame {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 8px 8px 0;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    background: var(--paper-1);
    box-shadow: var(--shadow-card);
    overflow: hidden;
    transition: box-shadow var(--dur) var(--ease-out);
}

.card-photo {
    position: relative;
    flex: 1;
    min-height: 0;
    overflow: hidden;
    border: 1.5px solid var(--ink);
    border-radius: 4px;
    background: var(--paper-2);
}

.card-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    transition: transform var(--dur-slow) var(--ease-out), opacity var(--dur);
}

.card-name {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 34px;
    padding: 6px 4px;
    font-family: var(--font-display);
    font-size: 0.82rem;
    font-weight: 800;
    line-height: 1.25;
    color: var(--ink);
    text-align: center;
}

/* hover：轻微抬升 + 更重的错位硬阴影，不做发光 */
.card-visual:hover {
    transform: translate(-3px, -3px) rotate(var(--card-tilt, 0deg));
}

.card-visual:hover .card-frame {
    box-shadow: var(--shadow-float);
}

.card-visual:hover .card-img {
    transform: scale(1.045);
}

/* 稀有角标：奶油黄贴纸 */
.card-rarity {
    position: absolute;
    top: 7px;
    left: 7px;
    z-index: 2;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 24px;
    padding: 0 9px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--butter);
    color: var(--ink);
    font-size: 0.68rem;
    font-weight: 800;
    letter-spacing: 0.04em;
    transform: rotate(-5deg);
    box-shadow: 2px 2px 0 var(--ink);
}

.card-rarity i {
    font-size: 0.7em;
}

.card-rarity.is-muted {
    background: var(--paper-2);
    color: var(--text-3);
    box-shadow: none;
}

/* 未获得：纸面剪影 + 锁 */
.card-silhouette {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-4);
    font-size: 1.9rem;
}

.card-visual.is-locked .card-photo {
    border-style: dashed;
    background: repeating-linear-gradient(135deg, var(--paper-2) 0 9px, var(--paper-1) 9px 18px);
}

.card-visual.is-locked .card-name {
    color: var(--text-4);
    font-size: 0.76rem;
    letter-spacing: 0.14em;
}

.card-visual.is-locked:hover {
    transform: translate(-2px, -2px) rotate(var(--card-tilt, 0deg));
}

.card-visual.is-locked:hover .card-frame {
    box-shadow: var(--shadow-card);
}

/* 稀有卡：奶油黄相框 + 墨线 */
.card-visual.is-rare .card-frame {
    background: var(--butter);
    border-width: 2px;
}

.card-visual.is-rare .card-photo {
    border-width: 2px;
}

.card-visual.is-rare .card-name {
    color: var(--ink);
}

/* 稀有槽位：奶油黄虚线框，提示目标 */
.card-visual.is-rare-slot .card-frame {
    background: var(--butter);
    border-width: 2px;
}

.card-visual.is-rare-slot .card-photo {
    border-style: dashed;
    border-color: var(--ink-soft);
    background: var(--paper-2);
}

.card-visual.is-rare-slot .card-silhouette {
    color: var(--ink-soft);
}

/* 大尺寸（稀有卡主展示） */
.card-visual.is-large .card-frame {
    padding: 10px 10px 0;
    border-radius: var(--r-md);
}

.card-visual.is-large .card-name {
    min-height: 44px;
    font-size: 1.02rem;
}

.card-visual.is-large .card-silhouette {
    font-size: 2.6rem;
}

.card-visual.is-large .card-rarity {
    top: 12px;
    left: 12px;
    height: 27px;
    font-size: 0.74rem;
}

@media (prefers-reduced-motion: reduce) {
    .card-visual:hover,
    .card-visual.is-locked:hover {
        transform: rotate(var(--card-tilt, 0deg));
    }

    .card-visual:hover .card-img {
        transform: none;
    }
}
</style>
