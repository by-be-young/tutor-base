<!-- src/views/admin/AdminLearnersView.vue -->
<template>
  <div class="admin-page">
    <section class="panel admin-topbar">
      <div class="admin-topbar__meta">
        <StudentSwitcher class="admin-switcher" @select="handleSelectStudent" />
        <span v-if="currentStudent" class="chip chip--cyan admin-current"
          :title="`${currentStudentName} · ${currentActivityLabel} · ${currentClaimLabel}`">
          <i class="fas fa-user-graduate" aria-hidden="true"></i>
          <span class="admin-current__text">{{ currentStudentName }} · {{ currentActivityLabel }} · {{ currentClaimLabel }}</span>
        </span>
        <span v-else class="chip admin-current">
          <i class="fas fa-user-graduate" aria-hidden="true"></i>
          <span class="admin-current__text">当前学员 · 未选择</span>
        </span>
        <button v-if="currentStudent && currentClaimState === 'unclaimed'" type="button"
          class="btn btn--soft btn--sm admin-claim-btn" :disabled="claimBusy" @click="claimCurrentStudent">
          <i aria-hidden="true" class="fas" :class="claimBusy ? 'fa-circle-notch fa-spin' : 'fa-hand-pointer'"></i>
          {{ claimBusy ? '认领中…' : '认领' }}
        </button>
        <button v-else-if="currentStudent && currentClaimState === 'mine'" type="button"
          class="btn btn--ghost btn--sm admin-claim-btn" :disabled="claimBusy" @click="releaseCurrentStudent">
          <i aria-hidden="true" class="fas fa-arrow-up-from-bracket"></i> 释放
        </button>
      </div>

      <div class="admin-topbar__actions">
        <span class="admin-save-state" :class="{ 'is-dirty': permissionDirty }">{{ saveStateText }}</span>
        <button type="button" class="btn btn--soft btn--sm" @click="addStudent">
          <i aria-hidden="true" class="fas fa-user-plus"></i> 新增
        </button>
        <button type="button" class="btn btn--primary btn--sm admin-save-btn"
          :disabled="!permissionDirty || savingPermissions" @click="savePermissions">
          <i aria-hidden="true" class="fas"
            :class="savingPermissions ? 'fa-circle-notch fa-spin' : permissionDirty ? 'fa-cloud-arrow-up' : 'fa-circle-check'"></i>
          {{ saveButtonLabel }}
        </button>
      </div>
    </section>

    <div class="seg admin-tabs" role="tablist" aria-label="学员管理分类">
      <button v-for="tab in tabs" :key="tab.id" type="button" role="tab" class="seg__btn"
        :class="{ 'is-active': activeTab === tab.id }" :aria-selected="activeTab === tab.id"
        :aria-controls="`admin-tab-${tab.id}`" @click="activeTab = tab.id">
        <i :class="tab.icon" aria-hidden="true"></i>
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <section :id="`admin-tab-${activeTab}`" class="admin-panel" role="tabpanel">
      <StudentAccountsPanel v-if="activeTab === 'students'" :students="students"
        :selected-student-id="selectedStudentId" @view-detail="openDetailDrawer" />
      <LearnerSubjectPanel v-else-if="activeTab === 'subjects'" :students="students"
        :selected-student-id="selectedStudentId" :blog-data="blogData" :all-subjects="allSubjects"
        :dirty="permissionDirty" :saving="savingPermissions" @permission-change="onPermissionChange"
        @save-permissions="savePermissions" />
      <PermissionPanel v-else :students="students" :selected-student-id="selectedStudentId"
        :blog-data="blogData" :all-subjects="allSubjects" :dirty="permissionDirty"
        :saving="savingPermissions" @permission-change="onPermissionChange" @save-permissions="savePermissions" />
    </section>

    <!-- 学员详情抽屉：v-if 控制挂载，关闭后不影响当前标签与已选学员 -->
    <LearnerDetailDrawer v-if="detailLearnerId !== null" :learner-id="detailLearnerId" :students="students"
      @close="closeDetailDrawer" @select="selectFromDetailDrawer" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useAdminWorkspace } from '@/composables/useAdminWorkspace'
import StudentSwitcher from '@/components/admin/StudentSwitcher.vue'
import StudentAccountsPanel from '@/components/admin/StudentAccountsPanel.vue'
import LearnerSubjectPanel from '@/components/admin/LearnerSubjectPanel.vue'
import PermissionPanel from '@/components/admin/PermissionPanel.vue'
import LearnerDetailDrawer from '@/components/admin/LearnerDetailDrawer.vue'

const {
  students,
  selectedStudentId,
  currentStudent,
  currentStudentName,
  currentClaimState,
  currentClaimLabel,
  currentActivityLabel,
  permissionDirty,
  savingPermissions,
  blogData,
  allSubjects,
  saveStateText,
  saveButtonLabel,
  claimBusy,
  handleSelectStudent,
  onPermissionChange,
  savePermissions,
  claimCurrentStudent,
  releaseCurrentStudent,
  addStudent,
  loadWorkspace
} = useAdminWorkspace()

const activeTab = ref('students')
const detailLearnerId = ref(null)

const tabs = [
  { id: 'students', label: '学员账户', icon: 'fas fa-users-gear' },
  { id: 'subjects', label: '学员科目', icon: 'fas fa-book-open' },
  { id: 'permission', label: '权限管理', icon: 'fas fa-shield-halved' }
]

function openDetailDrawer(student) {
  if (student === null || student === undefined) return
  detailLearnerId.value = student.id
}

function closeDetailDrawer() {
  detailLearnerId.value = null
}

function selectFromDetailDrawer(learnerId) {
  handleSelectStudent(learnerId)
  closeDetailDrawer()
}

onMounted(loadWorkspace)
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-topbar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border-radius: var(--r-lg);
}

.admin-topbar__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1 1 420px;
  min-width: 0;
  flex-wrap: wrap;
}

.admin-switcher {
  flex: 1 1 240px;
  min-width: 200px;
  max-width: 340px;
}

.admin-current {
  max-width: 340px;
  overflow: hidden;
  box-shadow: 2px 2px 0 var(--ink);
}

.admin-current__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-claim-btn {
  flex-shrink: 0;
}

.admin-topbar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}

.admin-save-state {
  padding: 3px 10px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-full);
  background: var(--paper-2);
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
  white-space: nowrap;
}

.admin-save-state.is-dirty {
  background: var(--butter);
}

.admin-save-btn {
  min-width: 118px;
}

/* 复用学员端 .seg 分段控件，仅补充横向滚动 */
.admin-tabs {
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: thin;
}

.admin-panel {
  min-width: 0;
  animation: fade-up 0.28s var(--ease-out);
}

@media (max-width: 768px) {
  .admin-page {
    gap: 12px;
  }

  .admin-topbar {
    padding: 12px;
    gap: 10px;
  }

  .admin-topbar__meta {
    flex-basis: 100%;
  }

  .admin-switcher {
    flex: 1 1 100%;
    max-width: none;
  }

  .admin-current {
    max-width: 100%;
  }

  .admin-topbar__actions {
    width: 100%;
    margin-left: 0;
    justify-content: flex-end;
  }

  .admin-save-state {
    margin-right: auto;
  }

  .admin-tabs__btn {
    height: 36px;
    padding: 0 13px;
    font-size: 0.8rem;
  }
}
</style>