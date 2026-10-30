// src/gateways/analyticsGateway.js
// 管理端分析：概览指标与近 N 天提交/批阅趋势。
import { backendClient } from './backendClient'

function toCount(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? Math.round(number) : 0
}

function normalizeReviewTarget(target) {
  if (!target) return null
  const learnerId = Number(target.learnerId)
  const articleId = Number(target.articleId)
  if (!Number.isFinite(learnerId) || learnerId <= 0 || !Number.isFinite(articleId) || articleId <= 0) {
    return null
  }
  return {
    learnerId,
    articleId,
    questionId: typeof target.questionId === 'string' ? target.questionId : ''
  }
}

function normalizeOverview(response) {
  const courseware = response?.courseware || {}
  return {
    learners: toCount(response?.learners),
    claimedLearners: toCount(response?.claimedLearners),
    unclaimedLearners: toCount(response?.unclaimedLearners),
    activeLearners7d: toCount(response?.activeLearners7d),
    pendingReviews: toCount(response?.pendingReviews),
    submissions7d: toCount(response?.submissions7d),
    courseware: {
      visible: toCount(courseware.visible),
      unclaimed: toCount(courseware.unclaimed),
      subjects: toCount(courseware.subjects)
    },
    pendingReviewTarget: normalizeReviewTarget(response?.pendingReviewTarget)
  }
}

function normalizeActivityPoint(point) {
  return {
    date: typeof point?.date === 'string' ? point.date : '',
    submissions: toCount(point?.submissions),
    reviews: toCount(point?.reviews)
  }
}

/** GET /admin/analytics/overview */
async function getOverview() {
  return normalizeOverview(await backendClient.request('/admin/analytics/overview'))
}

/** GET /admin/analytics/activity?days=N，返回按日期升序补零后的点序列。 */
async function getActivity(days = 30) {
  const requestedDays = Number(days)
  const normalizedDays = Number.isFinite(requestedDays) && requestedDays > 0
    ? Math.min(Math.round(requestedDays), 365)
    : 30
  const parameters = new URLSearchParams({ days: String(normalizedDays) })
  const response = await backendClient.request(`/admin/analytics/activity?${parameters}`)
  return {
    days: toCount(response?.days) || normalizedDays,
    points: Array.isArray(response?.points) ? response.points.map(normalizeActivityPoint) : []
  }
}

export const analyticsGateway = {
  getOverview,
  getActivity
}
