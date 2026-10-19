// src/utils/level.js
// 成长等级：每 200 积分提升 1 级。等级只由服务端积分推导，不做本地积分写入。
export const POINTS_PER_LEVEL = 200

const LEVEL_TIERS = [
  { min: 1, max: 3, title: '学海新芽', motto: '从第一篇文章开始' },
  { min: 4, max: 6, title: '拾光学徒', motto: '把零散的知识串起来' },
  { min: 7, max: 9, title: '解题行者', motto: '在练习里找到节奏' },
  { min: 10, max: 13, title: '知识领航', motto: '开始给别人讲明白' },
  { min: 14, max: 17, title: '思维舵手', motto: '举一反三，触类旁通' },
  { min: 18, max: 22, title: '星轨学者', motto: '建立自己的知识星系' },
  { min: 23, max: Number.POSITIVE_INFINITY, title: '传奇学人', motto: '持续学习本身就是答案' }
]

export function levelFromPoints(rawPoints) {
  const points = Math.max(0, Number(rawPoints) || 0)
  const level = Math.floor(points / POINTS_PER_LEVEL) + 1
  const intoLevel = points % POINTS_PER_LEVEL
  const toNext = POINTS_PER_LEVEL - intoLevel
  const progress = Math.round((intoLevel / POINTS_PER_LEVEL) * 100)
  const tier = LEVEL_TIERS.find(item => level >= item.min && level <= item.max) || LEVEL_TIERS[LEVEL_TIERS.length - 1]
  return {
    points,
    level,
    title: tier.title,
    motto: tier.motto,
    nextLevel: level + 1,
    toNext,
    progress,
    ringDegrees: (intoLevel / POINTS_PER_LEVEL) * 360
  }
}

export function levelTitle(level) {
  const tier = LEVEL_TIERS.find(item => level >= item.min && level <= item.max)
  return tier ? tier.title : LEVEL_TIERS[LEVEL_TIERS.length - 1].title
}

export function levelTiers() {
  return LEVEL_TIERS
}
