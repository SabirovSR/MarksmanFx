import type { PlayerStateDto, ArrowDto, TargetDto } from '@/types'

// ═══════════════════════════════════════════════════════════════════════════
// Утилиты рисования для игрового Canvas.
//
// Мы намеренно делаем каждую функцию чистой (pure): только ctx + данные,
// никаких ссылок на Vue-компоненты или сторы. Это позволяет легко тестировать
// и переиспользовать в разных контекстах (GameCanvas, миниатюра в лобби и т.д.)
// ═══════════════════════════════════════════════════════════════════════════

// ─── Цветовая палитра для игровых объектов ────────────────────────────────

const PALETTE = {
  player:    '#06b6d4',  // Циановый — цвет локального игрока
  opponent:  '#ef4444',  // Красный — цвет соперников
  bow:       '#a78bfa',  // Светло-фиолетовый — лук
  arrow:     '#fbbf24',  // Золотой — стрела в полёте
  arrowTip:  '#f59e0b',  // Насыщенный золотой — наконечник
  target:    ['#ef4444', '#f59e0b', '#22d3ee', '#fff'],  // Кольца мишени
  shadow:    'rgba(0, 0, 0, 0.5)',
  nickname:  '#e2e8f0',
  nicknameLocal: '#22d3ee',
  scoreText: '#f59e0b',
} as const

// ─── Игрок (лучник) ───────────────────────────────────────────────────────

/**
 * Мы рисуем лучника на основе геометрических примитивов (без спрайтов).
 * Это гарантирует чёткость на любом разрешении экрана.
 *
 * @param ctx         контекст Canvas (уже масштабированный под DPR)
 * @param player      данные игрока из последнего GameStateMessage
 * @param isLocal     true = это наш игрок (другой цвет + маркер)
 */
export function drawPlayer(
  ctx:     CanvasRenderingContext2D,
  player:  PlayerStateDto,
  isLocal: boolean,
): void {
  const { x, y, aimAngle, crouched } = player
  const color = isLocal ? PALETTE.player : PALETTE.opponent

  // Мы вычисляем высоту тела в зависимости от приседания
  const bodyH = crouched ? 24 : 40
  const bodyW = 18

  ctx.save()
  ctx.translate(x, y)

  // Мы рисуем тень под игроком для эффекта объёма
  ctx.beginPath()
  ctx.ellipse(0, 2, bodyW * 0.6, 4, 0, 0, Math.PI * 2)
  ctx.fillStyle = PALETTE.shadow
  ctx.fill()

  // ── Тело ─────────────────────────────────────────────────────────

  // Мы рисуем торс с закруглёнными углами
  const bodyY = crouched ? -bodyH : -bodyH
  ctx.beginPath()
  ctx.roundRect(-bodyW / 2, bodyY, bodyW, bodyH, 5)
  ctx.fillStyle = color + 'cc' // Полупрозрачное тело
  ctx.fill()

  // Мы добавляем обводку тела для чёткости на тёмном фоне
  ctx.strokeStyle = color
  ctx.lineWidth   = 1.5
  ctx.stroke()

  // ── Голова ───────────────────────────────────────────────────────

  const headY = bodyY - 14
  ctx.beginPath()
  ctx.arc(0, headY, 8, 0, Math.PI * 2)
  ctx.fillStyle = color
  ctx.fill()

  // Мы рисуем «визор» (маску шлема) — тёмная горизонтальная полоса
  ctx.beginPath()
  ctx.roundRect(-5, headY - 3, 10, 5, 2)
  ctx.fillStyle = 'rgba(0,0,0,0.7)'
  ctx.fill()

  // ── Лук с прицелом ───────────────────────────────────────────────

  // Мы поворачиваем контекст в направлении прицела
  ctx.rotate((aimAngle * Math.PI) / 180)

  // Мы рисуем рукоятку лука
  const bowX    = bodyW / 2 + 4
  const bowSpan = crouched ? 16 : 22

  ctx.beginPath()
  ctx.arc(bowX, 0, bowSpan, -Math.PI * 0.6, Math.PI * 0.6)
  ctx.strokeStyle = PALETTE.bow
  ctx.lineWidth   = 3
  ctx.stroke()

  // Мы рисуем тетиву
  ctx.beginPath()
  ctx.moveTo(bowX - 2, -bowSpan * Math.sin(Math.PI * 0.6))
  ctx.lineTo(bowX + 6, 0)
  ctx.lineTo(bowX - 2, bowSpan * Math.sin(Math.PI * 0.6))
  ctx.strokeStyle = '#c4b5fd'
  ctx.lineWidth   = 1.5
  ctx.stroke()

  // Мы рисуем нацеленную стрелу на луке
  ctx.beginPath()
  ctx.moveTo(bowX + 6, 0)
  ctx.lineTo(bowX + 30, 0)
  ctx.strokeStyle = PALETTE.arrow
  ctx.lineWidth   = 2
  ctx.stroke()

  // Мы рисуем наконечник нацеленной стрелы
  _drawArrowTip(ctx, bowX + 30, 0, 0)

  ctx.restore()

  // ── Очки игрока ──────────────────────────────────────────────────

  // Мы рисуем счёт над головой игрока
  const scoreY = y - bodyH - 28
  ctx.font         = 'bold 13px "JetBrains Mono", monospace'
  ctx.textAlign    = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle    = PALETTE.scoreText
  ctx.fillText(`${player.score}`, x, scoreY)
}

