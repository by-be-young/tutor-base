import { expect, test } from '@playwright/test'

const apiBaseUrl = process.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080/api/v1'
test.setTimeout(90_000)

async function api(page, path, { method = 'GET', body } = {}) {
  return page.evaluate(async ({ apiBaseUrl, path, method, body }) => {
    const headers = { Accept: 'application/json' }
    if (!['GET', 'HEAD'].includes(method)) {
      const csrfResponse = await fetch(`${apiBaseUrl}/csrf`, { credentials: 'include' })
      const csrf = await csrfResponse.json()
      headers['X-CSRF-TOKEN'] = csrf.token
    }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    const response = await fetch(`${apiBaseUrl}${path}`, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body)
    })
    const text = await response.text()
    return { status: response.status, body: text ? JSON.parse(text) : null }
  }, { apiBaseUrl, path, method, body })
}

async function login(page, username, password) {
  await page.goto('/#/')
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
}

test('administrator and learner complete the protected learning journey', async ({ page }) => {
  const administrator = process.env.E2E_ADMIN_USERNAME || 'e2e-admin'
  const administratorPassword = process.env.E2E_ADMIN_PASSWORD || 'E2e-admin-password-2026'
  const learner = `e2e-learner-${Date.now()}`
  const learnerPassword = 'E2e-learner-password-2026'

  await login(page, administrator, administratorPassword)
  await page.goto('/#/admin')
  await expect(page.getByText('管理员', { exact: true })).toBeVisible()

  page.once('dialog', dialog => dialog.accept(learner))
  await page.getByRole('button', { name: /新增/ }).click()
  await expect(page.getByRole('option', { name: learner })).toBeAttached()

  const learnersResponse = await api(page, '/admin/learners?limit=100')
  expect(learnersResponse.status).toBe(200)
  const learnerId = learnersResponse.body.items.find(item => item.username === learner).learnerId
  expect((await api(page, `/admin/learners/${learnerId}/content-grants`, {
    method: 'PUT', body: { articleIds: [23] }
  })).status).toBe(200)
  expect((await api(page, '/admin/articles/23/answer-keys', {
    method: 'PUT',
    body: { items: [{ questionId: '1', answerText: 'e2e-reference-answer', autoGrade: false }] }
  })).status).toBe(200)

  await page.getByRole('button', { name: /学生账户/ }).click()
  const learnerRow = page.getByRole('listitem').filter({ hasText: learner })
  await learnerRow.getByRole('button', { name: /设置\/重置密码/ }).click()
  await page.locator('#student-new-password').fill(learnerPassword)
  await page.locator('#student-confirm-password').fill(learnerPassword)
  await page.getByRole('button', { name: '确认设置' }).click()
  await expect(page.getByRole('status')).toContainText(learner)

  page.once('dialog', dialog => dialog.accept())
  await learnerRow.getByRole('button', { name: /进入账号/ }).click()
  await expect(page).toHaveURL(/#\/$/)
  await page.goto('/#/tasks')
  await expect(page.getByRole('heading', { name: /任务中心/ })).toBeVisible()
  await expect(page.getByText('当前积分', { exact: true }).first()).toBeVisible()

  await page.getByRole('button', { name: /去签到/ }).click()
  const checkInDialog = page.locator('.checkin-panel')
  await expect(checkInDialog.getByRole('heading', { name: /每日签到/ })).toBeVisible()
  await checkInDialog.getByRole('button', { name: /签到 \+100 积分/ }).click()
  await expect(checkInDialog.getByRole('button', { name: /今日已签到/ })).toBeDisabled()
  await expect(page.getByText('100', { exact: true }).first()).toBeVisible()

  const grants = await api(page, '/me/content-grants')
  expect(grants.status).toBe(200)
  expect(grants.body.articleIds).toEqual([23])
  expect((await api(page, '/articles/22/study-state')).status).toBe(403)

  const initialState = await api(page, '/articles/23/study-state')
  expect(initialState.status).toBe(200)
  expect(initialState.body.questions[0].answerText).toBeNull()
  const submission = await api(page, '/articles/23/submissions/1', {
    method: 'PUT', body: { answerText: 'learner-draft' }
  })
  expect(submission.status).toBe(200)
  expect(submission.body.reviewStatus).toBe('pending')
  expect((await api(page, '/articles/23/study-state')).body.questions[0].answerText).toBeNull()

  const manualWrong = await api(page, '/wrong-book/entries', {
    method: 'POST',
    body: { sourceArticleId: 23, sourceQuestionId: '1', myAnswer: 'learner-draft' }
  })
  expect(manualWrong.status).toBe(201)
  expect(manualWrong.body.correctAnswer).toBe('')
  const editedWrong = await api(page, `/wrong-book/entries/${manualWrong.body.id}`, {
    method: 'PATCH', body: { wrongReason: '端到端验证', tags: ['e2e'], mastered: true }
  })
  expect(editedWrong.status).toBe(200)
  expect(editedWrong.body.mastered).toBe(true)
  expect((await api(page, `/wrong-book/entries/${manualWrong.body.id}`, { method: 'DELETE' })).status).toBe(204)

  const rewards = await api(page, '/rewards')
  expect(rewards.status).toBe(200)
  expect(rewards.body.points).toBe(100)
  expect((await api(page, '/rewards/milestones/200/claims', { method: 'POST' })).status).toBe(409)

  expect((await api(page, '/session', { method: 'DELETE' })).status).toBe(204)
  expect((await api(page, '/sessions', {
    method: 'POST', body: { username: administrator, password: administratorPassword }
  })).status).toBe(200)
  const reviewed = await api(page, `/admin/articles/23/learners/${learnerId}/submissions/1/review`, {
    method: 'PUT', body: { result: 'wrong' }
  })
  expect(reviewed.status).toBe(200)
  expect(reviewed.body.reviewResult).toBe('wrong')

  expect((await api(page, `/admin/learners/${learnerId}/impersonate`, { method: 'POST' })).status).toBe(200)
  const automaticWrongBook = await api(page, '/wrong-book')
  expect(automaticWrongBook.status).toBe(200)
  expect(automaticWrongBook.body).toHaveLength(1)
  expect(automaticWrongBook.body[0].correctAnswer).toBe('e2e-reference-answer')
  expect(automaticWrongBook.body[0].manual).toBe(false)
})
