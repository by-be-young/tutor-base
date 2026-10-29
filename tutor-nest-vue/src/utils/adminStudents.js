// src/utils/adminStudents.js
// 管理员学员列表的纯函数工具：最近动态排序、相对时间、搜索、认领归属分组。
// 不依赖 Vue / Pinia / 浏览器 API，便于 node:test 直接覆盖。

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

function parseTimestamp(value) {
  if (value === null || value === undefined || value === '') return null
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  const parsed = Date.parse(String(value))
  return Number.isFinite(parsed) ? parsed : null
}

function usernameOf(student) {
  return String(student?.username ?? '')
}

function compareUsername(left, right) {
  return usernameOf(left).localeCompare(usernameOf(right), 'zh-CN')
}

/** lastActivityAt 解析为毫秒时间戳；无效或为空返回 null。 */
export function activityTimestamp(student) {
  return parseTimestamp(student?.lastActivityAt)
}

/** 按最近动态降序排序：null 最后，同值按 username（zh-CN）升序；返回新数组。 */
export function sortStudentsByRecentActivity(students) {
  return [...(students || [])].sort((left, right) => {
    const leftAt = activityTimestamp(left)
    const rightAt = activityTimestamp(right)
    if (leftAt === null && rightAt === null) return compareUsername(left, right)
    if (leftAt === null) return 1
    if (rightAt === null) return -1
    if (rightAt !== leftAt) return rightAt - leftAt
    return compareUsername(left, right)
  })
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatDate(timestamp) {
  const date = new Date(timestamp)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 中文相对时间：空/无效 → 暂无动态；<60s 刚刚；<60m N 分钟前；<24h N 小时前；<7d N 天前；否则 YYYY-MM-DD。 */
export function formatRelativeActivity(value, now = Date.now()) {
  const timestamp = parseTimestamp(value)
  if (timestamp === null) return '暂无动态'

  const current = Number.isFinite(now) ? now : Date.now()
  const delta = Math.max(0, current - timestamp)

  if (delta < MINUTE) return '刚刚'
  if (delta < HOUR) return `${Math.floor(delta / MINUTE)} 分钟前`
  if (delta < DAY) return `${Math.floor(delta / HOUR)} 小时前`
  if (delta < 7 * DAY) return `${Math.floor(delta / DAY)} 天前`
  return formatDate(timestamp)
}

/** 用户名大小写不敏感包含匹配；空查询匹配全部。 */
export function studentMatchesQuery(student, query) {
  const normalizedQuery = String(query ?? '').trim().toLowerCase()
  if (!normalizedQuery) return true
  return usernameOf(student).toLowerCase().includes(normalizedQuery)
}

function hasAccountId(value) {
  return value !== null && value !== undefined && value !== ''
}

function sameAccountId(left, right) {
  return hasAccountId(left) && hasAccountId(right) && Number(left) === Number(right)
}

/** 认领归属：'mine' 我认领、'unclaimed' 未认领、'other' 其他管理员认领。 */
export function claimState(student, currentAccountId) {
  const claimedByAccountId = student?.claimedByAccountId
  if (!hasAccountId(claimedByAccountId)) return 'unclaimed'
  return sameAccountId(claimedByAccountId, currentAccountId) ? 'mine' : 'other'
}

/** 测试学员：不进入活跃度统计、学员动态列表与默认选择。 */
export function isTestStudent(student) {
  return Boolean(student?.isTest)
}

/** 特殊测试学员：拥有全部课件权限，积分固定为 999999999 且不会减少。 */
export function isSpecialTestStudent(student) {
  return Boolean(student?.isSpecialTest)
}

/**
 * 切换器分组：
 * - mine：我认领的普通学员
 * - others：未认领 / 其他管理员认领的普通学员
 * - test：所有测试学员（含特殊测试）
 * 测试学员不从 mine/others 中重复出现；组内都按最近动态排序。
 */
export function groupStudentsForSwitcher(students, currentAccountId) {
  const mine = []
  const others = []
  const test = []

  for (const student of students || []) {
    if (isTestStudent(student)) {
      test.push(student)
      continue
    }
    if (claimState(student, currentAccountId) === 'mine') mine.push(student)
    else others.push(student)
  }

  return {
    mine: sortStudentsByRecentActivity(mine),
    others: sortStudentsByRecentActivity(others),
    test: sortStudentsByRecentActivity(test)
  }
}