// ─── Ник игрока ───────────────────────────────────────────────────────────

/**
 * Мы рисуем никнейм игрока над его головой.
 * Разделяем от drawPlayer, чтобы рисовать ники в самом конце (поверх всего),
 * иначе тела могут перекрыть текст других игроков.
 *
 * @param isLocal  локальный игрок выделяется циановым цветом и жирным шрифтом
 */
export function drawNickname(
  ctx:     CanvasRenderingContext2D,
  player:  PlayerStateDto,
  isLocal: boolean,
): void {
  const { x, y, crouched } = player
  const bodyH  = crouched ? 24 : 40
  const nickY  = y - bodyH - 42
  const color  = isLocal ? PALETTE.nicknameLocal : PALETTE.nickname

  // Мы рисуем полупрозрачную подложку под текстом для читаемости на любом фоне
  const text    = player.username
  const font    = isLocal ? 'bold 12px Rajdhani, sans-serif' : '12px Rajdhani, sans-serif'
  ctx.font      = font
  ctx.textAlign = 'center'
  const tw = ctx.measureText(text).width

  ctx.beginPath()
  ctx.roundRect(x - tw / 2 - 6, nickY - 9, tw + 12, 18, 4)
  ctx.fillStyle = 'rgba(0, 0, 0, 0.55)'
  ctx.fill()

  // Мы рисуем сам ник
  ctx.fillStyle    = color
  ctx.textBaseline = 'middle'
  ctx.fillText(text, x, nickY)

  // Мы выделяем нашего игрока тонкой циановой обводкой под ником
  if (isLocal) {
    ctx.beginPath()
    ctx.moveTo(x - tw / 2, nickY + 8)
    ctx.lineTo(x + tw / 2, nickY + 8)
    ctx.strokeStyle = PALETTE.nicknameLocal + '88'
    ctx.lineWidth   = 1
    ctx.stroke()
  }
}

// ─── Мишень ───────────────────────────────────────────────────────────────

/**
 * Мы рисуем концентрические кольца мишени с градиентными цветами.
 * Мишень прыгает вертикально — сервер присылает актуальную позицию в каждом кадре.
 *
 * @param points  очки за попадание — чем выше, тем ярче центр
 */
export function drawTarget(
  ctx:    CanvasRenderingContext2D,
  target: TargetDto,
): void {
  const { x, y, size, points } = target
  const rings = 4

  // Мы рисуем тень под мишенью
  ctx.save()
  ctx.beginPath()
  ctx.ellipse(x, y + size * 0.1, size * 0.6, size * 0.15, 0, 0, Math.PI * 2)
  ctx.fillStyle = 'rgba(0, 0, 0, 0.3)'
  ctx.fill()

  // Мы рисуем кольца от внешнего к центру
  for (let i = rings; i >= 1; i--) {
    const r      = (size * i) / rings
    const color  = PALETTE.target[rings - i]
    const alpha  = i === 1 ? 1.0 : 0.85

    ctx.beginPath()
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fillStyle = color + Math.round(alpha * 255).toString(16).padStart(2, '0')
    ctx.fill()

    // Мы рисуем обводку каждого кольца
    ctx.strokeStyle = 'rgba(0, 0, 0, 0.4)'
    ctx.lineWidth   = 1.5
    ctx.stroke()
  }

  // Мы рисуем крестик в центре мишени
  const cross = size * 0.08
  ctx.strokeStyle = '#000'
  ctx.lineWidth   = 1.5
  ctx.beginPath()
  ctx.moveTo(x - cross, y); ctx.lineTo(x + cross, y)
  ctx.moveTo(x, y - cross); ctx.lineTo(x, y + cross)
  ctx.stroke()

  // Мы отображаем очки за попадание рядом с мишенью
  if (points > 1) {
    ctx.font         = `bold ${Math.max(11, size * 0.3)}px "JetBrains Mono", monospace`
    ctx.textAlign    = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillStyle    = '#fff'
    ctx.fillText(`+${points}`, x + size * 1.1, y)
  }

  ctx.restore()
}

