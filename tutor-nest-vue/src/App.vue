<!-- src/App.vue -->
<template>
  <div id="app">
    <AppHeader @login-click="handleLoginClick" />
    <router-view ref="homeView" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const homeView = ref(null)

function handleLoginClick() {
  // 触发首页的输入框聚焦
  if (homeView.value?.focusUsername) {
    homeView.value.focusUsername()
  }
}

onMounted(() => {
  authStore.initFromStorage()
})
</script>

<style>
/* 全局样式 */
:root {
  --teal: #7BC8C4;
  --teal-dark: #5BA8A4;
  --teal-light: #B5E6E3;
  --teal-pale: #D4F4F2;
  --green: #8FCFB8;
  --gray: #5a7a6a;
  --shadow: rgba(80, 130, 120, 0.06);
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: "Times New Roman", "楷体", "KaiTi", serif;
  background: linear-gradient(145deg, #f0f7f5 0%, #e4f0ed 50%, #dcebe8 100%);
  color: #3d5a4a;
  min-height: 100vh;
  line-height: 1.6;
}

a {
  text-decoration: none;
  color: inherit;
}

#app {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
</style>