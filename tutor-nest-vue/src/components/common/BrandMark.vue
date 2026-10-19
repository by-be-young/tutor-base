<!-- src/components/common/BrandMark.vue -->
<template>
  <svg class="brand-mark" :width="size" :height="size" viewBox="0 0 48 48" fill="none" aria-hidden="true">
    <rect x="1" y="1" width="46" height="46" rx="11" fill="#1c1a14" stroke="#1c1a14" stroke-width="2" />
    <circle cx="24" cy="24" r="13.4" stroke="#fffdf6" stroke-width="2" />
    <ellipse cx="24" cy="24" rx="15" ry="6.4" transform="rotate(-32 24 24)" stroke="#ffc53d" stroke-width="1.8" />
    <circle cx="24" cy="24" r="4.4" fill="#ed4b2a" stroke="#fffdf6" stroke-width="1.2" />
    <circle cx="36.8" cy="13.6" r="2.8" fill="#c9ef4a" stroke="#1c1a14" stroke-width="1.2" />
    <circle cx="10.6" cy="35.2" r="1.8" fill="#7cc5f0" />
  </svg>
</template>

<script setup>
defineProps({
  size: { type: [Number, String], default: 36 }
})
</script>

<style scoped>
.brand-mark {
  display: block;
  flex-shrink: 0;
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}

.brand-mark:hover {
  transform: rotate(-8deg) scale(1.04);
}
</style>
