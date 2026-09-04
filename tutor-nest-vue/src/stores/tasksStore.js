// src/stores/tasksStore.js
// 任务中心数据：积分与卡片收藏统一由后端按当前会话提供。
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { milestoneCard } from '@/data/cardCatalog'
import { rewardGateway } from '@/gateways/rewardGateway'

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

    /** 加载当前用户的积分与卡片收藏；学习者身份由后端会话确定。 */
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
            const summary = await rewardGateway.getSummary()
            points.value = Number(summary.points ?? 0)
            collection.value = (summary.collection || []).map(card => ({
                id: card.id,
                milestone_points: card.milestonePoints,
                card_key: card.cardKey,
                set_key: card.setKey,
                rarity: card.rarity,
                claimed_at: card.claimedAt
            }))
            collection.value.forEach(row => claimedMilestones.add(Number(row.milestone_points)))
        } catch (e) {
            console.error('加载任务数据失败:', e)
            loadError.value = e.message || '加载任务数据失败'
        } finally {
            isLoading.value = false
        }
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

        const claimed = await rewardGateway.claimMilestone(milestone.pts)
        const { card } = milestoneCard(claimed.milestonePoints)

        claimedMilestones.add(claimed.milestonePoints)
        collection.value = [
            {
                id: claimed.id,
                milestone_points: claimed.milestonePoints,
                card_key: claimed.cardKey,
                set_key: claimed.setKey,
                rarity: claimed.rarity,
                claimed_at: claimed.claimedAt
            },
            ...collection.value
        ]
        return card
    }

    /** 当前用户已获得的卡片 key 集合（收藏室高亮用） */
    function obtainedCardKeys() {
        return new Set(collection.value.map(row => row.card_key))
    }

    /** 后端（每日签到等）发放积分后同步本地显示 */
    function syncPoints(next) {
        points.value = next
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
        claimMilestone,
        obtainedCardKeys,
        syncPoints
    }
})
