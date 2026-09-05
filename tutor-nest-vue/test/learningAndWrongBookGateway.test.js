import assert from 'node:assert/strict'
import test from 'node:test'

import { backendClient } from '../src/gateways/backendClient.js'
import { learningGateway } from '../src/gateways/learningGateway.js'
import { wrongBookGateway } from '../src/gateways/wrongBookGateway.js'

test('submission path encodes text question key and sends only answer text', async () => {
  backendClient.clearCsrfToken()
  const requests = await capture([
    jsonResponse(200, { token: 'learning-csrf' }),
    jsonResponse(200, { reviewStatus: 'pending' })
  ], () => learningGateway.submit(10, 'section/题 1', 'my answer'))

  assert.equal(requests[1].url.endsWith('/articles/10/submissions/section%2F%E9%A2%98%201'), true)
  assert.deepEqual(JSON.parse(requests[1].options.body), { answerText: 'my answer' })
  assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'learning-csrf')
})

test('administrator review identifies article learner and question in the resource path', async () => {
  backendClient.clearCsrfToken()
  const requests = await capture([
    jsonResponse(200, { token: 'review-csrf' }),
    jsonResponse(200, { reviewResult: 'partial' })
  ], () => learningGateway.review(10, 5, 'q/2', 'partial'))

  assert.equal(requests[1].url.endsWith('/admin/articles/10/learners/5/submissions/q%2F2/review'), true)
  assert.deepEqual(JSON.parse(requests[1].options.body), { result: 'partial' })
})

test('wrong-book mutation never sends a learner id', async () => {
  backendClient.clearCsrfToken()
  const requests = await capture([
    jsonResponse(200, { token: 'wrong-book-csrf' }),
    jsonResponse(201, { id: 7 })
  ], () => wrongBookGateway.collect(10, 'q1', 'draft'))

  assert.equal(requests[1].url.endsWith('/wrong-book/entries'), true)
  assert.deepEqual(JSON.parse(requests[1].options.body), {
    sourceArticleId: 10,
    sourceQuestionId: 'q1',
    myAnswer: 'draft'
  })
})

async function capture(responses, action) {
  const requests = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return responses.shift()
  }
  try {
    await action()
    return requests
  } finally {
    globalThis.fetch = originalFetch
    backendClient.clearCsrfToken()
  }
}

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}
