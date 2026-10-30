import assert from 'node:assert/strict'
import test from 'node:test'

import { backendClient } from '../src/gateways/backendClient.js'
import { identityGateway } from '../src/gateways/identityGateway.js'
import { teacherApplicationGateway } from '../src/gateways/teacherApplicationGateway.js'

test('teacher application submits without creating a session', async () => {
  backendClient.clearCsrfToken()
  const requests = []
  const responses = [
    jsonResponse(200, { token: 'teacher-csrf' }),
    jsonResponse(201, {
      id: 7,
      username: 'Ms.Lin',
      status: 'PENDING',
      createdAt: '2026-10-31T02:00:00Z',
      reviewedAt: null,
      rejectionReason: null,
      approvedAccountId: null
    })
  ]
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }

  try {
    const application = await identityGateway.submitTeacherApplication('Ms.Lin', 'secret12')
    assert.equal(application.status, 'PENDING')
    assert.equal(requests[1].url.endsWith('/teacher-applications'), true)
    assert.equal(requests[1].options.method, 'POST')
    assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'teacher-csrf')
    assert.equal(requests[1].options.body, JSON.stringify({ username: 'Ms.Lin', password: 'secret12' }))
  } finally {
    globalThis.fetch = originalFetch
    backendClient.clearCsrfToken()
  }
})

test('super administrator review gateway lists, approves and rejects applications', async () => {
  backendClient.clearCsrfToken()
  const requests = []
  const responses = [
    jsonResponse(200, {
      items: [{ id: 7, username: 'Ms.Lin', status: 'PENDING' }],
      pendingCount: 1
    }),
    jsonResponse(200, { token: 'review-csrf' }),
    jsonResponse(200, { applicationId: 7, accountId: 91, username: 'Ms.Lin', role: 'ADMINISTRATOR' }),
    jsonResponse(200, { id: 8, username: 'Mr.Wu', status: 'REJECTED', rejectionReason: '请补充姓名' })
  ]
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }

  try {
    const collection = await teacherApplicationGateway.list('pending')
    assert.equal(collection.pendingCount, 1)
    assert.equal(requests[0].url.endsWith('/admin/teacher-applications?status=pending'), true)

    await teacherApplicationGateway.approve(7)
    assert.equal(requests[2].url.endsWith('/admin/teacher-applications/7/approve'), true)
    assert.equal(requests[2].options.method, 'POST')

    await teacherApplicationGateway.reject(8, '请补充姓名')
    assert.equal(requests[3].url.endsWith('/admin/teacher-applications/8/reject'), true)
    assert.equal(requests[3].options.body, JSON.stringify({ reason: '请补充姓名' }))
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
