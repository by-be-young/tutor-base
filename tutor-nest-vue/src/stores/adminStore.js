import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { BackendGatewayError } from '@/gateways/backendClient'
import { learnerGateway } from '@/gateways/learnerGateway'
import { activityTimestamp, claimState, isTestStudent, sortStudentsByRecentActivity } from '@/utils/adminStudents'

function toStudent(learner) {
  return {
    id: learner.learnerId,
    username: learner.username,
    permissions: Array.isArray(learner.contentGrantArticleIds)
      ? [...learner.contentGrantArticleIds]
      : [],
    claimedByAccountId: learner.claimedByAccountId ?? null,
    claimedByUsername: learner.claimedByUsername ?? null,
    claimedAt: learner.claimedAt ?? null,
    lastActivityAt: learner.lastActivityAt ?? null,
    // 后端新增入口固定创建测试学员；响应缺少字段时仍按普通学员处理，避免误标。
    isTest: Boolean(learner.isTest),
    isSpecialTest: Boolean(learner.isSpecialTest)
  }
}

function studentKey(studentId) {
  return studentId === null || studentId === undefined ? '' : String(studentId)
}

function learnerActionError(error, action) {
  const code = error?.code || ''
  const status = error?.status
  let message = `${action}失败，请稍后重试`

  if (code === 'learner_already_claimed') {
    message = '该学员已被其他管理员认领，请刷新后重试'
  } else if (code === 'administrator_learner_protected') {
    message = '管理员学员受保护，不能删除'
  } else if (status === 404) {
    message = '未找到该学员，请刷新后重试'
  } else if (status === 401) {
    message = '登录状态已失效，请重新登录后再试'
  } else if (status === 403) {
    message = '当前账户无权执行该操作'
  } else if (status === 409) {
    message = '学员状态已发生变化，请刷新后重试'
  } else if (error instanceof BackendGatewayError && error.message) {
    message = `${action}失败：${error.message}`
  }

  const wrapped = new Error(message)
  wrapped.code = code || 'learner_action_failed'
  wrapped.status = status
  return wrapped
}

