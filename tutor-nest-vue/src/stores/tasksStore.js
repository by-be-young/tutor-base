// src/stores/tasksStore.js
// 任务中心数据：积分、任务领取记录、卡片收藏
// ----------------------------------------------------------------------------
// 与 wrong_questions 一致，通过 Supabase anon key 直接读写（表不启用 RLS），
// 行归属用 student_id（来自身份会话的 learnerId）约束。
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { supabase } from '@/utils/supabase'
import { milestoneCard } from '@/data/cardCatalog'

export const useTasksStore = defineStore('tasks', () => {
    const points = ref(0)
    const claimedTaskIds = reactive(new Set())
    const claimedMilestones = reactive(new Set())
    const collection = ref([])
    const isLoading = ref(false)
    const loadError = ref(null)

    /** 从身份会话映射学习者 id；无学习者身份时返回 null（无法记录积分） */
    function getStudentId(user) {
        return user?.learnerId != null ? String(user.learnerId) : null
    }

    /** 加载当前用户的积分、任务领取记录与卡片收藏 */
    async function load(user) {
        const sid = getStudentId(user)
        claimedTaskIds.clear()
        claimedMilestones.clear()
        collection.value = []
        points.value = 0
        if (!sid) return

        isLoading.value = true
        loadError.value = null
        try {
            const [pointsRes, claimsRes, cardsRes] = await Promise.all([
                supabase.from('user_points').select('points').eq('student_id', sid).maybeSingle(),
                supabase.from('task_claims').select('task_id').eq('student_id', sid),
                supabase.from('card_collection').select('*').eq('student_id', sid)
            ])
            if (pointsRes.error) throw pointsRes.error
            if (claimsRes.error) throw claimsRes.error
            if (cardsRes.error) throw cardsRes.error

            points.value = pointsRes.data?.points ?? 0
            ;(claimsRes.data || []).forEach(row => claimedTaskIds.add(Number(row.task_id)))
            collection.value = cardsRes.data || []
            collection.value.forEach(row => claimedMilestones.add(Number(row.milestone_points)))
        } catch (e) {
            console.error('加载任务数据失败:', e)
            loadError.value = e.message || '加载任务数据失败'
        } finally {
            isLoading.value = false
        }
    }

    /**
     * 管理员领取任务积分：记录 task_claims 并累加 user_points
     * @param {object} user 当前身份会话
     * @param {number} taskId 任务 id
     * @param {number} taskPoints 该任务奖励积分
     * @returns {Promise<number>} 领取后的积分
     */
    async function claimTask(user, taskId, taskPoints) {
        const sid = getStudentId(user)
        if (!sid) throw new Error('当前账号未关联学习者身份，无法领取积分')
        if (claimedTaskIds.has(taskId)) throw new Error('该任务已领取过积分')

        const { error: claimErr } = await supabase
            .from('task_claims')
            .insert({ student_id: sid, task_id: taskId })
        if (claimErr) throw new Error(claimErr.message || '领取任务积分失败')

        const { data: row, error: readErr } = await supabase
            .from('user_points')
            .select('points')
            .eq('student_id', sid)
            .maybeSingle()
        if (readErr) throw new Error(readErr.message || '读取积分失败')

        const next = (row?.points ?? 0) + taskPoints
        const { error: writeErr } = await supabase
            .from('user_points')
            .upsert({ student_id: sid, points: next, updated_at: new Date().toISOString() }, { onConflict: 'student_id' })
        if (writeErr) throw new Error(writeErr.message || '积分更新失败')

        points.value = next
        claimedTaskIds.add(taskId)
        return next
    }

    /**
     * 领取里程碑卡片：写入 card_collection（同一里程碑只能领取一次）
     * @param {object} user 当前身份会话
     * @param {object} milestone 里程碑 { pts, isRare, ... }
     * @returns {Promise<object>} 领取到的卡片
     */
    async function claimMilestone(user, milestone) {
        const sid = getStudentId(user)
        if (!sid) throw new Error('当前账号未关联学习者身份，无法领取卡片')
        if (claimedMilestones.has(milestone.pts)) throw new Error('该里程碑已领取过卡片')

        const { setKey, rarity, cardKey, card } = milestoneCard(milestone.pts)
        const { error } = await supabase
            .from('card_collection')
            .insert({ student_id: sid, milestone_points: milestone.pts, card_key: cardKey, set_key: setKey, rarity })
        if (error) throw new Error(error.message || '领取卡片失败')

        claimedMilestones.add(milestone.pts)
        collection.value = [
            { student_id: sid, milestone_points: milestone.pts, card_key: cardKey, set_key: setKey, rarity },
            ...collection.value
        ]
        return card
    }

    /** 当前用户已获得的卡片 key 集合（收藏室高亮用） */
    function obtainedCardKeys() {
        return new Set(collection.value.map(row => row.card_key))
    }

    return {
        points,
        claimedTaskIds,
        claimedMilestones,
        collection,
        isLoading,
        loadError,
        getStudentId,
        load,
        claimTask,
        claimMilestone,
        obtainedCardKeys
    }
})
