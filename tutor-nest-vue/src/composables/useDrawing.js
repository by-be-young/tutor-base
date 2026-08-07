// src/composables/useDrawing.js
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'

export function useDrawing() {
    const isActive = ref(false)
    const isDrawing = ref(false)

    let canvas = null
    let ctx = null
    let lastX = 0
    let lastY = 0
    let clearTimer = null
    let fadeId = null
    let fadeStartTime = 0

    const CLEAR_DELAY = 3000
    const FADE_DURATION = 800

    // 事件处理函数的引用（用于清理）
    let pointerDownHandler = null
    let pointerMoveHandler = null
    let pointerUpHandler = null
    let pointerLeaveHandler = null
    let touchMoveHandler = null
    let wheelHandler = null
    let gestureStartHandler = null
    let resizeHandler = null

    /**
     * 初始化画布
     */
    function initCanvas() {
        if (canvas) return // 已经初始化

        // 检查浏览器支持
        if (!window.PointerEvent) {
            console.warn('当前浏览器不支持 Pointer Events，绘制功能不可用')
            return
        }

        // 创建画布
        canvas = document.createElement('canvas')
        canvas.style.position = 'fixed'
        canvas.style.top = '0'
        canvas.style.left = '0'
        canvas.style.width = '100%'
        canvas.style.height = '100%'
        canvas.style.pointerEvents = 'none'
        canvas.style.zIndex = '9999'
        canvas.style.opacity = '1'
        canvas.style.touchAction = 'none' // 阻止默认触摸行为

        document.body.appendChild(canvas)
        ctx = canvas.getContext('2d')

        // 调整画布尺寸
        resizeCanvas()
        resizeHandler = () => resizeCanvas()
        window.addEventListener('resize', resizeHandler)
    }

    /**
     * 调整画布尺寸
     */
    function resizeCanvas() {
        if (!canvas) return
        canvas.width = window.innerWidth
        canvas.height = window.innerHeight
    }

    /**
     * 清除所有线条
     */
    function clearCanvas() {
        if (!ctx || !canvas) return
        ctx.clearRect(0, 0, canvas.width, canvas.height)
        canvas.style.opacity = '1'
    }

    /**
     * 停止淡出动画
     */
    function stopFade() {
        if (fadeId) {
            cancelAnimationFrame(fadeId)
            fadeId = null
        }
        if (canvas) {
            canvas.style.opacity = '1'
        }
    }

    /**
     * 启动淡出动画
     */
    function triggerFade() {
        if (fadeId || !canvas) return

        fadeStartTime = performance.now()

        function fadeStep(timestamp) {
            if (!canvas) {
                fadeId = null
                return
            }

            const elapsed = timestamp - fadeStartTime
            const progress = Math.min(elapsed / FADE_DURATION, 1)
            canvas.style.opacity = String(1 - progress)

            if (progress < 1) {
                fadeId = requestAnimationFrame(fadeStep)
            } else {
                clearCanvas()
                if (canvas) {
                    canvas.style.opacity = '1'
                }
                fadeId = null
            }
        }

        fadeId = requestAnimationFrame(fadeStep)
    }

    /**
     * 重置清除计时器
     */
    function resetClearTimer() {
        stopFade()
        clearTimeout(clearTimer)
        clearTimer = setTimeout(triggerFade, CLEAR_DELAY)
    }

    /**
     * 检查是否允许绘制：仅允许触控笔
     */
    function allowDrawing(e) {
        return e.pointerType === 'pen'
    }

    /**
     * 阻止触控笔相关的默认滚动行为
     */
    function preventPenScroll(e) {
        if (e.pointerType === 'pen') {
            e.preventDefault()
            return false
        }
        return true
    }

    /**
     * 开始绘制
     */
    function startDraw(e) {
        if (!isActive.value || !allowDrawing(e)) return

        preventPenScroll(e)
        stopFade()
        clearTimeout(clearTimer)

        if (canvas) {
            canvas.style.opacity = '1'
        }

        isDrawing.value = true
        lastX = e.clientX
        lastY = e.clientY
        resetClearTimer()
    }

    /**
     * 绘制中
     */
    function draw(e) {
        if (!isDrawing.value || !isActive.value) return

        if (!allowDrawing(e)) {
            stopDraw()
            return
        }

        preventPenScroll(e)

        const x = e.clientX
        const y = e.clientY

        if (!ctx) return

        ctx.beginPath()
        ctx.moveTo(lastX, lastY)
        ctx.lineTo(x, y)
        ctx.strokeStyle = '#FF007F'
        ctx.lineWidth = 4
        ctx.lineCap = 'round'
        ctx.stroke()

        lastX = x
        lastY = y
        resetClearTimer()
    }

    /**
     * 停止绘制
     */
    function stopDraw() {
        isDrawing.value = false
    }

    /**
     * 绑定事件监听器
     */
    function bindEvents() {
        if (!canvas) return

        // 指针事件（用于绘制）
        pointerDownHandler = (e) => startDraw(e)
        pointerMoveHandler = (e) => draw(e)
        pointerUpHandler = () => stopDraw()
        pointerLeaveHandler = () => stopDraw()

        document.addEventListener('pointerdown', pointerDownHandler)
        document.addEventListener('pointermove', pointerMoveHandler)
        document.addEventListener('pointerup', pointerUpHandler)
        document.addEventListener('pointerleave', pointerLeaveHandler)

        // 阻止触控笔引起的滚动
        touchMoveHandler = (e) => {
            if (isDrawing.value && isActive.value) {
                e.preventDefault()
            }
        }
        document.addEventListener('touchmove', touchMoveHandler, {
            passive: false,
            capture: true
        })

        // 阻止鼠标滚轮滚动
        wheelHandler = (e) => {
            if (isDrawing.value && isActive.value) {
                e.preventDefault()
            }
        }
        document.addEventListener('wheel', wheelHandler, {
            passive: false,
            capture: true
        })

        // 阻止触控笔相关的手势
        gestureStartHandler = (e) => {
            if (isDrawing.value && isActive.value) {
                e.preventDefault()
            }
        }
        document.addEventListener('gesturestart', gestureStartHandler, {
            passive: false,
            capture: true
        })
    }

    /**
     * 解绑事件监听器
     */
    function unbindEvents() {
        if (pointerDownHandler) {
            document.removeEventListener('pointerdown', pointerDownHandler)
            pointerDownHandler = null
        }
        if (pointerMoveHandler) {
            document.removeEventListener('pointermove', pointerMoveHandler)
            pointerMoveHandler = null
        }
        if (pointerUpHandler) {
            document.removeEventListener('pointerup', pointerUpHandler)
            pointerUpHandler = null
        }
        if (pointerLeaveHandler) {
            document.removeEventListener('pointerleave', pointerLeaveHandler)
            pointerLeaveHandler = null
        }
        if (touchMoveHandler) {
            document.removeEventListener('touchmove', touchMoveHandler, { capture: true })
            touchMoveHandler = null
        }
        if (wheelHandler) {
            document.removeEventListener('wheel', wheelHandler, { capture: true })
            wheelHandler = null
        }
        if (gestureStartHandler) {
            document.removeEventListener('gesturestart', gestureStartHandler, { capture: true })
            gestureStartHandler = null
        }
    }

    /**
     * 销毁画布
     */
    function destroyCanvas() {
        stopFade()
        clearTimeout(clearTimer)
        unbindEvents()

        if (resizeHandler) {
            window.removeEventListener('resize', resizeHandler)
            resizeHandler = null
        }

        if (canvas && canvas.parentNode) {
            canvas.parentNode.removeChild(canvas)
            canvas = null
            ctx = null
        }

        isDrawing.value = false
        isActive.value = false
    }

    /**
     * 激活绘制功能
     */
    function activate() {
        if (isActive.value) return

        initCanvas()
        bindEvents()
        isActive.value = true
        console.log('绘制功能已激活（仅触控笔可用）')
    }

    /**
     * 停用绘制功能
     */
    function deactivate() {
        if (!isActive.value) return

        destroyCanvas()
        isActive.value = false
        console.log('绘制功能已停用')
    }

    // 组件卸载时自动清理
    onUnmounted(() => {
        deactivate()
    })

    return {
        isActive,
        isDrawing,
        activate,
        deactivate
    }
}

/**
 * 在详情页中使用绘制功能的 Composable
 * 自动根据路由判断是否激活
 */
export function useDrawingInDetail() {
    const route = useRoute()
    const { isActive, isDrawing, activate, deactivate } = useDrawing()

    // 监听路由变化，只在详情页激活
    watch(
        () => route.name,
        (newRouteName, oldRouteName) => {
            // 离开旧页面时停用
            if (oldRouteName === 'ArticleDetail') {
                deactivate()
            }

            // 进入详情页时激活
            if (newRouteName === 'ArticleDetail') {
                // 延迟激活，确保 DOM 已渲染
                setTimeout(() => activate(), 100)
            }
        },
        { immediate: true }
    )

    // 组件挂载时检查当前路由
    onMounted(() => {
        if (route.name === 'ArticleDetail') {
            setTimeout(() => activate(), 100)
        }
    })

    // 组件卸载时清理
    onUnmounted(() => {
        deactivate()
    })

    return {
        isActive,
        isDrawing
    }
}