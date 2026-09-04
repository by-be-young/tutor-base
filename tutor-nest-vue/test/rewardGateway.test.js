import assert from 'node:assert/strict'
import test from 'node:test'

import { backendClient } from '../src/gateways/backendClient.js'
import { rewardGateway } from '../src/gateways/rewardGateway.js'

test('reward summary uses the authenticated backend endpoint', async () => {
  const requests = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return jsonResponse(200, { points: 400, collection: [] })
  }

  try {
    const summary = await rewardGateway.getSummary()
    assert.equal(summary.points, 400)
    assert.equal(requests[0].url.endsWith('/rewards'), true)
    assert.equal(requests[0].options.credentials, 'include')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('milestone claim sends only the milestone in the path and includes csrf', async () => {
  backendClient.clearCsrfToken()
  const requests = []
  const originalFetch = globalThis.fetch
  const responses = [
    jsonResponse(200, { token: 'reward-csrf' }),
    jsonResponse(201, {
      id: 9,
      milestonePoints: 1000,
      cardKey: 'flame-r',
      setKey: 'flame',
      rarity: 'rare',
      claimedAt: '2026-09-02T00:00:00Z'
    })
  ]
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }

  try {
    const reward = await rewardGateway.claimMilestone(1000)
    assert.equal(reward.cardKey, 'flame-r')
    assert.equal(requests[1].url.endsWith('/rewards/milestones/1000/claims'), true)
    assert.equal(requests[1].options.method, 'POST')
    assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'reward-csrf')
    assert.equal(requests[1].options.body, undefined)
  } finally {
    globalThis.fetch = originalFetch
    backendClient.clearCsrfToken()
  }
})

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}
