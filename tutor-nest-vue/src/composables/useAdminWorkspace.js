// src/composables/useAdminWorkspace.js
// 管理端三个页面（工作台 / 学员 / 批阅）共用的工作区状态。
// 这里只组合 adminStore、authStore 与 blogStore，不复制学员认领、权限保存等业务规则。
import { computed, ref } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useArticleStore } from '@/stores/blogStore'
import { useAuthStore } from '@/stores/authStore'
import { claimState, formatRelativeActivity } from '@/utils/adminStudents'
import { activeSubjects, buildSubjectScope } from '@/utils/adminSubjects'
import { showToast } from '@/utils/toast'

// 课件目录 / 学员列表都依赖文章目录；多页面与 AdminLayout 同时挂载时只请求一次。
let articleDataPromise = null

/** 幂等加载文章目录：并发调用共享同一个 Promise。 */
export function ensureArticleData(blogStore) {
  if (!blogStore) return Promise.resolve()
  if (blogStore.blogData.length) return Promise.resolve(blogStore.blogData)
  if (!articleDataPromise) {
    articleDataPromise = blogStore.loadArticleData().finally(() => {
      articleDataPromise = null
    })
  }
  return articleDataPromise
}

export function useAdminWorkspace() {
  const adminStore = useAdminStore()
  const authStore = useAuthStore()
  const blogStore = useArticleStore()

  const loading = ref(false)
  const savingPermissions = ref(false)
  const claimBusy = ref(false)

  const students = computed(() => adminStore.students)
  const selectedStudentId = computed(() => adminStore.currentStudentId)
  const currentStudent = computed(() => adminStore.currentStudent)
  const currentPermissions = computed(() => adminStore.currentPermissions)
  const permissionDirty = computed(() => adminStore.permissionDirty)
  const blogData = computed(() => blogStore.blogData)

  const accountId = computed(() => authStore.currentUser?.accountId ?? null)
  const currentStudentName = computed(() => currentStudent.value?.username || '未选择')
  const currentClaimState = computed(() => claimState(currentStudent.value, accountId.value))
  const currentActivityLabel = computed(() => formatRelativeActivity(currentStudent.value?.lastActivityAt))
  const currentClaimLabel = computed(() => {
    if (!currentStudent.value) return '未选择'
    if (currentClaimState.value === 'mine') return '我的学员'
    if (currentClaimState.value === 'unclaimed') return '未认领'
    return `由 ${currentStudent.value.claimedByUsername || '其他管理员'} 认领`
  })

  const allSubjects = computed(() => {
    const subjects = new Set()
    blogData.value.forEach(blog => {
      if (blog.series) subjects.add(blog.series)
    })
    return Array.from(subjects).sort()
  })

  // 当前学员实际在学的学科数（有任意已授权文章），用于概览上下文提示。
  const activeSubjectCount = computed(() => activeSubjects(buildSubjectScope({
    subjects: allSubjects.value,
    articles: blogData.value,
    permissions: currentPermissions.value
  })).length)

  const saveButtonLabel = computed(() => {
    if (savingPermissions.value) return '保存中…'
    return permissionDirty.value ? '保存权限' : '已保存'
  })

  const saveStateText = computed(() => {
    if (savingPermissions.value) return '保存中'
    return permissionDirty.value ? '待保存' : '未改动'
  })

  function handleSelectStudent(studentId) {
    adminStore.selectStudent(studentId)
  }

  function onPermissionChange() {
    // 脏标记由 adminStore 统一维护，这里只用于保留 PermissionPanel 的事件契约。
  }

  async function savePermissions() {
    if (savingPermissions.value || !permissionDirty.value) return false
    savingPermissions.value = true
    try {
      const success = await adminStore.savePermissions()
      if (success) showToast('权限已保存', 'success')
      return success
    } catch (error) {
      showToast(error.message || '权限保存失败，请稍后重试', 'error')
      return false
    } finally {
      savingPermissions.value = false
    }
  }

  async function claimCurrentStudent() {
    if (claimBusy.value || !adminStore.currentStudentId) return false
    const username = currentStudentName.value
    claimBusy.value = true
    try {
      await adminStore.claimStudent(adminStore.currentStudentId)
      showToast(`已认领学员“${username}”`, 'success')
      return true
    } catch (error) {
      showToast(error.message || '认领学员失败，请稍后重试', 'error')
      return false
    } finally {
      claimBusy.value = false
    }
  }

  async function releaseCurrentStudent() {
    if (claimBusy.value || !adminStore.currentStudentId) return false
    const username = currentStudentName.value
    claimBusy.value = true
    try {
      await adminStore.releaseStudent(adminStore.currentStudentId)
      showToast(`已释放学员“${username}”的认领`, 'success')
      return true
    } catch (error) {
      showToast(error.message || '释放认领失败，请稍后重试', 'error')
      return false
    } finally {
      claimBusy.value = false
    }
  }

  async function addStudent() {
    // e2e 依赖 window.prompt 的 dialog 流程，请勿改为自定义弹窗。
    const username = prompt('请输入新学生的姓名（用户名）:')
    if (!username || username.trim() === '') return null

    try {
      return await adminStore.addStudent(username.trim())
    } catch (error) {
      showToast(error.message || '新增学生失败，请稍后重试', 'error')
      return null
    }
  }

  /** 加载文章目录 + 学员列表，并挑选默认学员；页面进入时调用。 */
  async function loadWorkspace() {
    loading.value = true
    try {
      await ensureArticleData(blogStore)
      // 管理端多页共享同一个 adminStore：已有列表时不再重载，避免覆盖未保存的权限改动。
      if (!adminStore.students.length) {
        try {
          await adminStore.loadStudents()
        } catch (error) {
          showToast(error.message || '加载学生列表失败，请刷新后重试', 'error')
          return
        }
      }
      // 刷新/首次进入：优先「我认领且最近有动态」的学员。
      if (!adminStore.currentStudentId) {
        adminStore.selectInitialStudent(accountId.value)
      }
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    students,
    selectedStudentId,
    currentStudent,
    currentPermissions,
    permissionDirty,
    blogData,
    allSubjects,
    activeSubjectCount,
    currentStudentName,
    currentClaimState,
    currentClaimLabel,
    currentActivityLabel,
    accountId,
    savingPermissions,
    claimBusy,
    saveButtonLabel,
    saveStateText,
    handleSelectStudent,
    onPermissionChange,
    savePermissions,
    claimCurrentStudent,
    releaseCurrentStudent,
    addStudent,
    loadWorkspace
  }
}
