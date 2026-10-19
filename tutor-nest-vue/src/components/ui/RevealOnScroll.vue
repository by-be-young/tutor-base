<!-- src/components/ui/RevealOnScroll.vue -->
<template>
  <component :is="as" ref="rootRef" class="reveal" :class="revealClass" :style="revealStyle">
    <slot />
  </component>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  as: { type: String, default: 'div' },
  delay: { type: Number, default: 0 },
  direction: { type: String, default: 'up' },
  once: { type: Boolean, default: true },
  threshold: { type: Number, default: 0.12 }
})

const rootRef = ref(null)
let observer = null
let revealed = false

const revealClass = computed(() => ({
  [`reveal--${props.direction}`]: props.direction !== 'up'
}))

const revealStyle = computed(() => ({
  '--reveal-delay': `${props.delay}s`
}))

onMounted(() => {
  const el = rootRef.value
  if (!el) return
  if (typeof IntersectionObserver === 'undefined') {
    el.classList.add('is-visible')
    return
  }
  observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue
      el.classList.add('is-visible')
      revealed = true
      if (props.once) observer?.disconnect()
    }
  }, { threshold: props.threshold, rootMargin: '0px 0px -8% 0px' })
  observer.observe(el)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})

defineExpose({ revealed: () => revealed })
</script>
