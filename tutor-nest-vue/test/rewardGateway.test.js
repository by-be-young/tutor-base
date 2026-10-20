import assert from 'node:assert/strict'
import test from 'node:test'

import { backendClient } from '../src/gateways/backendClient.js'
import { rewardGateway } from '../src/gateways/rewardGateway.js'

test('reward summary uses the authenticated backend endpoint', async () => {
  const requests = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return jsonResponse(200, {
      points: 400,
      lifetimePoints: 1200,
      collection: [],
      stats: { totalDraws: 3, totalCards: 3, uniqueCards: 2, totalCatalogCards: 70, totalSets: 10 }
    })
  }

  try {
    const summary = await rewardGateway.getSummary()
    assert.equal(summary.points, 400)
    assert.equal(summary.lifetimePoints, 1200)
    assert.equal(summary.stats.totalDraws, 3)
    assert.equal(requests[0].url.endsWith('/rewards'), true)
    assert.equal(requests[0].options.credentials, 'include')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('card catalog is fetched from the rewards catalog endpoint', async () => {
  const requests = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return jsonResponse(200, {
      drawCost: 80,
      rareRate: 0.05,
      upRateWhenRare: 0.5,
      currentUp: {
        setKey: 'puppy',
        cardKey: 'puppy-r',
        name: '汪星人欢乐时光',
        setName: '狗狗图鉴',
        image: 'cards/puppy-r.png',
        rotatesAt: '2026-10-27T00:00:00Z'
      },
      sets: [{ key: 'puppy', name: '狗狗图鉴', rare: {}, commons: [] }],
      totalCards: 70,
      totalSets: 10
    })
  }

  try {
    const catalog = await rewardGateway.getCatalog()
    assert.equal(catalog.drawCost, 80)
    assert.equal(catalog.currentUp.cardKey, 'puppy-r')
    assert.equal(catalog.totalCards, 70)
    assert.equal(requests[0].url.endsWith('/rewards/catalog'), true)
    assert.equal(requests[0].options.credentials, 'include')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('draw posts the requested count and includes csrf', async () => {
  backendClient.clearCsrfToken()
  const requests = []
  const originalFetch = globalThis.fetch
  const responses = [
    jsonResponse(200, { token: 'reward-csrf' }),
    jsonResponse(200, {
      points: 120,
      lifetimePoints: 1400,
      currentUp: null,
      cards: [
        {
          id: 9,
          cardKey: 'puppy-r',
          setKey: 'puppy',
          rarity: 'rare',
          claimedAt: '2026-10-20T00:00:00Z',
          isUp: true
        }
      ],
      stats: { totalDraws: 4, totalCards: 4, uniqueCards: 3, totalCatalogCards: 70, totalSets: 10 }
    })
  ]
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }

  try {
    const result = await rewardGateway.draw(10)
    assert.equal(result.cards[0].cardKey, 'puppy-r')
    assert.equal(result.cards[0].isUp, true)
    assert.equal(requests[1].url.endsWith('/rewards/draws'), true)
    assert.equal(requests[1].options.method, 'POST')
    assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'reward-csrf')
    assert.equal(requests[1].options.body, JSON.stringify({ count: 10 }))
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
