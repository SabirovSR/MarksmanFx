<script setup lang="ts">
import {
  ref, onMounted, onUnmounted, watch, shallowRef,
} from 'vue'
import { useGameStore }  from '@/stores/game'
import { useAuthStore }  from '@/stores/auth'
import {
  drawBackground,
  drawPlayer,
  drawNickname,
  drawTarget,
  drawArrow,
} from '@/utils/canvasDrawing'

// ─── Пропсы ──────────────────────────────────────────────────────────────

const props = defineProps<{
  /** ID текущей комнаты — нужен для подписки WebSocket */
  roomId: string
}>()

// ─── Сторы и ссылки ──────────────────────────────────────────────────────

const game = useGameStore()
const auth = useAuthStore()

/** Мы храним ссылку на DOM-элемент <canvas> */
const canvasRef = shallowRef<HTMLCanvasElement | null>(null)

/** Контекст рисования — создаётся один раз при монтировании */
let ctx: CanvasRenderingContext2D | null = null

/** ID текущего requestAnimationFrame для отмены при размонтировании */
let rafId = 0

/** Кэшированные логические размеры canvas (без умножения на DPR) */
let logicalW = 0
let logicalH = 0

// ─── Масштабирование для Retina / High-DPI ───────────────────────────────

/**
 * Мы автоматически масштабируем canvas под devicePixelRatio.
 *
 * Проблема: на Retina (MacBook, iPhone) один CSS-пиксель = 2+ физических пикселя.
 * Если не масштабировать — всё выглядит размыто (blur).
 *
 * Решение:
 *   1. Устанавливаем физический размер canvas: width = cssW * dpr
 *   2. Масштабируем контекст: ctx.scale(dpr, dpr)
 *   3. Рисуем в CSS-пикселях — браузер переводит в физические сам
 *
 * @param canvas  DOM-элемент canvas
 * @param cssW    желаемая ширина в CSS-пикселях
 * @param cssH    желаемая высота в CSS-пикселях
 */
function setupCanvas(canvas: HTMLCanvasElement, cssW: number, cssH: number): void {
  // Мы читаем devicePixelRatio текущего экрана (1 на обычных, 2+ на Retina)
  const dpr = window.devicePixelRatio || 1

  // Мы сохраняем логические размеры для использования в drawFrame
  logicalW = cssW
  logicalH = cssH

  // Мы устанавливаем физический размер canvas (в реальных пикселях дисплея)
  canvas.width  = Math.round(cssW * dpr)
  canvas.height = Math.round(cssH * dpr)

  // Мы задаём CSS-размер — это то, что видит пользователь
  canvas.style.width  = `${cssW}px`
  canvas.style.height = `${cssH}px`

  // Мы масштабируем контекст рисования: все дальнейшие draw-вызовы
  // работают в CSS-координатах, а браузер умножает на dpr при выводе
  ctx = canvas.getContext('2d')!
  ctx.scale(dpr, dpr)

  console.debug(`[Canvas] DPR=${dpr}, физич=${canvas.width}×${canvas.height}, логич=${cssW}×${cssH}`)
}

// ─── Основной цикл рисования ─────────────────────────────────────────────

/**
 * Мы рисуем один кадр игры.
 *
 * Порядок слоёв (от нижнего к верхнему):
 *   1. Фоновая сетка
 *   2. Мишени
 *   3. Тела и луки игроков
 *   4. Летящие стрелы
 *   5. Ники игроков (самый верхний слой — не перекрываются телами)
 */
function drawFrame(): void {
  if (!ctx) return

  const state = game.gameState

  // Мы очищаем весь canvas перед рисованием нового кадра
  ctx.clearRect(0, 0, logicalW, logicalH)

  // Мы всегда рисуем фон — даже если нет данных
  drawBackground(ctx, logicalW, logicalH)

  if (!state) return

  const localUsername = auth.username

  // Мы рисуем мишени первыми — они под игроками
  for (const target of state.targets) {
    drawTarget(ctx, target)
  }

  // Мы рисуем тела игроков
  for (const player of state.players) {
    const isLocal = player.username === localUsername
    drawPlayer(ctx, player, isLocal)
  }

  // Мы рисуем стрелы в полёте поверх игроков
  for (const arrow of state.arrows) {
    if (!arrow.active) continue
    const isLocal = state.players.find(p => p.playerId === arrow.ownerId)?.username === localUsername
    drawArrow(ctx, arrow, isLocal ?? false)
  }

  // Мы рисуем ники в самом конце — они должны быть поверх всего остального
  for (const player of state.players) {
    const isLocal = player.username === localUsername
    drawNickname(ctx, player, isLocal)
  }
}

// ─── Игровой цикл (requestAnimationFrame) ────────────────────────────────

/**
 * Мы запускаем рендер-цикл через requestAnimationFrame.
 * rAF синхронизируется с частотой экрана (60/120/144 fps) и не выполняется,
 * если вкладка не активна — экономит батарею и ресурсы процессора.
 */
function startLoop(): void {
  function loop() {
    drawFrame()
    rafId = requestAnimationFrame(loop)
  }
  rafId = requestAnimationFrame(loop)
}

function stopLoop(): void {
  cancelAnimationFrame(rafId)
}

// ─── Адаптация под изменение размера окна ────────────────────────────────

let resizeObserver: ResizeObserver | null = null

/**
 * Мы следим за изменением размера контейнера и перенастраиваем canvas.
 * ResizeObserver точнее, чем window.resize, для адаптивных раскладок.
 */
function watchResize(canvas: HTMLCanvasElement): void {
  resizeObserver = new ResizeObserver((entries) => {
    const entry  = entries[0]
    const { width, height } = entry.contentRect
    if (width > 0 && height > 0) {
      setupCanvas(canvas, width, height)
    }
  })
  resizeObserver.observe(canvas.parentElement ?? canvas)
}

// ─── Жизненный цикл компонента ────────────────────────────────────────────

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return

  // Мы получаем размер контейнера (canvas растянут через CSS)
  const rect = canvas.parentElement?.getBoundingClientRect()
  const cssW = rect?.width  || canvas.offsetWidth  || 800
  const cssH = rect?.height || canvas.offsetHeight || 600

  // Мы инициализируем canvas с правильным масштабом для экрана
  setupCanvas(canvas, cssW, cssH)

  // Мы запускаем наблюдение за изменением размера
  watchResize(canvas)

  // Мы запускаем основной рендер-цикл
  startLoop()
})

onUnmounted(() => {
  // Мы останавливаем цикл при уничтожении компонента — предотвращаем утечку памяти
  stopLoop()
  resizeObserver?.disconnect()
  ctx = null
})
</script>

<template>
  <!--
    Мы делаем canvas 100% ширины и высоты родителя.
    Фактические пиксельные размеры устанавливаются программно в setupCanvas().
    display: block убирает нежелательный отступ снизу (inline baseline gap).
  -->
  <canvas
    ref="canvasRef"
    class="block w-full h-full"
    aria-label="Игровое поле"
  />
</template>