export const useAdminStore = defineStore('admin', () => {
  const students = ref([])
  const currentStudentId = ref(null)
  // 以学员 id 记录尚未保存的权限改动；切换学员不会丢失脏标记。
  const dirtyStudentIds = ref([])

  const currentStudent = computed(() => {
    const key = studentKey(currentStudentId.value)
    if (!key) return null
    return students.value.find(student => studentKey(student.id) === key) || null
  })

  const currentPermissions = computed(() => currentStudent.value?.permissions || [])

  const hasAnyUnsavedChanges = computed(() => dirtyStudentIds.value.length > 0)

  const permissionDirty = computed(() => hasUnsavedChanges(currentStudentId.value))

  function hasUnsavedChanges(studentId) {
    const key = studentKey(studentId)
    if (!key) return false
    return dirtyStudentIds.value.some(id => studentKey(id) === key)
  }

  function markDirty(studentId) {
    if (!hasUnsavedChanges(studentId)) dirtyStudentIds.value.push(studentId)
  }

  function clearDirty(studentId) {
    const key = studentKey(studentId)
    dirtyStudentIds.value = dirtyStudentIds.value.filter(id => studentKey(id) !== key)
  }

  function replaceStudent(learner) {
    const incoming = toStudent(learner)
    const index = students.value.findIndex(item => studentKey(item.id) === studentKey(incoming.id))
    const existing = index === -1 ? null : students.value[index]
    // 认领/释放不改变内容授权：若该学员有尚未保存的勾选，保留本地改动和脏标记。
    const keepLocalPermissions = existing !== null && hasUnsavedChanges(existing.id)
    const student = keepLocalPermissions
      ? { ...incoming, permissions: [...existing.permissions] }
      : incoming
    const next = index === -1
      ? [...students.value, student]
      : students.value.map((item, itemIndex) => (itemIndex === index ? student : item))
    students.value = sortStudentsByRecentActivity(next)
    if (!keepLocalPermissions) clearDirty(student.id)
    return student
  }

  async function loadStudents() {
    const learners = await learnerGateway.listAllLearners()
    students.value = sortStudentsByRecentActivity(learners.map(toStudent))
  }

  function selectStudent(studentId) {
    const key = studentKey(studentId)
    if (!key) {
      currentStudentId.value = null
      return null
    }
    const student = students.value.find(item => studentKey(item.id) === key)
    currentStudentId.value = student ? student.id : null
    return currentStudentId.value
  }

  /**
   * 首次进入/刷新时的默认学员，优先普通（非测试）学员：
   * 我的普通学员按最近动态 → 我的普通学员 → 其他普通学员按最近动态 → 其他普通学员 →
   * 只有普通学员为空时才回退到测试学员。
   * currentAccountId 由调用方从 authStore 传入，store 不直接依赖 authStore。
   */
  function selectInitialStudent(currentAccountId = null) {
    if (students.value.length === 0) {
      currentStudentId.value = null
      return null
    }

    const regular = students.value.filter(student => !isTestStudent(student))
    const mine = regular.filter(student => claimState(student, currentAccountId) === 'mine')
    const mineWithActivity = mine.filter(student => activityTimestamp(student) !== null)
    const withActivity = regular.filter(student => activityTimestamp(student) !== null)
    const candidate = mineWithActivity[0] || mine[0] || withActivity[0] || regular[0] || students.value[0]

    currentStudentId.value = candidate.id
    return currentStudentId.value
  }

  async function addStudent(username) {
    const learner = await learnerGateway.createLearner(String(username).trim())
    const student = toStudent(learner)
    students.value = sortStudentsByRecentActivity([...students.value, student])
    currentStudentId.value = student.id
    return student
  }

  async function claimStudent(studentId) {
    try {
      const learner = await learnerGateway.claimLearner(studentId)
      replaceStudent(learner)
      currentStudentId.value = learner.learnerId
      return learner
    } catch (error) {
      throw learnerActionError(error, '认领学员')
    }
  }

  async function releaseStudent(studentId) {
    try {
      await learnerGateway.releaseLearnerClaim(studentId)
    } catch (error) {
      throw learnerActionError(error, '释放认领')
    }

    // DELETE /claim 返回 204，没有响应体：本地把认领归属清空，其余字段保持不变。
    const key = studentKey(studentId)
    const current = students.value.find(item => studentKey(item.id) === key)
    if (current) {
      const released = { ...current, claimedByAccountId: null, claimedByUsername: null, claimedAt: null }
      students.value = students.value.map(item => (studentKey(item.id) === key ? released : item))
      clearDirty(released.id)
      return released
    }
    return null
  }

  async function deleteStudent(studentId, currentAccountId = null) {
    try {
      await learnerGateway.deleteLearner(studentId)
    } catch (error) {
      throw learnerActionError(error, '删除学员')
    }

    const key = studentKey(studentId)
    const deletingCurrent = studentKey(currentStudentId.value) === key
    students.value = students.value.filter(student => studentKey(student.id) !== key)
    clearDirty(studentId)
    if (deletingCurrent) selectInitialStudent(currentAccountId)
    return true
  }

  function updatePermissions(blogId, checked) {
    const student = currentStudent.value
    if (!student) return

    const permissions = [...student.permissions]
    const numericId = Number(blogId)
    if (!Number.isFinite(numericId)) return

    if (checked) {
      if (!permissions.includes(numericId)) permissions.push(numericId)
    } else {
      const index = permissions.indexOf(numericId)
      if (index > -1) permissions.splice(index, 1)
    }

    student.permissions = permissions
    markDirty(student.id)
  }

  async function savePermissions() {
    const student = currentStudent.value
    if (!student) return false

    const saved = await learnerGateway.replaceContentGrants(student.id, student.permissions)
    // 后端 LearnerResponse 的字段是 contentGrantArticleIds；取不到时保留本地已保存的列表，避免误清空
    student.permissions = Array.isArray(saved.contentGrantArticleIds)
      ? [...saved.contentGrantArticleIds]
      : [...student.permissions]
    // 保存响应若带认领/动态字段，一并刷新，保持列表元数据最新。
    if (saved.claimedByAccountId !== undefined) student.claimedByAccountId = saved.claimedByAccountId
    if (saved.claimedByUsername !== undefined) student.claimedByUsername = saved.claimedByUsername
    if (saved.claimedAt !== undefined) student.claimedAt = saved.claimedAt
    if (saved.lastActivityAt !== undefined) student.lastActivityAt = saved.lastActivityAt
    clearDirty(student.id)
    return true
  }

  return {
    students,
    currentStudentId,
    dirtyStudentIds,
    currentStudent,
    currentPermissions,
    permissionDirty,
    hasAnyUnsavedChanges,
    hasUnsavedChanges,
    loadStudents,
    selectStudent,
    selectInitialStudent,
    addStudent,
    claimStudent,
    releaseStudent,
    deleteStudent,
    updatePermissions,
    savePermissions
  }
})
