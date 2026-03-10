<script setup lang="ts">
import { useToastStore } from '@/stores/toast'
import ToastItem from './ToastItem.vue'

// Мы получаем реактивный список тостов из стора
const toast = useToastStore()
</script>

<template>
  <!--
    Мы фиксируем контейнер в правом нижнем углу экрана с высоким z-index,
    чтобы уведомления всегда были поверх любого контента, включая Canvas.

    TransitionGroup анимирует добавление и удаление тостов из очереди:
      - enter: тост вылетает справа (анимация toast-in из tailwind.config.js)
      - leave: тост уходит вправо (анимация toast-out)
      - move: оставшиеся тосты плавно смещаются вверх при удалении среднего
  -->
  <div
    class="fixed bottom-6 right-6 z-50 flex flex-col gap-3 items-end pointer-events-none"
    aria-live="polite"
    aria-atomic="false"
  >
    <TransitionGroup
      name="toast"
      tag="div"
      class="flex flex-col gap-3 items-end"
    >
      <ToastItem
        v-for="item in toast.toasts"
        :key="item.id"
        :toast="item"
        class="pointer-events-auto"
        @dismiss="toast.dismiss(item.id)"
      />
    </TransitionGroup>
  </div>
</template>

<style scoped>
/* Мы подключаем анимации через CSS-классы TransitionGroup */
.toast-enter-active {
  animation: toastIn 0.4s cubic-bezier(0.21, 1.02, 0.73, 1) both;
}

.toast-leave-active {
  animation: toastOut 0.35s ease-in both;
  /* Мы убираем тост из потока немедленно, чтобы остальные сдвинулись */
  position: absolute;
}

/* Мы анимируем сдвиг оставшихся тостов при удалении одного из очереди */
.toast-move {
  transition: transform 0.3s ease;
}

@keyframes toastIn {
  from { opacity: 0; transform: translateX(110%) scale(0.9); }
  to   { opacity: 1; transform: translateX(0) scale(1); }
}

@keyframes toastOut {
  from { opacity: 1; transform: translateX(0) scale(1); }
  to   { opacity: 0; transform: translateX(110%) scale(0.9); }
}
</style>
