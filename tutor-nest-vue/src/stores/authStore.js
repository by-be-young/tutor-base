// src/stores/authStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { supabase } from '@/utils/supabase'

export const useAuthStore = defineStore('auth', () => {
    const STORAGE_KEY = 'blog_user'

    const currentUser = ref(null)
    const permissionIds = ref([])

    const isLoggedIn = computed(() => !!currentUser.value)
    const username = computed(() => currentUser.value?.username || '')

    function getPermissionIds() {
        return permissionIds.value
    }

    async function loadAllBlogIds() {
        try {
            const res = await fetch(`${import.meta.env.BASE_URL}data/blogs.json`)
            if (!res.ok) throw new Error('加载文章数据失败')
            const blogs = await res.json()
            if (!Array.isArray(blogs)) return []
            return blogs
                .map(blog => Number(blog?.id))
                .filter(Number.isFinite)
        } catch (err) {
            console.error('读取全量权限失败:', err)
            return []
        }
    }

    async function login(username) {
        if (!username || username.trim() === '') {
            throw new Error('用户名不能为空')
        }

        const normalized = username.trim()

        // 超级用户处理
        if (normalized.toLowerCase() === 'young') {
            const allPermissions = await loadAllBlogIds()
            const superUser = {
                id: 'young-super-user',
                username: normalized,
                permissions: allPermissions
            }
            localStorage.setItem(STORAGE_KEY, JSON.stringify(superUser))
            setUser(superUser)
            return superUser
        }

        const { data, error } = await supabase
            .from('student')
            .select('id, username, permissions')
            .eq('username', normalized)
            .maybeSingle()

        if (error) {
            console.error('登录查询失败:', error)
            throw new Error('数据库查询失败')
        }
        if (!data) {
            throw new Error('用户不存在，请先注册')
        }

        localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
        setUser(data)
        return data
    }

    async function register(username) {
        if (!username || username.trim() === '') {
            throw new Error('用户名不能为空')
        }

        const normalized = username.trim()
        const { data, error } = await supabase
            .from('student')
            .insert({
                username: normalized,
                permissions: []
            })
            .select('id, username, permissions')
            .single()

        if (error) {
            console.error('注册失败:', error)
            if (error.code === '23505' || /duplicate|already exists|unique/i.test(error.message || '')) {
                throw new Error('用户名已存在，请直接登录')
            }
            throw new Error('注册失败，请稍后重试')
        }

        localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
        setUser(data)
        return data
    }

    function logout() {
        localStorage.removeItem(STORAGE_KEY)
        currentUser.value = null
        permissionIds.value = []
    }

    function setUser(user) {
        currentUser.value = user
        permissionIds.value = user?.permissions || []
    }

    function initFromStorage() {
        const raw = localStorage.getItem(STORAGE_KEY)
        if (!raw) return
        try {
            const user = JSON.parse(raw)
            setUser(user)
        } catch {
            localStorage.removeItem(STORAGE_KEY)
        }
    }

    return {
        currentUser,
        permissionIds,
        isLoggedIn,
        username,
        getPermissionIds,
        login,
        register,
        logout,
        initFromStorage,
        setUser
    }
})