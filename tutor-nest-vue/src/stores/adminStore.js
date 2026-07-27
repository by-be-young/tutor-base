// src/stores/adminStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { supabase } from '@/utils/supabase'

export const useAdminStore = defineStore('admin', () => {
    // 状态
    const students = ref([])
    const currentStudentId = ref(null)
    const permissionDirty = ref(false)

    // 计算属性
    const currentStudent = computed(() => {
        if (!currentStudentId.value) return null
        return students.value.find(s => String(s.id) === String(currentStudentId.value)) || null
    })

    const currentPermissions = computed(() => {
        return currentStudent.value?.permissions || []
    })

    // 方法
    async function loadStudents() {
        const { data, error } = await supabase
            .from('student')
            .select('id, username, permissions')
            .order('username')

        if (error) {
            console.error('加载学生列表失败:', error)
            return
        }

        students.value = data || []
    }

    function setCurrentStudent(studentId) {
        currentStudentId.value = studentId
    }

    async function addStudent(username) {
        const { data, error } = await supabase
            .from('student')
            .insert([{ username: username.trim(), permissions: [] }])
            .select()
            .single()

        if (error) {
            throw new Error('新增失败，请重试')
        }

        if (data) {
            students.value.push(data)
            currentStudentId.value = data.id
        }
    }

    async function updatePermissions(blogId, checked) {
        if (!currentStudent.value) return

        const permissions = [...currentStudent.value.permissions]
        const numericId = Number(blogId)

        if (checked) {
            if (!permissions.includes(numericId)) {
                permissions.push(numericId)
            }
        } else {
            const index = permissions.indexOf(numericId)
            if (index > -1) {
                permissions.splice(index, 1)
            }
        }

        currentStudent.value.permissions = permissions
        permissionDirty.value = true
    }

    async function savePermissions() {
        if (!currentStudent.value) return false

        const { error } = await supabase
            .from('student')
            .update({ permissions: currentStudent.value.permissions })
            .eq('id', currentStudent.value.id)

        if (error) {
            console.error('保存权限失败:', error)
            alert('保存失败，请重试')
            return false
        }

        permissionDirty.value = false
        return true
    }

    return {
        students,
        currentStudentId,
        permissionDirty,
        currentStudent,
        currentPermissions,
        loadStudents,
        setCurrentStudent,
        addStudent,
        updatePermissions,
        savePermissions
    }
})