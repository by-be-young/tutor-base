import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { learnerGateway } from '@/gateways/learnerGateway'

function toStudent(learner) {
  return {
    id: learner.learnerId,
    username: learner.username,
    permissions: Array.isArray(learner.contentGrantArticleIds)
      ? [...learner.contentGrantArticleIds]
      : []
  }
}

export const useAdminStore = defineStore('admin', () => {
  const students = ref([])
  const currentStudentId = ref(null)
  const permissionDirty = ref(false)

  const currentStudent = computed(() => {
    if (!currentStudentId.value) return null
    return students.value.find(student => String(student.id) === String(currentStudentId.value)) || null
  })

  const currentPermissions = computed(() => currentStudent.value?.permissions || [])

  async function loadStudents() {
    const learners = await learnerGateway.listAllLearners()
    students.value = learners
      .map(toStudent)
      .sort((left, right) => left.username.localeCompare(right.username, 'zh-CN'))
  }

  function setCurrentStudent(studentId) {
    currentStudentId.value = studentId
    permissionDirty.value = false
  }

  async function addStudent(username) {
    const learner = await learnerGateway.createLearner(username.trim())
    const student = toStudent(learner)
    students.value.push(student)
    students.value.sort((left, right) => left.username.localeCompare(right.username, 'zh-CN'))
    currentStudentId.value = student.id
    permissionDirty.value = false
  }

  function updatePermissions(blogId, checked) {
    if (!currentStudent.value) return

    const permissions = [...currentStudent.value.permissions]
    const numericId = Number(blogId)
    if (!Number.isFinite(numericId)) return

    if (checked) {
      if (!permissions.includes(numericId)) permissions.push(numericId)
    } else {
      const index = permissions.indexOf(numericId)
      if (index > -1) permissions.splice(index, 1)
    }

    currentStudent.value.permissions = permissions
    permissionDirty.value = true
  }

  async function savePermissions() {
    if (!currentStudent.value) return false

    const saved = await learnerGateway.replaceContentGrants(
      currentStudent.value.id,
      currentStudent.value.permissions
    )
    currentStudent.value.permissions = Array.isArray(saved.articleIds) ? [...saved.articleIds] : []
    permissionDirty.value = false
    return true
  }

  return {
    students,
    currentStudentId,
    permissionDirty,
    currentStudent,
    currentPermissions,
    loadStudents,
    setCurrentStudent,
    addStudent,
    updatePermissions,
    savePermissions
  }
})
