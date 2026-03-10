// ═══════════════════════════════════════════════════════════════════════════
// Типы игрового протокола — зеркало Java-моделей из common и server-spring
// Мы дублируем типы здесь, а не генерируем из Java, ради простоты и
// независимости фронтенда от бэкенд-артефактов.
// ═══════════════════════════════════════════════════════════════════════════

// ─── Enum-ы протокола ─────────────────────────────────────────────────────

export type GamePhase =
  | 'LOBBY'
  | 'PLAYING'
  | 'PAUSE_REQUESTED'
  | 'PAUSED'
  | 'FINISHED'

export type PlayerRole = 'PLAYER' | 'SPECTATOR'

// ─── Участники ────────────────────────────────────────────────────────────

/** Участник комнаты — игрок или зритель */
export interface RoomParticipant {
  username:  string
  sessionId: string
  role:      PlayerRole
  ready:     boolean
}

// ─── Сообщения WebSocket (сервер → клиент) ────────────────────────────────

/** Обновление состава комнаты — рассылается всем при любом изменении */
export interface RoomStateMessage {
  type:         'ROOM_STATE'
  roomId:       string
  roomName:     string
  phase:        GamePhase
  participants: RoomParticipant[]
}

/** Результат выстрела — рассылается всем в комнате */
export interface ShotBroadcastMessage {
  type:            'SHOT'
  shooterUsername: string
  aimX:            number
  aimY:            number
  chargeRatio:     number
  timestamp:       number
}

/** Персональная ошибка — отправляется только конкретному клиенту */
export interface RoomFullEvent {
  type:    'ROOM_FULL'
  message: string
}

/** Снимок состояния мира для рендеринга (60 fps) */
export interface GameStateMessage {
  type:    'GAME_STATE'
  players: PlayerStateDto[]
  arrows:  ArrowDto[]
  targets: TargetDto[]
}

/** Начало обратного отсчёта перед матчем */
export interface CountdownEvent {
  type:  'COUNTDOWN'
  value: number | 'GO'
}

// ─── Игровые объекты ──────────────────────────────────────────────────────

export interface PlayerStateDto {
  playerId:  string
  username:  string
  x:         number
  y:         number
  aimAngle:  number
  crouched:  boolean
  score:     number
}

export interface ArrowDto {
  ownerId:      string
  active:       boolean
  x:            number
  y:            number
  angleDegrees: number
  width:        number
  height:       number
}

export interface TargetDto {
  x:      number
  y:      number
  size:   number
  points: number
}

// ─── Лобби ────────────────────────────────────────────────────────────────

export interface RoomInfo {
  roomId:      string
  roomName:    string
  playerCount: number
  maxPlayers:  number
  phase:       GamePhase
}

// ─── HTTP-ответы REST API ─────────────────────────────────────────────────

export interface AuthResponse {
  token:     string
  username:  string
  expiresIn: number
}

export interface UserProfileDto {
  id:       number
  username: string
}

export interface ApiError {
  error:   string
  status?: number
}

// ─── Утилитарные типы ─────────────────────────────────────────────────────

/** Типизированное состояние запроса — используется в Pinia-сторах */
export interface AsyncState<T = undefined> {
  isLoading: boolean
  error:     string | null
  data:      T | null
}