// ─── Стрела в полёте ──────────────────────────────────────────────────────

/**
 * Мы рисуем летящую стрелу по данным из GameStateMessage.
 * Стрела имеет тело (линия), наконечник (треугольник) и оперение (V-форма).
 *
 * @param ownerIsLocal  стрела нашего игрока — рисуем цианом, иначе красным
 */
export function drawArrow(
  ctx:          CanvasRenderingContext2D,
  arrow:        ArrowDto,
  ownerIsLocal: boolean,
): void {
  if (!arrow.active) return

  const { x, y, angleDegrees, width, height } = arrow
  const color = ownerIsLocal ? PALETTE.player : PALETTE.opponent

  ctx.save()
  ctx.translate(x, y)
  ctx.rotate((angleDegrees * Math.PI) / 180)

  const len = Math.max(width, 20)

  // Мы рисуем тело стрелы (древко)
  ctx.beginPath()
  ctx.moveTo(-len / 2, 0)
  ctx.lineTo(len / 2 - 8, 0)
  ctx.strokeStyle = PALETTE.arrow
  ctx.lineWidth   = 2.5
  ctx.lineCap     = 'round'
  ctx.stroke()

  // Мы рисуем наконечник
  _drawArrowTip(ctx, len / 2 - 8, 0, 0)

  // Мы рисуем оперение (fletching) в конце стрелы
  ctx.beginPath()
  ctx.moveTo(-len / 2, 0)
  ctx.lineTo(-len / 2 + 8, -5)
  ctx.moveTo(-len / 2, 0)
  ctx.lineTo(-len / 2 + 8, 5)
  ctx.strokeStyle = color
  ctx.lineWidth   = 1.5
  ctx.stroke()

  ctx.restore()
}

// ─── Вспомогательные функции ──────────────────────────────────────────────

/**
 * Мы рисуем треугольный наконечник стрелы.
 * Вынесено в отдельную функцию — используется как для летящих стрел, так и для нацеленных.
 */
function _drawArrowTip(
  ctx: CanvasRenderingContext2D,
  tipX: number,
  tipY: number,
  rotation: number,
): void {
  ctx.save()
  ctx.translate(tipX, tipY)
  ctx.rotate(rotation)

  ctx.beginPath()
  ctx.moveTo(8, 0)
  ctx.lineTo(-4, -4)
  ctx.lineTo(-2, 0)
  ctx.lineTo(-4, 4)
  ctx.closePath()
  ctx.fillStyle = PALETTE.arrowTip
  ctx.fill()

  ctx.restore()
}

// ─── Фоновая сетка ────────────────────────────────────────────────────────

/**
 * Мы рисуем декоративную сетку фона игрового поля.
 * Это дешёвая операция — рисуем один раз и кэшируем в offscreen canvas.
 */
export function drawBackground(
  ctx:    CanvasRenderingContext2D,
  width:  number,
  height: number,
): void {
  // Мы заливаем фон тёмным градиентом
  const grad = ctx.createLinearGradient(0, 0, 0, height)
  grad.addColorStop(0, '#0f0f1a')
  grad.addColorStop(1, '#07070f')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, width, height)

  // Мы рисуем тонкую сетку для ощущения пространства
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.025)'
  ctx.lineWidth   = 1

  const step = 48
  for (let gx = 0; gx < width; gx += step) {
    ctx.beginPath()
    ctx.moveTo(gx, 0)
    ctx.lineTo(gx, height)
    ctx.stroke()
  }
  for (let gy = 0; gy < height; gy += step) {
    ctx.beginPath()
    ctx.moveTo(0, gy)
    ctx.lineTo(width, gy)
    ctx.stroke()
  }

  // Мы рисуем горизонтальную линию «земли»
  ctx.strokeStyle = 'rgba(6, 182, 212, 0.15)'
  ctx.lineWidth   = 2
  ctx.beginPath()
  ctx.moveTo(0, height - 48)
  ctx.lineTo(width, height - 48)
  ctx.stroke()
}
