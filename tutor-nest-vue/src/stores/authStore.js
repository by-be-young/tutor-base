// src/stores/authStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { supabase } from '@/utils/supabase'

export const useAuthStore = defineStore('auth', () => {
    const STORAGE_KEY = 'blog_user'
    const ADMIN_BACKUP_KEY = 'blog_admin_backup' // 管理员进入学生账号前的会话备份

    const currentUser = ref(null)
    const permissionIds = ref([])

    const isLoggedIn = computed(() => !!currentUser.value)
    const username = computed(() => currentUser.value?.username || '')

    // 是否处于「管理员进入学生账号」状态（存在备份且当前不是超级用户）
    const isImpersonating = computed(() => {
        return !!localStorage.getItem(ADMIN_BACKUP_KEY)
            && !!currentUser.value
            && currentUser.value.id !== 'young-super-user'
    })

    function getPermissionIds() {
        return permissionIds.value
    }

    async function loadAllArticleIds() {
        try {
            const res = await fetch(`${import.meta.env.BASE_URL}data/articles.json`)
            if (!res.ok) throw new Error('加载文章数据失败')
            const articles = await res.json()
            if (!Array.isArray(articles)) return []
            return articles
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
            const allPermissions = await loadAllArticleIds()
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
        localStorage.removeItem(ADMIN_BACKUP_KEY)
        currentUser.value = null
        permissionIds.value = []
    }

    /**
     * 管理员快捷进入学生账号：备份当前管理员会话后直接登录为学生，无需账号密码
     * @param {string} username 学生用户名
     */
    async function enterStudentAccount(username) {
        if (currentUser.value) {
            localStorage.setItem(ADMIN_BACKUP_KEY, JSON.stringify(currentUser.value))
        }
        return await login(username)
    }

    /**
     * 从学生账号返回管理员：恢复备份的管理员会话并同步最新权限
     * @returns {object|null} 恢复的管理员用户；无备份时返回 null
     */
    async function restoreAdmin() {
        const raw = localStorage.getItem(ADMIN_BACKUP_KEY)
        if (!raw) return null
        try {
            const admin = JSON.parse(raw)
            localStorage.setItem(STORAGE_KEY, JSON.stringify(admin))
            localStorage.removeItem(ADMIN_BACKUP_KEY)
            setUser(admin)
            await initFromStorage() // 同步最新权限（超级用户刷新全部文章；普通学生查库）
            return admin
        } catch {
            localStorage.removeItem(ADMIN_BACKUP_KEY)
            return null
        }
    }

    function setUser(user) {
        currentUser.value = user
        permissionIds.value = user?.permissions || []
    }

    /**
     * 从本地恢复登录并同步数据库最新信息：
     * - 普通学生：重新查询 student 表同步最新权限；用户已删除则清除本地登录；
     *   数据库不可达时保留本地快照，避免误登出
     * - 超级用户：从 articles.json 刷新全部文章权限（新增文章自动可见）
     */
    async function initFromStorage() {
        const raw = localStorage.getItem(STORAGE_KEY)
        if (!raw) return
        let user
        try {
            user = JSON.parse(raw)
        } catch {
            localStorage.removeItem(STORAGE_KEY)
            return
        }

        // 超级用户：刷新全部文章权限
        if (user.id === 'young-super-user') {
            const allPermissions = await loadAllArticleIds()
            const fresh = { ...user, permissions: allPermissions }
            localStorage.setItem(STORAGE_KEY, JSON.stringify(fresh))
            setUser(fresh)
            return
        }

        // 普通学生：从数据库同步最新信息（权限可能已变更）
        const { data, error } = await supabase
            .from('student')
            .select('id, username, permissions')
            .eq('id', user.id)
            .maybeSingle()

        if (error) {
            // 数据库不可达：保留本地快照，避免误登出
            console.error('同步用户信息失败，使用本地缓存:', error)
            setUser(user)
            return
        }
        if (!data) {
            // 用户已不存在 → 清除本地登录
            logout()
            return
        }

        localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
        setUser(data)
    }

    return {
        currentUser,
        permissionIds,
        isLoggedIn,
        username,
        isImpersonating,
        getPermissionIds,
        login,
        register,
        logout,
        initFromStorage,
        setUser,
        enterStudentAccount,
        restoreAdmin
    }
})