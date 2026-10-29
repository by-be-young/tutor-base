// src/stores/coursewareStore.js
// 管理员课件认领：同步静态目录、读取认领范围，并给业务面板提供「我的课件」。
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import {
  catalogSignature,
  catalogSyncItems,
  computeCatalogBySubject,
  computeUnclaimedCount,
  computeVisibleArticleIds,
  computeVisibleSubjects,
  coursewareActionError
} from '@/utils/coursewareClaims'

export const useCoursewareStore = defineStore('courseware', () => {
  // catalogItems 为 scope=all 的完整目录；unclaimedItems 为 scope=unclaimed 的待认领目录。
  const catalogItems = ref([])
  const subjects = ref([])
  const subjectClaims = ref([])
  const articleClaims = ref([])
  const unclaimedItems = ref([])
  const loading = ref(false)
  const syncing = ref(false)
  const loaded = ref(false)
  const error = ref('')

  // 最近一次同步的目录签名与文章快照，用于幂等 sync 和刷新。
  const lastSyncedSignature = ref(null)
  const lastArticles = ref([])
  let inflight = null

  const visibleArticleIds = computed(() => computeVisibleArticleIds(
    catalogItems.value,
    subjectClaims.value,
    articleClaims.value
  ))

  const visibleSubjects = computed(() => computeVisibleSubjects(
    subjects.value,
    catalogItems.value,
    visibleArticleIds.value
  ))

  const unclaimedCount = computed(() => computeUnclaimedCount(catalogItems.value))

  const catalogBySubject = computed(() => computeCatalogBySubject(catalogItems.value))

  function isVisible(articleId) {
    const id = Number(articleId)
    return Number.isFinite(id) && visibleArticleIds.value.has(id)
  }

  async function syncCatalogIfNeeded(articles = []) {
    const items = catalogSyncItems(articles)
    const signature = catalogSignature(items)
    // 目录为空时不发请求，避免首次进入把空目录推送上去；文章到位后签名变化会再同步。
    if (items.length === 0) {
      lastSyncedSignature.value = signature
      return
    }
    if (signature === lastSyncedSignature.value) return

    syncing.value = true
    try {
      await coursewareGateway.syncCatalog(items)
      lastSyncedSignature.value = signature
    } finally {
      syncing.value = false
    }
  }

  async function fetchClaimsAndCatalog() {
    const [claims, catalog, unclaimed] = await Promise.all([
      coursewareGateway.getClaims(),
      coursewareGateway.getCatalog('all'),
      coursewareGateway.getCatalog('unclaimed')
    ])
    subjectClaims.value = claims.subjectClaims
    articleClaims.value = claims.articleClaims
    subjects.value = catalog.subjects
    catalogItems.value = catalog.items
    unclaimedItems.value = unclaimed.items
  }

  /**
   * 首次进入课件页/业务页时调用：仅在文章目录变化或首次时同步，再并行拉取认领与目录。
   * 失败会写入 error 并抛出，调用方可 toast。
   */
  async function loadWorkspace(articles = []) {
    if (inflight) return inflight
    lastArticles.value = Array.isArray(articles) ? articles : []
    loading.value = true
    error.value = ''

    inflight = (async () => {
      try {
        await syncCatalogIfNeeded(lastArticles.value)
        await fetchClaimsAndCatalog()
        loaded.value = true
      } catch (err) {
        error.value = err?.message || '加载课件目录失败，请稍后重试'
        throw err
      } finally {
        loading.value = false
        inflight = null
      }
    })()

    return inflight
  }

  /** 业务面板使用：目录已加载且文章签名一致时直接复用，避免重复请求。 */
  async function ensureWorkspace(articles = []) {
    const items = catalogSyncItems(articles)
    const signature = catalogSignature(items)
    if (loaded.value && signature === lastSyncedSignature.value) return
    return loadWorkspace(articles)
  }

  async function refresh() {
    if (inflight) return inflight
    return loadWorkspace(lastArticles.value)
  }

  async function reloadCatalog() {
    const [catalog, unclaimed] = await Promise.all([
      coursewareGateway.getCatalog('all'),
      coursewareGateway.getCatalog('unclaimed')
    ])
    subjects.value = catalog.subjects
    catalogItems.value = catalog.items
    unclaimedItems.value = unclaimed.items
  }

  // 认领/释放已经成功后，目录刷新失败不应让用户以为操作失败：只记录 error。
  async function safeReloadCatalog() {
    try {
      await reloadCatalog()
    } catch (err) {
      error.value = err?.message || '刷新课件目录失败，请稍后重试'
    }
  }

  async function claimSubject(subject) {
    const name = String(subject ?? '').trim()
    if (!name) return null
    try {
      const result = await coursewareGateway.claimSubject(name)
      if (!subjectClaims.value.includes(name)) {
        subjectClaims.value = [...subjectClaims.value, name]
      }
      await safeReloadCatalog()
      return result
    } catch (err) {
      throw coursewareActionError(err, '认领学科')
    }
  }

  async function releaseSubject(subject) {
    const name = String(subject ?? '').trim()
    if (!name) return null
    try {
      await coursewareGateway.releaseSubject(name)
      subjectClaims.value = subjectClaims.value.filter(item => item !== name)
      await safeReloadCatalog()
      return true
    } catch (err) {
      throw coursewareActionError(err, '释放学科')
    }
  }

  async function claimArticle(articleId) {
    const id = Number(articleId)
    if (!Number.isFinite(id) || id <= 0) return null
    try {
      const result = await coursewareGateway.claimArticle(id)
      if (!articleClaims.value.includes(id)) {
        articleClaims.value = [...articleClaims.value, id]
      }
      await safeReloadCatalog()
      return result
    } catch (err) {
      throw coursewareActionError(err, '认领课件')
    }
  }

  async function releaseArticle(articleId) {
    const id = Number(articleId)
    if (!Number.isFinite(id) || id <= 0) return null
    try {
      await coursewareGateway.releaseArticle(id)
      articleClaims.value = articleClaims.value.filter(item => Number(item) !== id)
      await safeReloadCatalog()
      return true
    } catch (err) {
      throw coursewareActionError(err, '释放课件')
    }
  }

  return {
    catalogItems,
    subjects,
    subjectClaims,
    articleClaims,
    unclaimedItems,
    loading,
    syncing,
    loaded,
    error,
    visibleArticleIds,
    visibleSubjects,
    unclaimedCount,
    catalogBySubject,
    loadWorkspace,
    ensureWorkspace,
    refresh,
    claimSubject,
    releaseSubject,
    claimArticle,
    releaseArticle,
    isVisible
  }
})
