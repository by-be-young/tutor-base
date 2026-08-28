import assert from 'node:assert/strict'
import test from 'node:test'

import { backendClient } from '../src/gateways/backendClient.js'
import { identityGateway } from '../src/gateways/identityGateway.js'

test('403 csrf_invalid refreshes the token and retries one time', async () => {
  backendClient.clearCsrfToken()
  const requests = []
  const responses = [
    jsonResponse(200, { token: 'stale-token' }),
    jsonResponse(403, { code: 'csrf_invalid', detail: 'Fetch a fresh CSRF token.' }),
    jsonResponse(200, { token: 'fresh-token' }),
    new Response(null, { status: 204 })
  ]
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }

  try {
    const result = await backendClient.request('/admin/learners/5/password', {
      method: 'PUT',
      body: { password: 'Password-for-test-2026' }
    })

    assert.equal(result, null)
    assert.equal(requests.length, 4)
    assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'stale-token')
    assert.equal(requests[3].options.headers['X-CSRF-TOKEN'], 'fresh-token')
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

test('successful login discards the CSRF token bound to the rotated cookie', async () => {
  backendClient.clearCsrfToken()
  const requests = []
  const responses = [
    jsonResponse(200, { token: 'anonymous-token' }),
    jsonResponse(200, {
      accountId: 1,
      learnerId: 2,
      username: 'young',
      roles: ['ADMINISTRATOR']
    }),
    jsonResponse(200, { token: 'authenticated-token' }),
    new Response(null, { status: 204 })
  ]
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }

  try {
    await identityGateway.login('young', 'Administrator-password-2026')
    await identityGateway.setLearnerPassword(5, 'Password-for-test-2026')

    assert.deepEqual(requests.map(request => request.options.method), ['GET', 'POST', 'GET', 'PUT'])
    assert.equal(requests[3].options.headers['X-CSRF-TOKEN'], 'authenticated-token')
  } finally {
    globalThis.fetch = originalFetch
    backendClient.clearCsrfToken()
  }
})

test('successful registration discards the CSRF token bound to the rotated cookie', async () => {
  backendClient.clearCsrfToken()
  const requests = []
  const responses = [
    jsonResponse(200, { token: 'anonymous-token' }),
    jsonResponse(200, {
      accountId: 3,
      learnerId: 4,
      username: 'Bob',
      roles: ['LEARNER']
    }),
    jsonResponse(200, { token: 'fresh-token' }),
    new Response(null, { status: 204 })
  ]
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }

  try {
    await identityGateway.register('Bob', 'Learner-password-2026')
    await identityGateway.setLearnerPassword(4, 'Password-for-test-2026')

    assert.deepEqual(requests.map(request => request.options.method), ['GET', 'POST', 'GET', 'PUT'])
    assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'anonymous-token')
    assert.equal(requests[3].options.headers['X-CSRF-TOKEN'], 'fresh-token')
  } finally {
    globalThis.fetch = originalFetch
    backendClient.clearCsrfToken()
  }
})

test('password change keeps the CSRF token because the session is not rotated', async () => {
  backendClient.clearCsrfToken()
  const requests = []
  const responses = [
    jsonResponse(200, { token: 'anonymous-token' }),
    jsonResponse(200, {
      accountId: 1,
      learnerId: 2,
      username: 'young',
      roles: ['ADMINISTRATOR']
    }),
    jsonResponse(200, { token: 'authenticated-token' }),
    new Response(null, { status: 204 }),
    new Response(null, { status: 204 })
  ]
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }

  try {
    await identityGateway.login('young', 'Administrator-password-2026')
    await identityGateway.changePassword('Old-password-2026', 'New-password-2026')
    await identityGateway.setLearnerPassword(2, 'Password-for-test-2026')

    assert.deepEqual(requests.map(request => request.options.method), ['GET', 'POST', 'GET', 'PUT', 'PUT'])
    assert.equal(requests[3].options.headers['X-CSRF-TOKEN'], 'authenticated-token')
    assert.equal(requests[4].options.headers['X-CSRF-TOKEN'], 'authenticated-token')
  } finally {
    globalThis.fetch = originalFetch
    backendClient.clearCsrfToken()
  }
})
