<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { Toast, ToastType } from '@/stores/toast'

const props = defineProps<{ toast: Toast }>()
const emit  = defineEmits<{ dismiss: [] }>()

// Мы вычисляем цветовую схему на основе типа уведомления
const config = computed<{
  bar:   string
  icon:  string
  emoji: string
  title: string
}>(() => {
  const map: Record<ToastType, { bar: string; icon: string; emoji: string; title: string }> = {
    success: {
      bar:   'bg-accent-green',
      icon:  'border-accent-green/40 bg-accent-green/10',
      emoji: '✓',
      title: 'text-accent-green',
    },
    error: {
      bar:   'bg-accent-red',
      icon:  'border-accent-red/40 bg-accent-red/10',
      emoji: '✕',
      title: 'text-accent-red-light',
    },
    warning: {
      bar:   'bg-accent-gold',
      icon:  'border-accent-gold/40 bg-accent-gold/10',
      emoji: '⚠',
      title: 'text-accent-gold-light',
    },
    info: {
      bar:   'bg-accent-cyan',
      icon:  'border-accent-cyan/40 bg-accent-cyan/10',
      emoji: 'ℹ',
      title: 'text-accent-cyan',
    },
  }
  return map[props.toast.type]
})

// Мы анимируем прогресс-полоску оставшегося времени
const progress = ref(100)
onMounted(() => {
  const start    = performance.now()
  const duration = props.toast.duration

  function tick(now: number) {
    const elapsed = now - start
    progress.value = Math.max(0, 100 - (elapsed / duration) * 100)
    if (elapsed < duration && !props.toast.leaving) {
      requestAnimationFrame(tick)
    }
  }
  requestAnimationFrame(tick)
})
</script>

<template>
  <!--
    Мы создаём карточку уведомления с:
      - Цветной полоской слева (тип уведомления)
      - Иконкой-эмодзи в цветном кружке
      - Заголовком и опциональным текстом
      - Прогресс-баром внизу (сколько времени до автоудаления)
      - Кнопкой закрытия
  -->
  <div
    class="relative flex items-start gap-3 w-80 max-w-sm
           bg-dark-900/95 backdrop-blur-sm
           border border-dark-700 rounded-xl
           shadow-card overflow-hidden"
    role="alert"
  >
    <!-- Цветная вертикальная полоска — визуальный индикатор типа -->
    <div :class="['absolute left-0 top-0 bottom-0 w-1 rounded-l-xl', config.bar]" />

    <!-- Иконка типа уведомления -->
    <div
      :class="[
        'ml-4 mt-3.5 flex-shrink-0 w-8 h-8 rounded-lg border flex items-center justify-center',
        'font-bold text-sm select-none',
        config.icon,
      ]"
    >
      {{ config.emoji }}
    </div>

    <!-- Текст уведомления -->
    <div class="flex-1 py-3 pr-8 min-w-0">
      <p :class="['font-game font-semibold text-sm tracking-wide', config.title]">
        {{ toast.title }}
      </p>
      <p v-if="toast.message" class="text-slate-400 text-xs mt-0.5 leading-relaxed">
        {{ toast.message }}
      </p>
    </div>

    <!-- Кнопка закрытия -->
    <button
      class="absolute top-2.5 right-2.5 text-slate-500 hover:text-white
             transition-colors w-5 h-5 flex items-center justify-center
             rounded text-xs hover:bg-dark-700"
      @click="emit('dismiss')"
      aria-label="Закрыть уведомление"
    >
      ✕
    </button>

    <!-- Прогресс-полоска автоудаления — убывает за время duration -->
    <div class="absolute bottom-0 left-0 right-0 h-0.5 bg-dark-700">
      <div
        :class="['h-full transition-none rounded-full', config.bar]"
        :style="{ width: `${progress}%` }"
      />
    </div>
  </div>
</template>
