// src/stores/uiStore.js
// 全局 UI 状态：命令面板、移动端菜单、登录/注册意图。
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const paletteOpen = ref(false)
  const mobileMenuOpen = ref(false)
  const authIntent = ref(null) // 'login' | 'register' | null
  const authIntentNonce = ref(0)

  function openPalette() {
    mobileMenuOpen.value = false
    paletteOpen.value = true
  }

  function closePalette() {
    paletteOpen.value = false
  }

  function togglePalette() {
    paletteOpen.value ? closePalette() : openPalette()
  }

  function toggleMobileMenu() {
    mobileMenuOpen.value = !mobileMenuOpen.value
  }

  function closeMobileMenu() {
    mobileMenuOpen.value = false
  }

  function requestAuth(mode = 'login') {
    authIntent.value = mode
    authIntentNonce.value += 1
    mobileMenuOpen.value = false
  }

  function consumeAuthIntent() {
    const intent = authIntent.value
    authIntent.value = null
    return intent
  }

  watch(paletteOpen, (open) => {
    if (typeof document === 'undefined') return
    document.body.style.overflow = open ? 'hidden' : ''
  })

  return {
    paletteOpen,
    mobileMenuOpen,
    authIntent,
    authIntentNonce,
    openPalette,
    closePalette,
    togglePalette,
    toggleMobileMenu,
    closeMobileMenu,
    requestAuth,
    consumeAuthIntent
  }
})
