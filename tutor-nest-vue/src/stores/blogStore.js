// src/stores/blogStore.js
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useBlogStore = defineStore('blog', () => {
    const blogData = ref([])
    const isLoading = ref(false)
    const error = ref(null)

    async function loadBlogData() {
        isLoading.value = true
        error.value = null

        try {
            // 从 data/blogs.json 加载
            const response = await fetch('/data/blogs.json')
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

    function getBlogsBySubject(subject) {
        return blogData.value.filter(b => b.series === subject)
    }

    function getBlogById(id) {
        return blogData.value.find(b => b.id === id)
    }

    return {
        blogData,
        isLoading,
        error,
        loadBlogData,
        getBlogsBySubject,
        getBlogById
    }
})