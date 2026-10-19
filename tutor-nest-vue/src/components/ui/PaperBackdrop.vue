<!-- src/components/ui/PaperBackdrop.vue -->
<template>
  <div class="paper-decor" aria-hidden="true">
    <span class="paper-decor__shape paper-decor__shape--strip"></span>
    <span class="paper-decor__shape paper-decor__shape--circle"></span>
    <span class="paper-decor__shape paper-decor__shape--rect"></span>
    <span class="paper-decor__halftone"></span>
  </div>
</template>

<script setup>
defineProps({
  dense: { type: Boolean, default: false }
})
</script>
