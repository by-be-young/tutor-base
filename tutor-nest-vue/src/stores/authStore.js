import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { identityGateway } from '@/gateways/identityGateway'
import { learnerGateway } from '@/gateways/learnerGateway'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { flattenManifests } from '@/utils/coursewareManifest'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref(null)
  const permissionIds = ref([])
  const initialized = ref(false)
  let initializationPromise = null

  const isLoggedIn = computed(() => currentUser.value !== null)
  const username = computed(() => currentUser.value?.username || '')
  const roles = computed(() => currentUser.value?.roles || [])
  const isAdministrator = computed(() => roles.value.includes('ADMINISTRATOR'))
  const isSuperAdministrator = computed(() =>
    isAdministrator.value && currentUser.value?.superAdmin === true)

  function getPermissionIds() {
    return permissionIds.value
  }

  /** 管理员：自己托管且已发布的课件 ID（列表接口已排除未托管条目）。 */
  async function loadAdministratorArticleIds() {
    const articles = await coursewareGateway.listArticles()
    return (articles || [])
      .filter(article => article && article.published !== false)
      .map(article => Number(article.legacyArticleId ?? article.articleId))
      .filter(Number.isFinite)
  }

  /**
   * 特殊测试学员：全部课件 ID 来自数据库清单。
   * 没有可读课件时返回空数组，不再回退静态目录。
   */
  async function loadAllArticleIds() {
    const manifests = await coursewareGateway.fetchManifests()
    return flattenManifests(manifests).map(article => Number(article.id)).filter(Number.isFinite)
  }

  async function loadLearningPermissions(session) {
    if (session.roles.includes('ADMINISTRATOR')) return loadAdministratorArticleIds()
    if (!session.learnerId) return []
    const grants = await learnerGateway.getCurrentContentGrants()
    // 特殊测试学员拥有全部文章权限：按数据库清单全量放行。
    if (grants.allArticles) return loadAllArticleIds()
    return grants.articleIds
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
      roles: normalizedRoles,
      superAdmin: session.superAdmin === true
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

  async function register(usernameValue, password) {
    await ensureInitialized()
    const username = usernameValue?.trim()
    if (!username || !password) throw new Error('请输入用户名和密码')
    return applySession(await identityGateway.register(username, password))
  }

  async function submitTeacherApplication(usernameValue, password) {
    await ensureInitialized()
    const username = usernameValue?.trim()
    if (!username || !password) throw new Error('请输入用户名和密码')
    // 只提交申请，不写入登录态；超级管理员审核通过后才能登录。
    return identityGateway.submitTeacherApplication(username, password)
  }

  async function logout() {
    try {
      await identityGateway.logout()
    } finally {
      await applySession(null)
    }
  }

  async function impersonateLearner(learnerId) {
    await ensureInitialized()
    return applySession(await identityGateway.impersonateLearner(learnerId))
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
    isSuperAdministrator,
    getPermissionIds,
    login,
    register,
    submitTeacherApplication,
    logout,
    impersonateLearner,
    restoreSession,
    ensureInitialized
  }
})
