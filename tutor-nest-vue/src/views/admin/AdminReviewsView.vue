<!-- src/views/admin/AdminReviewsView.vue -->
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
      </div>
    </section>

    <section class="admin-panel">
      <ReviewPanel :students="students" :selected-student-id="selectedStudentId" :blog-data="blogData"
        :all-subjects="allSubjects" />
    </section>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useAdminWorkspace } from '@/composables/useAdminWorkspace'
import StudentSwitcher from '@/components/admin/StudentSwitcher.vue'
import ReviewPanel from '@/components/admin/ReviewPanel.vue'

const {
  students,
  selectedStudentId,
  currentStudent,
  currentStudentName,
  currentClaimLabel,
  currentActivityLabel,
  blogData,
  allSubjects,
  handleSelectStudent,
  loadWorkspace
} = useAdminWorkspace()

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

.admin-panel {
  min-width: 0;
  animation: fade-up 0.28s var(--ease-out);
}

@media (max-width: 768px) {
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
}
</style>
