<!-- src/components/ui/EmptyState.vue -->
<template>
  <div class="empty">
    <span class="empty__icon">
      <i :class="icon"></i>
    </span>
    <p v-if="title" class="empty__title">{{ title }}</p>
    <p v-if="text" class="empty__text">{{ text }}</p>
    <div v-if="$slots.default" class="empty__actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  icon: { type: String, default: 'fas fa-compass' },
  title: { type: String, default: '' },
  text: { type: String, default: '' }
})
</script>
