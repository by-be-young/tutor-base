<!--
  NotFoundView.vue - 404 页面（Riso Campus 印刷风）
  ----------------------------------------------------------------------------
  视觉：朱红/钴蓝色块 + 站内插画白边照片 + 胶带贴纸 + 错位硬阴影。
  不涉及业务逻辑，仅保留原路由可达性（/* 兜底）。
-->
<template>
  <div class="page page--narrow notfound">
    <section class="notfound__stage" aria-labelledby="notfound-title">
      <!-- 视觉锚点：钴蓝色块海报 + 白边照片 + 胶带 -->
      <div class="notfound__poster">
        <span class="notfound__halftone" aria-hidden="true"></span>
        <span class="notfound__tape notfound__tape--top" aria-hidden="true"></span>

        <figure class="notfound__photo">
          <img v-if="!imageBroken" class="notfound__img" :src="artImage" alt="星际漫游插画：飞船在星云中航行"
            loading="eager" decoding="async" @error="imageBroken = true" />
          <div v-else class="notfound__img-fallback" aria-hidden="true">
            <i class="fas fa-map-signs"></i>
          </div>
        </figure>

        <span class="notfound__poster-label mono" aria-hidden="true">LOST / IN / SPACE</span>
        <span class="notfound__poster-stamp mono" aria-hidden="true">NEST ARCHIVE · 404</span>
      </div>

      <div class="notfound__body">
        <span class="eyebrow">NEST / 404</span>
        <p class="notfound__code" aria-hidden="true">404</p>
        <h1 id="notfound-title" class="page-title notfound__title">这条航道没有知识点</h1>
        <p class="page-sub notfound__text">
          抱歉，您访问的页面不存在。该页面可能已被移除、名称已更改或暂时不可用。
        </p>

        <div class="notfound__actions">
          <router-link to="/" class="btn btn--primary">
            <i class="fas fa-house" aria-hidden="true"></i> 返回首页
          </router-link>
          <router-link to="/wrong-training" class="btn btn--soft">
            <i class="fas fa-dumbbell" aria-hidden="true"></i> 错题训练
          </router-link>
          <router-link to="/tasks" class="btn btn--ghost">
            <i class="fas fa-list-check" aria-hidden="true"></i> 任务中心
          </router-link>
        </div>

        <p class="notfound__hint mono">
          <i class="fas fa-triangle-exclamation" aria-hidden="true"></i>
          ERR_ROUTE_NOT_FOUND · 请检查地址，或从导航重新进入
        </p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'

// 站内插画加载失败时回落到色块 + 图标，保证离线/路径异常时仍有视觉锚点
const imageBroken = ref(false)
const artImage = `${import.meta.env.BASE_URL}cards/cosmos-r.png`
</script>

<style scoped>
.notfound {
  display: flex;
  align-items: center;
  min-height: calc(100vh - var(--header-h) - 120px);
}

.notfound__stage {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  align-items: center;
  gap: 44px;
  width: 100%;
  padding: 24px 0 10px;
}

/* ---------- 钴蓝海报拼贴 ---------- */
.notfound__poster {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 340px;
  padding: 30px 24px;
  background: var(--cobalt);
  border: 1.5px solid var(--ink);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-float);
  transform: rotate(-2deg);
  overflow: hidden;
  transition: transform var(--dur-slow) var(--ease-out);
}

.notfound__poster:hover {
  transform: rotate(-1deg) translateY(-4px);
}

.notfound__halftone {
  position: absolute;
  right: -18px;
  bottom: -22px;
  width: 180px;
  height: 180px;
  background-image: radial-gradient(var(--paper-1) 2px, transparent 2.4px);
  background-size: 15px 15px;
  transform: rotate(8deg);
  opacity: 0.4;
  pointer-events: none;
}

.notfound__photo {
  position: relative;
  width: min(78%, 252px);
  padding: 9px;
  background: var(--paper-1);
  border: 1.5px solid var(--ink);
  box-shadow: var(--shadow-card);
  transform: rotate(3deg);
  transition: transform var(--dur-slow) var(--ease-out);
}

.notfound__poster:hover .notfound__photo {
  transform: rotate(1.5deg) translateY(-3px);
}

.notfound__img {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  border: 1px solid var(--line-strong);
}

.notfound__img-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 3 / 4;
  border: 1px solid var(--line-strong);
  background: var(--vermilion);
  color: var(--paper-1);
  font-size: 3rem;
}

.notfound__tape {
  position: absolute;
  top: 24px;
  left: 50%;
  width: 110px;
  height: 26px;
  margin-left: -55px;
  background: var(--butter);
  border: 1.5px solid var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
  transform: rotate(-6deg);
  pointer-events: none;
}

.notfound__poster-label {
  position: absolute;
  right: 16px;
  bottom: 18px;
  padding: 3px 9px;
  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: var(--paper-1);
  background: var(--vermilion);
  border: 1.5px solid var(--ink);
  box-shadow: 2px 2px 0 var(--ink);
  transform: rotate(-4deg);
}

.notfound__poster-stamp {
  position: absolute;
  left: 16px;
  bottom: 20px;
  font-size: 0.58rem;
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--paper-1);
  opacity: 0.85;
}

/* ---------- 文案与操作 ---------- */
.notfound__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
}

.notfound__code {
  font-family: var(--font-display);
  font-size: clamp(4.8rem, 14vw, 9.5rem);
  font-weight: 800;
  line-height: 0.84;
  letter-spacing: -0.05em;
  margin: 2px 0 6px;
  color: var(--ink);
  text-shadow: 6px 6px 0 var(--butter);
}

.notfound__title {
  font-size: clamp(1.5rem, 3vw, 2.2rem);
}

.notfound__text {
  max-width: 44ch;
}

.notfound__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}

.notfound__hint {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: var(--text-3);
  font-size: 0.72rem;
  letter-spacing: 0.02em;
}

.notfound__hint i {
  color: var(--vermilion);
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .notfound {
    min-height: auto;
    align-items: flex-start;
  }

  .notfound__stage {
    grid-template-columns: minmax(0, 1fr);
    gap: 30px;
    padding-top: 18px;
  }

  .notfound__poster {
    min-height: 280px;
    width: min(100%, 420px);
    margin: 0 auto;
  }

  .notfound__photo {
    width: min(58%, 200px);
  }
}

@media (max-width: 760px) {
  .notfound__stage {
    gap: 22px;
  }

  .notfound__poster {
    min-height: 230px;
    padding: 22px 16px;
  }

  .notfound__photo {
    width: min(56%, 168px);
    padding: 7px;
  }

  .notfound__tape {
    top: 16px;
    width: 86px;
    margin-left: -43px;
    height: 22px;
  }

  .notfound__code {
    text-shadow: 4px 4px 0 var(--butter);
  }

  .notfound__actions {
    width: 100%;
  }

  .notfound__actions .btn {
    flex: 1 1 auto;
  }
}
</style>
