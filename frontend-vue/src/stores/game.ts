import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { GameStateMessage, CountdownEvent, GamePhase } from '@/types'
import { useToastStore } from './toast'

/**
 * Стор игрового состояния.
 *
 * Мы храним последний GameStateMessage для рендеринга на Canvas и
 * управляем флагом обратного отсчёта перед матчем.
 */
export const useGameStore = defineStore('game', () => {
  const toast = useToastStore()

  // ── Состояние ────────────────────────────────────────────────────────

  /** Последний снимок мира — Canvas читает это значение в каждом кадре */
  const gameState = ref<GameStateMessage | null>(null)

  /** Текущее значение обратного отсчёта: 3, 2, 1, 'GO' или null */
  const countdown = ref<number | 'GO' | null>(null)

  const phase = ref<GamePhase>('LOBBY')

  /** Имя победителя — показываем экран победы */
  const winner = ref<string | null>(null)

  /** Мы считаем fps для отладочной информации */
  const fps = ref(0)
  let   _fpsFrames = 0
  let   _fpsTimer  = performance.now()

  // ── Вычисляемые ─────────────────────────────────────────────────────

  const isPlaying  = computed(() => phase.value === 'PLAYING')
  const isPaused   = computed(() => phase.value === 'PAUSED' || phase.value === 'PAUSE_REQUESTED')
  const isFinished = computed(() => phase.value === 'FINISHED')

  // ── Методы ──────────────────────────────────────────────────────────

  /**
   * Мы обновляем состояние мира при получении GameStateMessage (60 fps).
   * Метод максимально дешёвый — никаких вычислений, только присвоение.
   */
  function handleGameState(msg: GameStateMessage) {
    gameState.value = msg

    // Мы считаем fps в фоне без влияния на производительность рендеринга
    _fpsFrames++
    const now = performance.now()
    if (now - _fpsTimer >= 1000) {
      fps.value    = _fpsFrames
      _fpsFrames   = 0
      _fpsTimer    = now
    }
  }

  /**
   * Мы обрабатываем событие обратного отсчёта перед матчем.
   * CountdownOverlay.vue наблюдает за countdown и анимирует изменение.
   */
  function handleCountdown(event: CountdownEvent) {
    countdown.value = event.value
    // Мы сбрасываем отсчёт через 1 секунду — достаточно для одного числа
    setTimeout(() => {
      if (countdown.value === event.value) countdown.value = null
    }, 900)
  }

  /** Мы фиксируем завершение матча и показываем экран победителя */
  function handleGameOver(winnerUsername: string) {
    phase.value  = 'FINISHED'
    winner.value = winnerUsername
    toast.success(
      '🏆 Победитель определён!',
      `${winnerUsername} набрал 6 очков`,
    )
  }

  function reset() {
    gameState.value = null
    countdown.value = null
    winner.value    = null
    phase.value     = 'LOBBY'
    fps.value       = 0
  }

  return {
    gameState, countdown, phase, winner, fps,
    isPlaying, isPaused, isFinished,
    handleGameState, handleCountdown, handleGameOver, reset,
  }
})
