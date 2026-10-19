import assert from 'node:assert/strict'
import test from 'node:test'

import { backendClient } from '../src/gateways/backendClient.js'
import { growthGateway } from '../src/gateways/growthGateway.js'

test('achievement summary and task board use learner-scoped endpoints', async () => {
  const requests = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    if (url.endsWith('/achievements')) return jsonResponse(200, { points: 1240, achievements: [] })
    return jsonResponse(200, { points: 1240, daily: [], weekly: [], limited: [] })
  }

  try {
    const achievements = await growthGateway.getAchievements()
    const board = await growthGateway.getTaskBoard()
    assert.equal(achievements.points, 1240)
    assert.equal(board.points, 1240)
    assert.equal(requests[0].url.endsWith('/achievements'), true)
    assert.equal(requests[1].url.endsWith('/tasks/board'), true)
    assert.equal(requests[0].options.credentials, 'include')
    assert.equal(requests[0].options.method, 'GET')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('growth claims send csrf and never send a learner id or reward amount', async () => {
  backendClient.clearCsrfToken()
  const requests = []
  const originalFetch = globalThis.fetch
  const responses = [
    jsonResponse(200, { token: 'growth-csrf' }),
    jsonResponse(200, { key: 'mastery', tierLevel: 1, awardedPoints: 30, totalPoints: 1270 }),
    jsonResponse(204, null),
    jsonResponse(200, { key: 'daily-submit', awardedPoints: 20, totalPoints: 1290 })
  ]
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }

  try {
    const achievement = await growthGateway.claimAchievement('mastery')
    await growthGateway.acceptTask('sprint-submit')
    const task = await growthGateway.claimTask('daily-submit')
    assert.equal(achievement.awardedPoints, 30)
    assert.equal(task.awardedPoints, 20)
    assert.equal(requests[1].url.endsWith('/achievements/mastery/claims'), true)
    assert.equal(requests[2].url.endsWith('/tasks/sprint-submit/accept'), true)
    assert.equal(requests[3].url.endsWith('/tasks/daily-submit/claims'), true)
    for (const index of [1, 2, 3]) {
      assert.equal(requests[index].options.method, 'POST')
      assert.equal(requests[index].options.headers['X-CSRF-TOKEN'], 'growth-csrf')
      assert.equal(requests[index].options.body, undefined)
    }
  } finally {
    globalThis.fetch = originalFetch
    backendClient.clearCsrfToken()
  }
})

function jsonResponse(status, body) {
  return new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}
