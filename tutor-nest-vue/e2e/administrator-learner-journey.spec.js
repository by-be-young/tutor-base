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
  // 首页从 cdnjs 加载 FontAwesome；CI 访问外网偶发慢/被限流会让 load 事件长时间不触发。
  // E2E 只依赖文本与 role，不需要图标字体，因此拦截该请求，避免外部网络影响用例稳定性。
  await page.route('https://cdnjs.cloudflare.com/**', route => route.abort())
  await page.goto('/#/', { waitUntil: 'domcontentloaded' })
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
  await page.goto('/#/admin', { waitUntil: 'domcontentloaded' })
  await expect(page.getByText('管理员', { exact: true })).toBeVisible()
  // 默认落地在「学员科目」，它是权限管理之上的科目设定入口。
  await expect(page.getByRole('heading', { name: '学员科目' })).toBeVisible()

  page.once('dialog', dialog => dialog.accept(learner))
  await page.getByRole('button', { name: /新增/ }).click()

  // 新建学员会被自动认领并直接切换为当前学员：断言切换器与浮层里都能看到新学员。
  const studentSwitcher = page.getByRole('button', { name: '切换学员' })
  await expect(studentSwitcher).toContainText(learner)
  await studentSwitcher.click()
  await expect(page.getByRole('listbox', { name: '学员列表' }).getByText(learner)).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(studentSwitcher).toHaveAttribute('aria-expanded', 'false')

  const learnersResponse = await api(page, '/admin/learners?limit=100')
  expect(learnersResponse.status).toBe(200)
  const createdLearner = learnersResponse.body.items.find(item => item.username === learner)
  expect(createdLearner).toBeTruthy()
  // 新建学员自动归创建者认领。
  expect(createdLearner.claimedByUsername).toBeTruthy()
  expect(createdLearner.claimedByAccountId).not.toBeNull()
  const learnerId = createdLearner.learnerId
  expect((await api(page, `/admin/learners/${learnerId}/content-grants`, {
    method: 'PUT', body: { articleIds: [23] }
  })).status).toBe(200)
  expect((await api(page, '/admin/articles/23/answer-keys', {
    method: 'PUT',
    body: { items: [{ questionId: '1', answerText: 'e2e-reference-answer', autoGrade: false }] }
  })).status).toBe(200)

  await page.getByRole('button', { name: /学生账户/ }).click()
  // 学生账户默认筛选「我的学员」；新建学员会被自动认领，因此应直接出现在列表里。
  await expect(page.getByRole('button', { name: /我的学员/ })).toHaveAttribute('aria-pressed', 'true')
  const learnerRow = page.getByRole('listitem').filter({ hasText: learner })
  await expect(learnerRow).toBeVisible()
  await learnerRow.getByRole('button', { name: /设置\/重置密码/ }).click()
  const passwordDialog = page.getByRole('dialog', { name: '设置学生密码' })
  await expect(passwordDialog).toBeVisible()
  await page.locator('#student-new-password').fill(learnerPassword)
  await page.locator('#student-confirm-password').fill(learnerPassword)
  await page.getByRole('button', { name: '确认设置' }).click()
  await expect(passwordDialog).toBeHidden()
  await expect(page.locator('.student-notice')).toContainText(learner)

  page.once('dialog', dialog => dialog.accept())
  await learnerRow.getByRole('button', { name: /进入账号/ }).click()
  await expect(page).toHaveURL(/#\/$/)
  await page.goto('/#/tasks', { waitUntil: 'domcontentloaded' })
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
  const catalog = await api(page, '/rewards/catalog')
  expect(catalog.status).toBe(200)
  expect(catalog.body.drawCost).toBeGreaterThan(0)
  const overdrawCount = Math.min(10, Math.floor(rewards.body.points / catalog.body.drawCost) + 1)
  const overdraw = await api(page, '/rewards/draws', { method: 'POST', body: { count: overdrawCount } })
  expect(overdraw.status).toBe(409)
  expect(overdraw.body.code).toBe('insufficient_points')
  expect((await api(page, '/rewards')).body.points).toBe(100)

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

  // 收尾：回到管理员会话，覆盖认领/释放/删除学员的管理界面最小路径。
  expect((await api(page, '/session', { method: 'DELETE' })).status).toBe(204)
  expect((await api(page, '/sessions', {
    method: 'POST', body: { username: administrator, password: administratorPassword }
  })).status).toBe(200)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('link', { name: '管理' }).click()
  await expect(page).toHaveURL(/#\/admin/)
  await expect(page.getByRole('button', { name: '切换学员' })).toBeVisible()

  await page.getByRole('button', { name: /学生账户/ }).click()
  // 「我的学员」筛选下看不到未认领的学员，先切到「全部」再操作认领/删除。
  await page.getByRole('button', { name: /^全部/ }).click()
  const managedRow = page.getByRole('listitem').filter({ hasText: learner })
  await expect(managedRow).toBeVisible()

  await managedRow.getByRole('button', { name: '释放' }).click()
  await expect(managedRow.getByRole('button', { name: '认领' })).toBeVisible()
  await managedRow.getByRole('button', { name: '认领' }).click()
  await expect(managedRow.getByRole('button', { name: '释放' })).toBeVisible()

  await managedRow.getByRole('button', { name: '删除学员' }).click()
  const deleteDialog = page.getByRole('dialog', { name: '删除学员' })
  await expect(deleteDialog).toContainText(learner)
  await deleteDialog.getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByRole('listitem').filter({ hasText: learner })).toHaveCount(0)
})
