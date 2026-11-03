import { reactive } from 'vue'

/** 全局提示列表。 */
export const toasts = reactive([])

/**
 * 弹一条提示，3 秒后自动消失。
 * @param {string} message 文本
 * @param {'info'|'success'|'error'} type 类型
 */
export function toast(message, type = 'info') {
  const id = Date.now() + Math.random()
  toasts.push({ id, message, type })
  window.setTimeout(() => {
    const index = toasts.findIndex((item) => item.id === id)
    if (index >= 0) toasts.splice(index, 1)
  }, 3200)
}

/**
 * 统一的错误提示。
 * @param {Error} error 异常
 */
export function toastError(error) {
  toast(error?.message || '操作失败', 'error')
}
