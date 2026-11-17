// src/stores/coursewareStore.js
// 「我的课件」可见范围：直接以数据库托管清单（blogStore.blogData）为准。
// 旧的静态目录同步与学科/单篇认领已下线，这里不再发任何网络请求。
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export const useCoursewareStore = defineStore('courseware', () => {
  const articles = ref([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')

  const visibleArticleIds = computed(() => {
    const ids = new Set()
    for (const article of articles.value) {
      const id = Number(article?.id)
      if (Number.isFinite(id)) ids.add(id)
    }
    return ids
  })

  const visibleSubjects = computed(() => {
    const subjects = new Set()
    for (const article of articles.value) {
      if (article?.series) subjects.add(article.series)
    }
    return [...subjects]
  })

  // 全部可见课件都来自已发布/草稿的数据库清单，没有「待认领」概念。
  const unclaimedCount = computed(() => 0)

  function signature(list) {
    return (Array.isArray(list) ? list : [])
      .map(article => {
        const id = Number(article?.id)
        return Number.isFinite(id) ? `${id}:${article?.series || ''}` : ''
      })
      .filter(Boolean)
      .sort()
      .join(',')
  }

  function apply(list = []) {
    const next = Array.isArray(list) ? list : []
    if (signature(next) !== signature(articles.value)) {
      articles.value = next
    }
    loaded.value = true
    error.value = ''
  }

  /** 页面/面板挂载时调用：接收数据库课件清单并计算可见范围。 */
  async function loadWorkspace(list = []) {
    loading.value = true
    try {
      apply(list)
      return articles.value
    } finally {
      loading.value = false
    }
  }

  /** 清单可能异步到位（先空后满）：签名变化时同步一次即可。 */
  async function ensureWorkspace(list = []) {
    if (!loaded.value || signature(list) !== signature(articles.value)) {
      return loadWorkspace(list)
    }
    return articles.value
  }

  async function refresh() {
    return loadWorkspace(articles.value)
  }

  function isVisible(articleId) {
    const id = Number(articleId)
    return Number.isFinite(id) && visibleArticleIds.value.has(id)
  }

  /** 认领模型已下线，不再存在「学科覆盖但单篇释放」的状态。 */
  function isExcluded() {
    return false
  }

  return {
    articles,
    loading,
    loaded,
    error,
    visibleArticleIds,
    visibleSubjects,
    unclaimedCount,
    loadWorkspace,
    ensureWorkspace,
    refresh,
    isVisible,
    isExcluded
  }
})
