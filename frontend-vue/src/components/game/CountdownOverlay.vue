<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useGameStore } from '@/stores/game'

const game = useGameStore()

/**
 * Мы используем отдельный ключ для управления анимацией.
 * Каждое изменение animKey пересоздаёт элемент в DOM,
 * что принудительно перезапускает CSS-анимацию countdown.
 */
const animKey = ref(0)

/** Мы вычисляем, является ли текущее значение финальным "GO" */
const isGo = computed(() => game.countdown === 'GO')

/** Мы форматируем текст для отображения */
const displayText = computed<string>(() => {
  if (game.countdown === null)  return ''
  if (game.countdown === 'GO') return 'ПОЕХАЛИ!'
  return String(game.countdown)
})

/**
 * Мы следим за изменением countdown и перезапускаем анимацию.
 * Каждое новое значение (3 → 2 → 1 → GO) получает уникальный ключ —
 * Vue пересоздаёт DOM-узел и CSS-анимация начинается заново.
 */
watch(() => game.countdown, (newVal) => {
  if (newVal !== null) {
    animKey.value++
  }
})
</script>

<template>
  <!--
    Мы показываем оверлей только когда countdown не null.
    Оверлей не блокирует интерфейс — pointer-events: none.
  -->
  <Transition name="overlay">
    <div
      v-if="game.countdown !== null"
      class="absolute inset-0 flex items-center justify-center pointer-events-none z-20"
    >
      <!--
        Мы добавляем полупрозрачный фон только для GO — обратный отсчёт
        не перекрывает игровое поле, GO должен быть более заметным.
      -->
      <div
        v-if="isGo"
        class="absolute inset-0 bg-black/40 backdrop-blur-sm"
      />

      <!--
        Мы используем :key="animKey" для принудительного пересоздания элемента.
        Это перезапускает CSS-анимацию countdown при каждом новом числе.
      -->
      <div
        :key="animKey"
        :class="[
          'countdown-text relative',
          isGo
            ? 'text-accent-cyan font-game font-bold tracking-[0.15em] text-glow-cyan'
            : 'font-game font-bold text-white',
        ]"
        :style="{
          fontSize: isGo ? 'clamp(4rem, 12vw, 9rem)' : 'clamp(6rem, 18vw, 14rem)',
        }"
      >
        {{ displayText }}
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/*
  Мы определяем CSS-анимацию обратного отсчёта с эффектом "взрыва":
    0%   — маленький + размытый + прозрачный (появляется из ниоткуда)
    25%  — нормального размера + чёткий (кульминация, максимальная видимость)
    55%  — чуть сжимается (как дыхание)
    100% — большой + снова размытый + прозрачный (улетает в экран)
*/
.countdown-text {
  animation: countdownPulse 0.9s ease-out both;
}

@keyframes countdownPulse {
  0%   {
    opacity: 0;
    transform: scale(0.2);
    filter: blur(14px) drop-shadow(0 0 0 transparent);
  }
  25%  {
    opacity: 1;
    transform: scale(1.12);
    filter: blur(0) drop-shadow(0 0 40px rgba(255, 255, 255, 0.4));
  }
  55%  {
    opacity: 1;
    transform: scale(1.0);
    filter: blur(0) drop-shadow(0 0 20px rgba(255, 255, 255, 0.2));
  }
  100% {
    opacity: 0;
    transform: scale(2.2);
    filter: blur(10px) drop-shadow(0 0 0 transparent);
  }
}

/* Мы даём "ПОЕХАЛИ!" специальный пульсирующий цвет */
@keyframes goPulse {
  0%, 100% { text-shadow: 0 0 20px rgba(6, 182, 212, 0.7); }
  50%       { text-shadow: 0 0 60px rgba(6, 182, 212, 1); }
}

/* Мы плавно убираем весь оверлей когда отсчёт завершён */
.overlay-enter-active,
.overlay-leave-active {
  transition: opacity 0.3s ease;
}
.overlay-enter-from,
.overlay-leave-to {
  opacity: 0;
}
</style>
