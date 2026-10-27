import assert from 'node:assert/strict'
import test from 'node:test'

import {
  activityTimestamp,
  claimState,
  formatRelativeActivity,
  groupStudentsForSwitcher,
  sortStudentsByRecentActivity,
  studentMatchesQuery
} from '../src/utils/adminStudents.js'

const NOW = Date.parse('2026-03-10T12:00:00.000Z')
const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

function student(username, lastActivityAt, extra = {}) {
  return { id: extra.id ?? username, username, permissions: [], lastActivityAt, ...extra }
}

test('activityTimestamp parses ISO timestamps and rejects empty or invalid values', () => {
  assert.equal(activityTimestamp({ lastActivityAt: '2026-03-10T08:00:00.000Z' }), Date.parse('2026-03-10T08:00:00.000Z'))
  assert.equal(activityTimestamp({ lastActivityAt: null }), null)
  assert.equal(activityTimestamp({ lastActivityAt: '' }), null)
  assert.equal(activityTimestamp({ lastActivityAt: 'not-a-date' }), null)
  assert.equal(activityTimestamp({}), null)
  assert.equal(activityTimestamp(null), null)
})

test('sortStudentsByRecentActivity orders newest first, nulls last and username ties ascending', () => {
  const students = [
    student('赵六', null),
    student('bob', '2026-03-09T10:00:00.000Z'),
    student('Alice', '2026-03-10T10:00:00.000Z'),
    student('chen', '2026-03-09T10:00:00.000Z'),
    student('无动态', null)
  ]

  const sorted = sortStudentsByRecentActivity(students)

  assert.deepEqual(sorted.map(item => item.username), ['Alice', 'bob', 'chen', '无动态', '赵六'])
  assert.deepEqual(students.map(item => item.username), ['赵六', 'bob', 'Alice', 'chen', '无动态'])
})

test('sortStudentsByRecentActivity returns a new array and tolerates an empty input', () => {
  const students = [student('a', null)]
  const sorted = sortStudentsByRecentActivity(students)
  assert.notEqual(sorted, students)
  assert.deepEqual(sortStudentsByRecentActivity([]), [])
  assert.deepEqual(sortStudentsByRecentActivity(undefined), [])
})

test('formatRelativeActivity covers empty, invalid and each relative boundary', () => {
  assert.equal(formatRelativeActivity(null, NOW), '暂无动态')
  assert.equal(formatRelativeActivity('', NOW), '暂无动态')
  assert.equal(formatRelativeActivity('bad-value', NOW), '暂无动态')
  assert.equal(formatRelativeActivity(new Date(NOW - 30 * 1000).toISOString(), NOW), '刚刚')
  assert.equal(formatRelativeActivity(new Date(NOW - 59 * 1000).toISOString(), NOW), '刚刚')
  assert.equal(formatRelativeActivity(new Date(NOW - MINUTE).toISOString(), NOW), '1 分钟前')
  assert.equal(formatRelativeActivity(new Date(NOW - 59 * MINUTE).toISOString(), NOW), '59 分钟前')
  assert.equal(formatRelativeActivity(new Date(NOW - HOUR).toISOString(), NOW), '1 小时前')
  assert.equal(formatRelativeActivity(new Date(NOW - 23 * HOUR).toISOString(), NOW), '23 小时前')
  assert.equal(formatRelativeActivity(new Date(NOW - DAY).toISOString(), NOW), '1 天前')
  assert.equal(formatRelativeActivity(new Date(NOW - 6 * DAY).toISOString(), NOW), '6 天前')
})

test('formatRelativeActivity falls back to YYYY-MM-DD after one week and clamps future values', () => {
  const old = new Date(2026, 0, 5, 12, 0, 0)
  assert.equal(formatRelativeActivity(old.toISOString(), old.getTime() + 8 * DAY), '2026-01-05')
  assert.equal(formatRelativeActivity(new Date(NOW + 5 * MINUTE).toISOString(), NOW), '刚刚')
})

test('studentMatchesQuery is case-insensitive, trims and matches empty queries', () => {
  const target = student('Young Learner', null)
  assert.equal(studentMatchesQuery(target, 'young'), true)
  assert.equal(studentMatchesQuery(target, 'LEARNER'), true)
  assert.equal(studentMatchesQuery(target, '  earn '), true)
  assert.equal(studentMatchesQuery(target, 'missing'), false)
  assert.equal(studentMatchesQuery(target, ''), true)
  assert.equal(studentMatchesQuery(target, '   '), true)
  assert.equal(studentMatchesQuery({ username: '张三' }, '张'), true)
  assert.equal(studentMatchesQuery(null, 'x'), false)
})

test('groupStudentsForSwitcher splits mine and others with numeric or string account ids', () => {
  const students = [
    student('mine-old', '2026-03-08T10:00:00.000Z', { claimedByAccountId: 7, claimedByUsername: 'admin' }),
    student('mine-new', '2026-03-10T10:00:00.000Z', { claimedByAccountId: '7', claimedByUsername: 'admin' }),
    student('unclaimed', '2026-03-10T11:00:00.000Z'),
    student('other-admin', '2026-03-09T10:00:00.000Z', { claimedByAccountId: 9, claimedByUsername: 'other' })
  ]

  const numeric = groupStudentsForSwitcher(students, 7)
  assert.deepEqual(numeric.mine.map(item => item.username), ['mine-new', 'mine-old'])
  assert.deepEqual(numeric.others.map(item => item.username), ['unclaimed', 'other-admin'])

  const asString = groupStudentsForSwitcher(students, '7')
  assert.deepEqual(asString.mine.map(item => item.username), ['mine-new', 'mine-old'])
})

test('groupStudentsForSwitcher treats a missing current account as no matches', () => {
  const students = [student('unclaimed', null), student('claimed', null, { claimedByAccountId: 3 })]
  const groups = groupStudentsForSwitcher(students, null)
  assert.deepEqual(groups.mine, [])
  assert.deepEqual(groups.others.map(item => item.username), ['claimed', 'unclaimed'])
})

test('claimState distinguishes mine, unclaimed and other administrators', () => {
  assert.equal(claimState({ claimedByAccountId: null }, 7), 'unclaimed')
  assert.equal(claimState({ claimedByAccountId: 7 }, 7), 'mine')
  assert.equal(claimState({ claimedByAccountId: '7' }, 7), 'mine')
  assert.equal(claimState({ claimedByAccountId: 8, claimedByUsername: 'other' }, 7), 'other')
  assert.equal(claimState(null, 7), 'unclaimed')
})
