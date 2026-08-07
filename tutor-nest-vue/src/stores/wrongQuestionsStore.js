// src/stores/wrongQuestionsStore.js
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { supabase } from '@/utils/supabase'

export const useWrongQuestionsStore = defineStore('wrongQuestions', () => {
    const questions = ref([])
    const isLoading = ref(false)
    const error = ref(null)

    // 获取学生标识（数字 id 转为字符串，超级用户保留 'young-super-user'）
    function getStudentId(user) {
        if (!user?.id) return null
        return String(user.id)
    }

    /**
     * 加载当前学生的全部错题（不含已移除的）
     * 题目内容与学科不存储在表中，展示时按来源文章动态解析
     */
    async function fetchQuestions(studentId) {
        if (!studentId) {
            questions.value = []
            return []
        }

        isLoading.value = true
        error.value = null
        try {
            const { data, error: err } = await supabase
                .from('wrong_questions')
                .select('*')
                .eq('student_id', String(studentId))
                .eq('removed', false)
                .order('updated_at', { ascending: false })

            if (err) {
                console.error('加载错题失败:', err)
                error.value = err.message
                return []
            }
            questions.value = data || []
            return questions.value
        } catch (e) {
            console.error('加载错题失败:', e)
            error.value = e.message
            return []
        } finally {
            isLoading.value = false
        }
    }

    /**
     * 更新错题（任意字段）
     */
    async function updateQuestion(id, data) {
        const { data: updated, error: err } = await supabase
            .from('wrong_questions')
            .update({
                ...data,
                updated_at: new Date().toISOString()
            })
            .eq('id', id)
            .select()
            .single()

        if (err) {
            console.error('更新错题失败:', err)
            throw new Error(err.message || '更新失败，请稍后重试')
        }

        const idx = questions.value.findIndex(q => q.id === id)
        if (idx !== -1) questions.value[idx] = updated
        return updated
    }

    /**
     * 标记 / 取消掌握
     */
    async function toggleMastered(id, mastered) {
        return updateQuestion(id, { mastered: Boolean(mastered) })
    }

    /**
     * 删除错题：手动条目硬删除，自动条目软删除（removed = true）
     */
    async function deleteQuestion(id, isManual = true) {
        if (isManual) {
            const { error: err } = await supabase
                .from('wrong_questions')
                .delete()
                .eq('id', id)

            if (err) {
                console.error('删除错题失败:', err)
                throw new Error(err.message || '删除失败，请稍后重试')
            }
            questions.value = questions.value.filter(q => q.id !== id)
        } else {
            const updated = await updateQuestion(id, { removed: true })
            questions.value = questions.value.filter(q => q.id !== id)
            return updated
        }
        return null
    }

    /**
     * 自动收集错题（批阅为错误时调用）
     * 存在相同 (student_id, source_blog_id, source_question_id) 的记录时：
     *   - wrong_count + 1
     *   - 保留已填写的错因、笔记、掌握状态等增强字段
     * 不存在则新建记录
     */
    async function autoCollect({ studentId, sourceArticleId, sourceQuestionId, myAnswer }) {
        if (!studentId || !sourceArticleId || sourceQuestionId === undefined) return null

        const studentIdStr = String(studentId)
        const sourceQuestionIdStr = String(sourceQuestionId)

        // 1. 查询是否已存在该来源的记录
        const { data: existing, error: queryErr } = await supabase
            .from('wrong_questions')
            .select('id, wrong_count, wrong_reason, note, mastered')
            .eq('student_id', studentIdStr)
            .eq('source_blog_id', sourceArticleId)
            .eq('source_question_id', sourceQuestionIdStr)
            .maybeSingle()

        if (queryErr) {
            console.error('查询已有错题失败:', queryErr)
            return null
        }

        const row = {
            student_id: studentIdStr,
            my_answer: myAnswer || '',
            is_manual: false,
            source_blog_id: sourceArticleId,
            source_question_id: sourceQuestionIdStr,
            wrong_count: 1,
            updated_at: new Date().toISOString()
        }

        if (existing) {
            row.wrong_count = (existing.wrong_count || 1) + 1
            // 保留已有的增强字段
            row.wrong_reason = existing.wrong_reason || ''
            row.note = existing.note || ''
            row.mastered = existing.mastered || false
        }

        const { data: upserted, error: upsertErr } = await supabase
            .from('wrong_questions')
            .upsert(row, { onConflict: 'student_id,source_blog_id,source_question_id' })
            .select()
            .single()

        if (upsertErr) {
            console.error('自动收集错题失败:', upsertErr)
            return null
        }

        // 同步到本地列表
        const idx = questions.value.findIndex(q => q.id === upserted.id)
        if (idx !== -1) {
            questions.value[idx] = upserted
        } else {
            questions.value.unshift(upserted)
        }
        return upserted
    }

    return {
        questions,
        isLoading,
        error,
        getStudentId,
        fetchQuestions,
        updateQuestion,
        toggleMastered,
        deleteQuestion,
        autoCollect
    }
})
