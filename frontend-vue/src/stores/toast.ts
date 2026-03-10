import { ref } from 'vue'
import { defineStore } from 'pinia'

// ─── Типы ─────────────────────────────────────────────────────────────────

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface Toast {
  id:       string
  type:     ToastType
  title:    string
  message?: string
  duration: number
  /** Флаг исчезновения — когда true, запускается CSS анимация выхода */
  leaving:  boolean
}

// ─── Store ────────────────────────────────────────────────────────────────

/**
 * Мы управляем глобальной очередью Toast-уведомлений через Pinia.
 * Это позволяет вызвать toast.success() из ЛЮБОГО компонента или сервиса,
 * не передавая пропсы через всё дерево компонентов.
 */
export const useToastStore = defineStore('toast', () => {
  const toasts = ref<Toast[]>([])

  /**
   * Мы добавляем новое уведомление в очередь.
   * Через duration миллисекунд запускаем анимацию выхода, а затем удаляем.
   */
  function show(
    title:    string,
    type:     ToastType = 'info',
    message?: string,
    duration  = 4000,
  ): string {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`

    toasts.value.push({ id, type, title, message, duration, leaving: false })

    // Мы запускаем таймер автоудаления тоста
    setTimeout(() => dismiss(id), duration)

    return id
  }

  /** Мы показываем зелёное уведомление об успехе (логин, создание комнаты) */
  function success(title: string, message?: string) {
    return show(title, 'success', message)
  }

  /** Мы показываем красное уведомление об ошибке (неверный пароль, сеть) */
  function error(title: string, message?: string) {
    return show(title, 'error', message, 6000)
  }

  /** Мы показываем жёлтое предупреждение (комната заполнена, токен истекает) */
  function warning(title: string, message?: string) {
    return show(title, 'warning', message, 5000)
  }

  /** Мы показываем информационный тост (новый игрок подключился) */
  function info(title: string, message?: string) {
    return show(title, 'info', message)
  }

  /**
   * Мы запускаем анимацию выхода тоста, а после её завершения удаляем из списка.
   * Задержка 400ms = длительность CSS-анимации toast-out.
   */
  function dismiss(id: string) {
    const toast = toasts.value.find(t => t.id === id)
    if (!toast || toast.leaving) return

    // Мы помечаем тост как уходящий — CSS увидит изменение и запустит анимацию
    toast.leaving = true

    // Мы удаляем тост после завершения CSS-анимации
    setTimeout(() => {
      toasts.value = toasts.value.filter(t => t.id !== id)
    }, 400)
  }

  return { toasts, show, success, error, warning, info, dismiss }
})
