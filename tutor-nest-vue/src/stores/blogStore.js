// src/stores/blogStore.js
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useArticleStore = defineStore('blog', () => {
    const blogData = ref([])
    const isLoading = ref(false)
    const error = ref(null)

    async function loadArticleData() {
        isLoading.value = true
        error.value = null

        try {
            // 从 data/articles.json 加载（使用 BASE_URL 适配 GitHub Pages 子路径）
            const response = await fetch(`${import.meta.env.BASE_URL}data/articles.json`)
            if (!response.ok) throw new Error('加载失败')

            const data = await response.json()
            blogData.value = data
        } catch (err) {
            error.value = err.message
            console.error('加载博客数据失败:', err)
        } finally {
            isLoading.value = false
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
        loadArticleData,
        getArticlesBySubject,
        getArticleById
    }
})