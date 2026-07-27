<!-- src/views/NotFoundView.vue -->
<template>
    <div class="not-found-container">
        <div class="not-found-content">
            <div class="not-found-icon">
                <i class="fas fa-map-signs"></i>
            </div>
            <h1 class="not-found-title">404</h1>
            <p class="not-found-message">抱歉，您访问的页面不存在</p>
            <p class="not-found-description">
                该页面可能已被移除、名称已更改或暂时不可用。
            </p>
            <router-link to="/" class="not-found-btn">
                <i class="fas fa-home"></i> 返回首页
            </router-link>
        </div>

        <!-- 装饰圆点 -->
        <div class="deco-dot dot1"></div>
        <div class="deco-dot dot2"></div>
    </div>
</template>

<script setup>
// 404 页面不需要额外逻辑
</script>

<style scoped>
.not-found-container {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: calc(100vh - 70px);
    position: relative;
    overflow: hidden;
}

.not-found-content {
    text-align: center;
    z-index: 2;
    animation: fadeInUp 0.6s ease;
}

@keyframes fadeInUp {
    from {
        opacity: 0;
        transform: translateY(30px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.not-found-icon {
    font-size: 5rem;
    color: var(--teal);
    margin-bottom: 20px;
    opacity: 0.7;
}

.not-found-title {
    font-size: 8rem;
    font-weight: 700;
    color: #2d4a3a;
    line-height: 1;
    margin-bottom: 10px;
    text-shadow: 0 4px 20px rgba(80, 130, 120, 0.1);
}

.not-found-message {
    font-size: 1.5rem;
    color: #2d4a3a;
    margin-bottom: 10px;
    font-weight: 500;
}

.not-found-description {
    font-size: 1rem;
    color: var(--gray);
    margin-bottom: 40px;
    max-width: 400px;
    margin-left: auto;
    margin-right: auto;
}

.not-found-btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 12px 32px;
    background: linear-gradient(135deg, rgba(255, 248, 214, 0.95), rgba(243, 227, 162, 0.92));
    color: #705d13;
    border-radius: 30px;
    font-size: 1.1rem;
    font-weight: 600;
    text-decoration: none;
    transition: all 0.3s ease;
    box-shadow: 0 4px 16px rgba(217, 186, 75, 0.2);
}

.not-found-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(217, 186, 75, 0.3);
}

/* 装饰圆点 */
.deco-dot {
    position: absolute;
    border-radius: 50%;
    opacity: 0.15;
    pointer-events: none;
    z-index: 0;
}

.dot1 {
    width: 300px;
    height: 300px;
    background: var(--teal-pale);
    top: -100px;
    right: -100px;
}

.dot2 {
    width: 200px;
    height: 200px;
    background: var(--green);
    bottom: -50px;
    left: -50px;
}

@media (max-width: 640px) {
    .not-found-title {
        font-size: 5rem;
    }

    .not-found-message {
        font-size: 1.2rem;
    }

    .not-found-icon {
        font-size: 3rem;
    }

    .dot1,
    .dot2 {
        display: none;
    }
}
</style>