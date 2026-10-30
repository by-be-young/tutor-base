// src/stores/blogStore.js
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { flattenManifests } from '@/utils/coursewareManifest'

/** 静态目录索引：`id/title/series/date/path`，仅用于兜底与补充日期、图片路径。 */
async function fetchStaticIndex() {
    const baseUrl = import.meta.env?.BASE_URL || ''
    try {
        const response = await fetch(`${baseUrl}data/articles.json`)
        if (!response.ok) return []
        const data = await response.json()
        return Array.isArray(data) ? data : []
    } catch {
        return []
    }
}

export const useArticleStore = defineStore('blog', () => {
    const blogData = ref([])
    const isLoading = ref(false)
    const error = ref(null)
    const source = ref('none')   // backend | static

    async function loadArticleData() {
        isLoading.value = true
        error.value = null

        const staticIndex = await fetchStaticIndex()
        try {
            const manifests = await coursewareGateway.fetchManifests()
            if (manifests.length) {
                blogData.value = flattenManifests(manifests, staticIndex)
                source.value = 'backend'
                return
            }
        } catch (err) {
            // 未登录、后端未就绪或网络异常：退回静态目录，页面仍可浏览
            console.warn('课件清单接口不可用，回退静态目录:', err?.message || err)
        } finally {
            isLoading.value = false
        }

        if (staticIndex.length) {
            blogData.value = staticIndex
            source.value = 'static'
        } else if (!blogData.value.length) {
            error.value = '加载失败'
            console.error('加载课件目录失败：后端与静态目录均不可用')
        }
    }

    function getArticlesBySubject(subject) {
        return blogData.value.filter(b => b.series === subject)
    }

    function getArticleById(id) {
        return blogData.value.find(b => b.id === id)
    }

    return {
        blogData,
        isLoading,
        error,
        source,
        loadArticleData,
        getArticlesBySubject,
        getArticleById
    }
})