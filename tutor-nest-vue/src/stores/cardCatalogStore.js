// src/stores/cardCatalogStore.js
// 收藏卡池目录：卡组、卡片、当期 UP、抽卡价格与概率全部由后端 /api/v1/rewards/catalog 提供。
// 前端不硬编码卡组数量、卡片数量或 UP 轮换规则；卡池更新后刷新即可生效。
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { rewardGateway } from '@/gateways/rewardGateway'

const BASE_URL = import.meta.env.BASE_URL || '/'

/** 站内相对图片路径补全 BASE_URL；外链/内联图片保持原样 */
export function resolveCardImage(image) {
  if (!image) return ''
  const value = String(image)
  if (/^(?:https?:)?\/\//i.test(value) || value.startsWith('data:') || value.startsWith('blob:')) {
    return value
  }
  return `${BASE_URL}${value.replace(/^\/+/, '')}`
}

function normalizeCard(raw, fallbackSetKey, fallbackRarity, set) {
  if (!raw) return null
  const cardKey = raw.cardKey ?? raw.card_key ?? ''
  const setName = raw.setName ?? raw.set_name ?? set?.name ?? ''
  const image = raw.image ?? raw.img ?? ''
  return {
    cardKey,
    name: raw.name || cardKey,
    image,
    img: resolveCardImage(image),
    rarity: raw.rarity || fallbackRarity || 'common',
    setKey: raw.setKey ?? raw.set_key ?? fallbackSetKey ?? '',
    setName,
    setColors: set ? [set.colorA, set.colorB].filter(Boolean) : []
  }
}

function normalizeSet(raw) {
  const colorA = raw.colorA || raw.colors?.[0] || ''
  const colorB = raw.colorB || raw.colors?.[1] || ''
  const set = {
    key: raw.key,
    name: raw.name || raw.key,
    theme: raw.theme || '',
    style: raw.style || '',
    emblem: raw.emblem || 'fas fa-images',
    colorA,
    colorB,
    colors: [colorA, colorB].filter(Boolean)
  }
  set.rare = normalizeCard(raw.rare, set.key, 'rare', set)
  set.commons = (raw.commons || [])
    .map(card => normalizeCard(card, set.key, 'common', set))
    .filter(Boolean)
  set.cardCount = 1 + set.commons.length
  return set
}

export const useCardCatalogStore = defineStore('cardCatalog', () => {
  const sets = ref([])
  const currentUp = ref(null)
  const drawCost = ref(0)
  const rareRate = ref(0)
  const upRateWhenRare = ref(0)
  const totalCards = ref(0)
  const totalSets = ref(0)
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')

  let inflight = null

  const cardBySet = computed(() => {
    const map = new Map()
    for (const set of sets.value) {
      if (set.rare) map.set(set.rare.cardKey, set.rare)
      for (const common of set.commons) map.set(common.cardKey, common)
    }
    return map
  })

  const setByKey = computed(() => new Map(sets.value.map(set => [set.key, set])))

  const rareRatePercent = computed(() => formatPercent(rareRate.value))
  const upRatePercent = computed(() => formatPercent(upRateWhenRare.value))

  function formatPercent(rate) {
    const numeric = Number(rate)
    if (!Number.isFinite(numeric)) return '—'
    return `${Math.round(numeric * 10000) / 100}%`
  }

  function normalizeUp(raw) {
    if (!raw) return null
    const set = setByKey.value.get(raw.setKey) || null
    const card = normalizeCard(raw, raw.setKey, 'rare', set)
    return {
      ...card,
      setName: raw.setName || card.setName || set?.name || '',
      rotatesAt: raw.rotatesAt || null
    }
  }

  function applyCatalog(data) {
    const rawSets = Array.isArray(data?.sets) ? data.sets : []
    sets.value = rawSets.map(normalizeSet).filter(set => set.key)
    currentUp.value = normalizeUp(data?.currentUp)
    drawCost.value = Number(data?.drawCost) || 0
    rareRate.value = Number(data?.rareRate) || 0
    upRateWhenRare.value = Number(data?.upRateWhenRare) || 0
    totalCards.value = Number(data?.totalCards) || sets.value.reduce((sum, set) => sum + set.cardCount, 0)
    totalSets.value = Number(data?.totalSets) || sets.value.length
    error.value = ''
    loaded.value = true
  }

  /** 用抽卡响应中的 currentUp 刷新当期 UP（后端可能随抽卡轮换） */
  function syncCurrentUp(up) {
    if (up) currentUp.value = normalizeUp(up)
  }

  async function load(force = false) {
    if (loaded.value && !force) return
    if (inflight) return inflight
    loading.value = true
    error.value = ''
    inflight = (async () => {
      try {
        const data = await rewardGateway.getCatalog()
        applyCatalog(data)
      } catch (cause) {
        error.value = cause?.message || '卡池数据加载失败'
      } finally {
        loading.value = false
        inflight = null
      }
    })()
    return inflight
  }

  function findCard(setKey, cardKey) {
    const card = cardBySet.value.get(cardKey)
    if (card) return card
    const set = setKey ? setByKey.value.get(setKey) : null
    if (!set) return null
    if (set.rare?.cardKey === cardKey) return set.rare
    return set.commons.find(item => item.cardKey === cardKey) || null
  }

  function setOf(setKey) {
    return setByKey.value.get(setKey) || null
  }

  function cardsInSet(set) {
    if (!set) return []
    return [...(set.rare ? [set.rare] : []), ...(set.commons || [])]
  }

  return {
    sets,
    currentUp,
    drawCost,
    rareRate,
    upRateWhenRare,
    rareRatePercent,
    upRatePercent,
    totalCards,
    totalSets,
    loading,
    loaded,
    error,
    load,
    syncCurrentUp,
    findCard,
    setOf,
    cardsInSet,
    resolveCardImage
  }
})
