// src/stores/tasksStore.js
// 任务中心数据：积分、等级累计积分、卡片收藏与收藏统计统一由后端按当前会话提供。
// 抽卡结果、概率与卡片目录均由服务端决定，前端只负责展示。
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { rewardGateway } from '@/gateways/rewardGateway'
import { useCardCatalogStore } from '@/stores/cardCatalogStore'

function emptyStats() {
  return {
    totalDraws: 0,
    totalCards: 0,
    uniqueCards: 0,
    totalCatalogCards: 0,
    totalSets: 0
  }
}

/** 服务端收藏记录 → 前端内部结构 */
function mapCollectionRow(row) {
  return {
    id: row.id,
    cardKey: row.cardKey ?? row.card_key ?? '',
    setKey: row.setKey ?? row.set_key ?? '',
    rarity: row.rarity || 'common',
    claimedAt: row.claimedAt ?? row.claimed_at ?? null
  }
}

function uniqueCardKeys(rows) {
  return new Set(rows.map(row => row.cardKey).filter(Boolean))
}

export const useTasksStore = defineStore('tasks', () => {
  const catalogStore = useCardCatalogStore()

  const points = ref(0)
  const lifetimePoints = ref(0)
  const collection = ref([])
  const stats = ref(emptyStats())
  const isLoading = ref(false)
  const loadError = ref(null)

  /** 从身份会话映射学习者 id；无学习者身份时返回 null（无法记录积分） */
  function getStudentId(user) {
    return user?.learnerId != null ? String(user.learnerId) : null
  }

  function applyStats(raw, fallbackRows = collection.value) {
    const unique = uniqueCardKeys(fallbackRows).size
    const base = emptyStats()
    stats.value = {
      totalDraws: Number(raw?.totalDraws ?? base.totalDraws) || 0,
      totalCards: Number(raw?.totalCards ?? fallbackRows.length) || 0,
      uniqueCards: Number(raw?.uniqueCards ?? unique) || 0,
      totalCatalogCards: Number(raw?.totalCatalogCards ?? catalogStore.totalCards) || 0,
      totalSets: Number(raw?.totalSets ?? catalogStore.totalSets) || 0
    }
  }

  /** 加载当前用户的积分与卡片收藏；学习者身份由后端会话确定。 */
  async function load(user) {
    const sid = getStudentId(user)
    points.value = 0
    lifetimePoints.value = 0
    collection.value = []
    stats.value = emptyStats()
    if (!sid) return

    isLoading.value = true
    loadError.value = null
    try {
      const summary = await rewardGateway.getSummary()
      collection.value = (summary.collection || []).map(mapCollectionRow)
      points.value = Number(summary.points ?? 0)
      lifetimePoints.value = Number(summary.lifetimePoints ?? summary.points ?? 0)
      applyStats(summary.stats)
    } catch (e) {
      console.error('加载任务数据失败:', e)
      loadError.value = e.message || '加载任务数据失败'
    } finally {
      isLoading.value = false
    }
  }

  /** 当前用户已获得的卡片 key 集合（收藏室高亮用） */
  function obtainedCardKeys() {
    return uniqueCardKeys(collection.value)
  }

  /**
   * 卡池抽卡：抽卡、扣积分、概率与 UP 判定全部由后端完成。
   * @param {number} count 抽卡次数（1 或 10）
   * @returns {Promise<Array>} 每张卡附带目录信息、是否重复、是否 UP
   */
  async function drawCards(count = 10) {
    const before = obtainedCardKeys()
    const response = await rewardGateway.draw(count)
    points.value = Number(response.points ?? points.value)
    if (Number.isFinite(Number(response.lifetimePoints))) {
      lifetimePoints.value = Number(response.lifetimePoints)
    }
    const rawCards = Array.isArray(response.cards) ? response.cards : []
    const rows = rawCards.map(mapCollectionRow)
    const seen = new Set(before)
    await catalogStore.load().catch(() => {})
    catalogStore.syncCurrentUp(response.currentUp)
    const results = rawCards.map((raw, index) => {
      const row = rows[index]
      const duplicate = seen.has(row.cardKey)
      seen.add(row.cardKey)
      return {
        ...row,
        card: catalogStore.findCard(row.setKey, row.cardKey),
        duplicate,
        isUp: !!raw.isUp
      }
    })
    collection.value = [...rows, ...collection.value]
    applyStats(response.stats)
    return results
  }

  /** 单抽（保留旧调用方式） */
  async function drawCard() {
    const results = await drawCards(1)
    return results[0]
  }

  /**
   * 后端（签到 / 任务 / 成就等）发放积分后同步本地显示。
   * 传入新的可用积分；若同时知道服务端累计积分则直接采用，
   * 否则按积分增量累加 lifetimePoints（消耗积分不会降低等级）。
   */
  function syncPoints(next, lifetime) {
    const numeric = Number(next)
    if (!Number.isFinite(numeric)) return
    const delta = numeric - points.value
    points.value = numeric
    const explicitLifetime = Number(lifetime)
    if (Number.isFinite(explicitLifetime)) {
      lifetimePoints.value = explicitLifetime
      return
    }
    if (delta > 0) lifetimePoints.value += delta
  }

  return {
    points,
    lifetimePoints,
    collection,
    stats,
    isLoading,
    loadError,
    getStudentId,
    load,
    drawCard,
    drawCards,
    obtainedCardKeys,
    syncPoints
  }
})
