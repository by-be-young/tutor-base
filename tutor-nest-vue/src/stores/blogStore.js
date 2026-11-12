// src/stores/blogStore.js
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { flattenManifests, groupByTeacher } from '@/utils/coursewareManifest'
import { computed } from 'vue'

export const useArticleStore = defineStore('blog', () => {
    const blogData = ref([])
    const isLoading = ref(false)
    const error = ref(null)
    const source = ref('none')   // backend | none

    async function loadArticleData() {
        isLoading.value = true
        error.value = null

        try {
            const manifests = await coursewareGateway.fetchManifests()
            blogData.value = flattenManifests(manifests)
            source.value = manifests.length ? 'backend' : 'none'
            if (!manifests.length) {
                error.value = '还没有可访问的课件'
            }
        } catch (err) {
            // 正文只来自数据库托管版本：接口不可用时不再回退静态目录，避免页面展示打不开的课件。
            console.warn('课件清单接口不可用:', err?.message || err)
            blogData.value = []
            source.value = 'none'
            error.value = '加载失败'
        } finally {
            isLoading.value = false
        }
    }

    function getArticlesBySubject(subject) {
        return blogData.value.filter(b => b.series === subject)
    }

    /** 多老师分组：同一学员属于多位老师时，首页/目录按老师区分归属。 */
    const teacherGroups = computed(() => groupByTeacher(blogData.value))
    const multipleTeachers = computed(() => teacherGroups.value.length > 1)

    function getArticleById(id) {
        return blogData.value.find(b => b.id === id)
    }

    return {
        blogData,
        isLoading,
        error,
        source,
        teacherGroups,
        multipleTeachers,
        loadArticleData,
        getArticlesBySubject,
        getArticleById
    }
})