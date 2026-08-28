// src/stores/checkinStore.js
// 每日签到：签到记录、月度统计与积分发放均由后端承担（/api/v1/checkins），
// 本 store 只负责状态展示与弹窗开关；奖励规则（基础分、第 7/14 次额外奖励）
// 来自后端响应，前端不硬编码业务规则。
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { checkinGateway } from '@/gateways/checkinGateway'
import { useAuthStore } from '@/stores/authStore'
import { useTasksStore } from '@/stores/tasksStore'

export const useCheckinStore = defineStore('checkin', () => {
  const authStore = useAuthStore()
  const tasksStore = useTasksStore()

  const modalVisible = ref(false)
  const loading = ref(false)
  const loadError = ref(null)
  const year = ref(null) // 当前展示的月份
  const month = ref(null)
  const dates = ref([]) // 该月已签到日期（ISO date 字符串）
  const monthCount = ref(0)
  const todayCheckedIn = ref(false)
  const today = ref(null) // 服务端 Asia/Shanghai 今日日期
  const pointsPerCheckIn = ref(100)
  const bonusMilestones = ref([7, 14])

  /** 客户端本地年/月（日历展示基准；「今日」判定以服务端为准） */
  function currentLocalYearMonth() {
    const now = new Date()
    return { year: now.getFullYear(), month: now.getMonth() + 1 }
  }

  async function loadMonth(targetYear, targetMonth) {
    const { year: y, month: m } = targetYear
      ? { year: targetYear, month: targetMonth }
      : currentLocalYearMonth()
    if (!authStore.currentUser?.learnerId) return
    loading.value = true
    loadError.value = null
    try {
      const data = await checkinGateway.getMonthOverview(y, m)
      year.value = data.year
      month.value = data.month
      dates.value = data.dates || []
      monthCount.value = data.monthCount
      todayCheckedIn.value = data.todayCheckedIn
      today.value = data.today
      pointsPerCheckIn.value = data.pointsPerCheckIn
      bonusMilestones.value = data.bonusMilestones || [7, 14]
    } catch (e) {
      loadError.value = e.message || '签到数据加载失败'
    } finally {
      loading.value = false
    }
  }

  /** 执行签到并同步积分；返回后端结果（含 pointsAwarded/totalPoints） */
  async function checkInToday() {
    const result = await checkinGateway.checkInToday()
    dates.value = [...new Set([...dates.value, result.date])].sort()
    monthCount.value = result.monthCount
    todayCheckedIn.value = true
    tasksStore.syncPoints(result.totalPoints)
    return result
  }

  /** 从任务列表等处打开签到弹窗：总是回到本月并刷新状态 */
  function openModal() {
    if (!authStore.currentUser?.learnerId) return
    modalVisible.value = true
    loadMonth()
  }

  /** 仅在显式登录成功后调用（HomeView.handleLogin）；会话恢复不触发弹窗 */
  function openAfterLogin() {
    if (!authStore.currentUser?.learnerId) return
    modalVisible.value = true
    loadMonth()
  }

  function closeModal() {
    modalVisible.value = false
  }

  return {
    modalVisible,
    loading,
    loadError,
    year,
    month,
    dates,
    monthCount,
    todayCheckedIn,
    today,
    pointsPerCheckIn,
    bonusMilestones,
    loadMonth,
    checkInToday,
    openModal,
    openAfterLogin,
    closeModal
  }
})
