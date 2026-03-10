import { useToastStore } from '@/stores/toast'

/**
 * Мы создаём удобный composable для быстрого вызова уведомлений.
 * Вместо `const toast = useToastStore()` можно писать `const { notify } = useToast()`.
 * Это удобно в компонентах, которые не работают с остальной частью стора.
 */
export function useToast() {
  const store = useToastStore()

  return {
    success: store.success,
    error:   store.error,
    warning: store.warning,
    info:    store.info,
    dismiss: store.dismiss,
  }
}
