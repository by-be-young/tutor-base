<!-- src/components/ui/CountUp.vue -->
<template>
  <span ref="elRef" class="count-up">{{ display }}</span>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  animate: { type: Boolean, default: false },
  duration: { type: Number, default: 1000 },
  decimals: { type: Number, default: 0 },
  prefix: { type: String, default: '' },
  suffix: { type: String, default: '' },
  separator: { type: Boolean, default: true }
})

const elRef = ref(null)
const current = ref(Number(props.value) || 0)
let observer = null
let rafId = 0
let started = false

const formatter = computed(() => new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: props.decimals,
  maximumFractionDigits: props.decimals,
  useGrouping: props.separator
}))

const display = computed(() => `${props.prefix}${formatter.value.format(current.value)}${props.suffix}`)

function reducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

function animate() {
  if (started) return
  started = true
  const target = Number(props.value) || 0
  if (reducedMotion() || props.duration === 0) {
    current.value = target
    return
  }
  const startTime = performance.now()
  current.value = 0
  function tick(now) {
    const progress = Math.min(1, (now - startTime) / props.duration)
    const eased = 1 - Math.pow(1 - progress, 3)
    current.value = target * eased
    if (progress < 1) rafId = requestAnimationFrame(tick)
    else current.value = target
  }
  rafId = requestAnimationFrame(tick)
}

watch(() => props.value, (next) => {
  const target = Number(next) || 0
  if (!props.animate || !started || reducedMotion()) {
    current.value = target
    return
  }
  current.value = target
})

onMounted(() => {
  if (!props.animate) return
  if (typeof IntersectionObserver === 'undefined' || !elRef.value) {
    animate()
    return
  }
  observer = new IntersectionObserver((entries) => {
    if (entries.some(entry => entry.isIntersecting)) {
      animate()
      observer?.disconnect()
    }
  }, { threshold: 0.4 })
  observer.observe(elRef.value)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  observer?.disconnect()
})
</script>

<style scoped>
.count-up {
  font-variant-numeric: tabular-nums;
  display: inline-block;
}
</style>
