// src/utils/toast.js
// 轻量全局 Toast（与 TasksView / WrongQuestionsView 内置实现一致；
// 样式见各组件中的 :global(.custom-toast)，本工具只负责 DOM 行为）。
let toastTimer = null

export function showToast(message, type = 'info', duration = 3000) {
    const existing = document.querySelector('.custom-toast')
    if (existing) {
        existing.remove()
        if (toastTimer) {
            clearTimeout(toastTimer)
            toastTimer = null
        }
    }

    const toast = document.createElement('div')
    toast.className = `custom-toast toast-${type}`
    toast.textContent = message
    document.body.appendChild(toast)

    requestAnimationFrame(() => toast.classList.add('toast-visible'))

    toastTimer = setTimeout(() => {
        toast.classList.remove('toast-visible')
        setTimeout(() => {
            if (toast.parentNode) toast.parentNode.removeChild(toast)
            toastTimer = null
        }, 300)
    }, duration)
}
