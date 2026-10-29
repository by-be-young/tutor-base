// src/utils/analytics.js
// 管理端分析纯函数：折线路径、序列归一化、最大值与紧凑数字格式。
// 不依赖 Vue / Pinia / 浏览器 API，便于 node:test 直接覆盖。
import { levelFromPoints } from './level.js'

function toNonNegativeNumber(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0) return 0
  return number
}

function numericKeysOf(points) {
  const keys = new Set()
  for (const point of points) {
    if (!point || typeof point !== 'object') continue
    for (const [key, value] of Object.entries(point)) {
      if (typeof value === 'number' && Number.isFinite(value)) keys.add(key)
    }
  }
  return Array.from(keys)
}

function roundCoordinate(value) {
  return Math.round(value * 100) / 100
}

function toPadding(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : 0
}

/**
 * padding 支持数字（四边相同）或 { top, right, bottom, left }。
 * 非法/负数按 0 处理，避免生成越界的坐标。
 */
function normalizePadding(padding) {
  if (padding && typeof padding === 'object') {
    return {
      top: toPadding(padding.top),
      right: toPadding(padding.right),
      bottom: toPadding(padding.bottom),
      left: toPadding(padding.left)
    }
  }
  const all = toPadding(padding)
  return { top: all, right: all, bottom: all, left: all }
}

/** 取出某个指标序列，缺失/非法/负数一律归零，保证图表不会出现 NaN。 */
export function normalizeSeries(points, key) {
  if (!Array.isArray(points) || !key) return []
  return points.map(point => toNonNegativeNumber(point?.[key]))
}

/** 多个序列共用纵轴：返回 keys 中的最大值；不传 keys 时统计所有数值字段。 */
export function maxValue(points, keys) {
  if (!Array.isArray(points) || points.length === 0) return 0
  const keyList = Array.isArray(keys) ? keys : keys === null || keys === undefined ? [] : [keys]
  const candidates = keyList.length > 0 ? keyList : numericKeysOf(points)
  let max = 0
  for (const point of points) {
    for (const key of candidates) {
      const value = toNonNegativeNumber(point?.[key])
      if (value > max) max = value
    }
  }
  return max
}

/**
 * 生成 SVG 折线 path 的 d 属性。
 * - 空数组返回 ''，单点生成 'M x y'，多点按点序连线。
 * - 纵轴按 points 中所有数值字段的公共最大值缩放，多序列共用同一比例尺。
 * - padding 支持数字或 { top, right, bottom, left }。
 */
export function buildLinePath(points, key, width, height, padding = 0) {
  const list = Array.isArray(points) ? points : []
  if (list.length === 0) return ''

  const boxWidth = toNonNegativeNumber(width)
  const boxHeight = toNonNegativeNumber(height)
  const pad = normalizePadding(padding)
  const innerWidth = Math.max(0, boxWidth - pad.left - pad.right)
  const innerHeight = Math.max(0, boxHeight - pad.top - pad.bottom)
  const values = normalizeSeries(list, key)
  const topValue = maxValue(list)
  const count = list.length
  const stepX = count > 1 ? innerWidth / (count - 1) : 0

  return values
    .map((value, index) => {
      const rawX = pad.left + stepX * index + (count === 1 ? innerWidth / 2 : 0)
      const rawY = topValue <= 0
        ? pad.top + innerHeight
        : pad.top + innerHeight - (value / topValue) * innerHeight
      return `${index === 0 ? 'M' : 'L'}${roundCoordinate(rawX)} ${roundCoordinate(rawY)}`
    })
    .join(' ')
}

function trimDecimal(value) {
  return String(Number(value.toFixed(1)))
}

/** 紧凑数字：999 → 999，1500 → 1.5k，12000 → 1.2万，1.2 亿 → 1.2亿。 */
export function formatCompactNumber(rawValue) {
  const value = Number(rawValue)
  if (!Number.isFinite(value)) return '0'

  const absolute = Math.abs(value)
  if (absolute < 1000) return String(Math.round(value))
  if (absolute < 10000) return `${trimDecimal(value / 1000)}k`
  if (absolute < 100000000) return `${trimDecimal(value / 10000)}万`
  return `${trimDecimal(value / 100000000)}亿`
}

/**
 * 按累计积分推导等级，口径与 utils/level.js 完全一致（每 200 分一级）。
 * 只暴露详情抽屉需要的字段，避免页面重复实现等级规则。
 */
export function levelFromLifetime(lifetimePoints) {
  const info = levelFromPoints(lifetimePoints)
  return {
    level: info.level,
    title: info.title,
    points: info.points,
    toNext: info.toNext
  }
}
