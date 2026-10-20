import { backendClient } from './backendClient.js'

export const rewardGateway = {
  getSummary() {
    return backendClient.request('/rewards')
  },

  /** 卡池目录：卡组、卡片、当期 UP、抽卡价格与概率 */
  getCatalog() {
    return backendClient.request('/rewards/catalog')
  },

  draw(count = 1) {
    return backendClient.request('/rewards/draws', {
      method: 'POST',
      body: { count }
    })
  }
}
