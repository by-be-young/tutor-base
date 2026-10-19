// src/utils/toast.js
// 轻量全局 Toast：最多同时 3 条，自动堆叠、自动消失；样式见 theme.css。
const MAX_TOASTS = 3

function getStack() {
  let stack = document.querySelector('.toast-stack')
  if (!stack) {
    stack = document.createElement('div')
    stack.className = 'toast-stack'
    stack.setAttribute('role', 'status')
    stack.setAttribute('aria-live', 'polite')
    document.body.appendChild(stack)
  }
  return stack
}

export function showToast(message, type = 'info', duration = 3000) {
  if (typeof document === 'undefined') return () => {}
  const stack = getStack()

  while (stack.children.length >= MAX_TOASTS) {
    stack.firstElementChild?.remove()
  }

  const toast = document.createElement('div')
  toast.className = `custom-toast toast-${type}`
  toast.textContent = String(message ?? '')

  const timers = { hide: 0, remove: 0 }
  const dismiss = () => {
    window.clearTimeout(timers.hide)
    window.clearTimeout(timers.remove)
    toast.classList.remove('toast-visible')
    timers.remove = window.setTimeout(() => toast.remove(), 320)
  }

  toast.addEventListener('click', dismiss)
  stack.appendChild(toast)
  requestAnimationFrame(() => toast.classList.add('toast-visible'))

  timers.hide = window.setTimeout(dismiss, Math.max(1200, duration))
  return dismiss
}

export function clearToasts() {
  document.querySelectorAll('.custom-toast').forEach(node => node.remove())
}
