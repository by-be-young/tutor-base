import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { identityGateway } from '@/gateways/identityGateway'
import { learnerGateway } from '@/gateways/learnerGateway'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref(null)
  const permissionIds = ref([])
  const initialized = ref(false)
  let initializationPromise = null

  const isLoggedIn = computed(() => currentUser.value !== null)
  const username = computed(() => currentUser.value?.username || '')
  const roles = computed(() => currentUser.value?.roles || [])
  const isAdministrator = computed(() => roles.value.includes('ADMINISTRATOR'))

  function getPermissionIds() {
    return permissionIds.value
  }

  async function loadAllArticleIds() {
    const response = await fetch(`${import.meta.env.BASE_URL}data/articles.json`)
    if (!response.ok) return []
    const articles = await response.json()
    return Array.isArray(articles)
      ? articles.map(article => Number(article?.id)).filter(Number.isFinite)
      : []
  }

  async function loadLearningPermissions(session) {
    if (session.roles.includes('ADMINISTRATOR')) return loadAllArticleIds()
    if (!session.learnerId) return []
    return learnerGateway.getCurrentContentGrants()
  }

  function normalizeSession(session) {
    const normalizedRoles = Array.isArray(session.roles)
      ? session.roles.map(role => String(role).toUpperCase())
      : []
    return {
      accountId: session.accountId,
      learnerId: session.learnerId ?? null,
      id: session.learnerId ?? null,
      username: session.username,
      roles: normalizedRoles
    }
  }

  async function applySession(session) {
    if (!session) {
      currentUser.value = null
      permissionIds.value = []
      return null
    }
    const user = normalizeSession(session)
    currentUser.value = user
    permissionIds.value = await loadLearningPermissions(user).catch(() => [])
    return user
  }

  async function login(usernameValue, password) {
    await ensureInitialized()
    const username = usernameValue?.trim()
    if (!username || !password) throw new Error('请输入用户名和密码')
    return applySession(await identityGateway.login(username, password))
  }

  async function logout() {
    try {
      await identityGateway.logout()
    } finally {
      await applySession(null)
    }
  }

  async function restoreSession() {
    if (initializationPromise) return initializationPromise
    initializationPromise = (async () => {
      try {
        return await applySession(await identityGateway.getSession())
      } catch {
        return applySession(null)
      } finally {
        initialized.value = true
      }
    })()
    return initializationPromise
  }

  async function ensureInitialized() {
    if (!initialized.value) await restoreSession()
  }

  return {
    currentUser,
    permissionIds,
    initialized,
    isLoggedIn,
    username,
    roles,
    isAdministrator,
    getPermissionIds,
    login,
    logout,
    restoreSession,
    ensureInitialized
  }
})
