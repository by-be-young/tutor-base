<!-- src/components/ui/LevelBadge.vue -->
<template>
  <div class="level-badge" :class="`level-badge--${size}`" :title="tooltip">
    <div class="level-badge__ring" :style="ringStyle" aria-hidden="true">
      <div class="level-badge__inner">
        <span class="level-badge__lv">LV</span>
        <strong class="level-badge__num">{{ info.level }}</strong>
      </div>
    </div>

    <div v-if="size !== 'xs'" class="level-badge__meta">
      <div class="level-badge__head">
        <strong class="level-badge__title">{{ info.title }}</strong>
        <span class="level-badge__points mono">{{ info.points }} 积分</span>
      </div>
      <div v-if="showProgress" class="progress level-badge__progress">
        <span class="progress__bar" :style="{ width: info.progress + '%' }"></span>
      </div>
      <span class="level-badge__hint">
        距 LV.{{ info.nextLevel }} 还差 <strong>{{ info.toNext }}</strong> 积分 · {{ info.motto }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { levelFromPoints, POINTS_PER_LEVEL } from '@/utils/level'

const props = defineProps({
  points: { type: Number, default: 0 },
  size: { type: String, default: 'md' },
  showProgress: { type: Boolean, default: true }
})

const info = computed(() => levelFromPoints(props.points))
const ringStyle = computed(() => ({
  background: `conic-gradient(var(--vermilion) 0deg, var(--butter) ${info.value.ringDegrees}deg, rgba(28, 26, 20, 0.14) ${info.value.ringDegrees}deg 360deg)`
}))
const tooltip = computed(() =>
  `LV.${info.value.level} ${info.value.title} · ${info.value.points} 积分 · 每 ${POINTS_PER_LEVEL} 积分升 1 级 · 距下一级还差 ${info.value.toNext}`)
</script>

<style scoped>
.level-badge {
  display: flex;
  align-items: center;
  gap: 14px;
}

.level-badge--xs {
  gap: 0;
}

.level-badge__ring {
  --ring-size: 58px;
  width: var(--ring-size);
  height: var(--ring-size);
  padding: 5px;
  border-radius: 50%;
  border: 1.5px solid var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
  flex-shrink: 0;
}

.level-badge--sm .level-badge__ring {
  --ring-size: 44px;
  padding: 4px;
}

.level-badge--xs .level-badge__ring {
  --ring-size: 30px;
  padding: 3px;
  box-shadow: none;
}

.level-badge--lg .level-badge__ring {
  --ring-size: 92px;
  padding: 7px;
  box-shadow: 4px 4px 0 var(--ink);
}

.level-badge__inner {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--paper-1);
  border: 1px solid var(--ink);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.level-badge__lv {
  font-family: var(--font-mono);
  font-size: 0.5rem;
  letter-spacing: 0.12em;
  color: var(--text-3);
}

.level-badge__num {
  font-family: var(--font-display);
  font-size: 1.1rem;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: var(--ink);
}

.level-badge--lg .level-badge__num {
  font-size: 1.8rem;
}

.level-badge--lg .level-badge__lv {
  font-size: 0.6rem;
}

.level-badge--xs .level-badge__num {
  font-size: 0.7rem;
}

.level-badge--xs .level-badge__lv {
  display: none;
}

.level-badge__meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.level-badge__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.level-badge__title {
  font-family: var(--font-display);
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--ink);
}

.level-badge--lg .level-badge__title {
  font-size: 1.3rem;
}

.level-badge__points {
  font-size: 0.68rem;
  color: var(--text-3);
  font-weight: 600;
}

.level-badge__progress {
  max-width: 260px;
  height: 8px;
}

.level-badge__hint {
  font-size: 0.7rem;
  color: var(--text-3);
}

.level-badge__hint strong {
  color: var(--vermilion);
}

@media (max-width: 768px) {
  .level-badge__hint {
    display: none;
  }

  .level-badge--lg .level-badge__ring {
    --ring-size: 72px;
  }
}
</style>
