import { backendClient } from './backendClient.js'

export const checkinGateway = {
  /** 查询指定自然月（服务端 Asia/Shanghai）的签到概览 */
  getMonthOverview(year, month) {
    return backendClient.request(`/checkins?year=${year}&month=${month}`)
  },

  /** 执行当日签到（幂等：同日重复调用返回已签到状态） */
  checkInToday() {
    return backendClient.request('/checkins', { method: 'POST' })
  }
}
