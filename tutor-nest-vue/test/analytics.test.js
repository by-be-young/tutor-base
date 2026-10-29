import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildLinePath,
  formatCompactNumber,
  levelFromLifetime,
  maxValue,
  normalizeSeries
} from '../src/utils/analytics.js'
import { levelFromPoints } from '../src/utils/level.js'

test('normalizeSeries maps missing, invalid and negative values to zero', () => {
  const points = [
    { submissions: 3 },
    { submissions: '5' },
    {},
    { submissions: -2 },
    { submissions: 'bad' }
  ]
  assert.deepEqual(normalizeSeries(points, 'submissions'), [3, 5, 0, 0, 0])
  assert.deepEqual(normalizeSeries(points, 'reviews'), [0, 0, 0, 0, 0])
  assert.deepEqual(normalizeSeries([], 'submissions'), [])
  assert.deepEqual(normalizeSeries(null, 'submissions'), [])
  assert.deepEqual(normalizeSeries(points, ''), [])
})

test('maxValue reads the requested keys and falls back to every numeric field', () => {
  const points = [
    { date: '2026-10-01', submissions: 3, reviews: 7 },
    { date: '2026-10-02', submissions: 9, reviews: 2 }
  ]
  assert.equal(maxValue(points, 'submissions'), 9)
  assert.equal(maxValue(points, ['reviews']), 7)
  assert.equal(maxValue(points, ['submissions', 'reviews']), 9)
  assert.equal(maxValue(points), 9)
  assert.equal(maxValue([], ['submissions']), 0)
  assert.equal(maxValue(null, ['submissions']), 0)
  assert.equal(maxValue([{ submissions: 'bad' }], ['submissions']), 0)
  assert.equal(maxValue([{ submissions: -4 }], ['submissions']), 0)
})

test('buildLinePath returns an empty string for empty or invalid data', () => {
  assert.equal(buildLinePath([], 'submissions', 100, 100, 10), '')
  assert.equal(buildLinePath(null, 'submissions', 100, 100, 10), '')
  assert.equal(buildLinePath(undefined, 'submissions', 100, 100, 10), '')
})

test('buildLinePath draws an M/L polyline scaled to the shared maximum', () => {
  const points = [
    { date: '2026-10-01', submissions: 0 },
    { date: '2026-10-02', submissions: 5 },
    { date: '2026-10-03', submissions: 10 }
  ]
  assert.equal(buildLinePath(points, 'submissions', 100, 100, 10), 'M10 90 L50 50 L90 10')
})

test('buildLinePath keeps both series on the same vertical scale', () => {
  const points = [
    { date: '2026-10-01', submissions: 5, reviews: 10 },
    { date: '2026-10-02', submissions: 10, reviews: 0 }
  ]
  assert.equal(buildLinePath(points, 'submissions', 100, 100, 10), 'M10 50 L90 10')
  assert.equal(buildLinePath(points, 'reviews', 100, 100, 10), 'M10 10 L90 90')
})

test('buildLinePath supports an object padding and a single data point', () => {
  const points = [
    { submissions: 0 },
    { submissions: 10 }
  ]
  assert.equal(
    buildLinePath(points, 'submissions', 120, 100, { top: 10, right: 20, bottom: 10, left: 20 }),
    'M20 90 L100 10'
  )
  assert.equal(buildLinePath([{ submissions: 5 }], 'submissions', 100, 100, 10), 'M50 10')
})

test('buildLinePath draws an all-zero series on the baseline', () => {
  const points = [{ submissions: 0 }, { submissions: 0 }]
  assert.equal(buildLinePath(points, 'submissions', 100, 100, 10), 'M10 90 L90 90')
})

test('formatCompactNumber formats thousands, 万 and 亿 with one decimal', () => {
  assert.equal(formatCompactNumber(0), '0')
  assert.equal(formatCompactNumber(12), '12')
  assert.equal(formatCompactNumber(999), '999')
  assert.equal(formatCompactNumber(1000), '1k')
  assert.equal(formatCompactNumber(1500), '1.5k')
  assert.equal(formatCompactNumber(12000), '1.2万')
  assert.equal(formatCompactNumber(123456), '12.3万')
  assert.equal(formatCompactNumber(120000000), '1.2亿')
  assert.equal(formatCompactNumber(-1500), '-1.5k')
  assert.equal(formatCompactNumber('bad'), '0')
})

test('levelFromLifetime follows the 200-points-per-level rule', () => {
  assert.deepEqual(levelFromLifetime(0), { level: 1, title: '学海新芽', points: 0, toNext: 200 })
  assert.deepEqual(levelFromLifetime(120), { level: 1, title: '学海新芽', points: 120, toNext: 80 })
  assert.deepEqual(levelFromLifetime(300), { level: 2, title: '学海新芽', points: 300, toNext: 100 })
  assert.deepEqual(levelFromLifetime(800), { level: 5, title: '拾光学徒', points: 800, toNext: 200 })
  assert.deepEqual(levelFromLifetime(-20), { level: 1, title: '学海新芽', points: 0, toNext: 200 })

  const lifetime = 1250
  const expected = levelFromPoints(lifetime)
  assert.deepEqual(levelFromLifetime(lifetime), {
    level: expected.level,
    title: expected.title,
    points: expected.points,
    toNext: expected.toNext
  })
})
