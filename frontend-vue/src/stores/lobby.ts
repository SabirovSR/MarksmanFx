import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useRouter } from 'vue-router'
import type { RoomInfo, RoomStateMessage, RoomParticipant } from '@/types'
import { useToastStore } from './toast'
import { useAuthStore }  from './auth'

/**
 * Стор лобби — список комнат, создание/вход, состав текущей комнаты.
 *
 * Мы разделяем isLoading на два флага:
 *   isLoadingRooms   — загрузка списка (скелетон в таблице)
 *   isLoadingAction  — действие с кнопкой (создать / войти / быстрый матч)
 */
export const useLobbyStore = defineStore('lobby', () => {
  const router = useRouter()
  const toast  = useToastStore()
  const auth   = useAuthStore()

  // ── Реактивное состояние ────────────────────────────────────────────

  const isLoadingRooms  = ref(false)
  const isLoadingAction = ref(false)

  /** Список всех доступных комнат (обновляется через /topic/lobby) */
  const rooms       = ref<RoomInfo[]>([])

  /** Текущая комната, в которой находится пользователь */
  const currentRoom = ref<RoomStateMessage | null>(null)

  // ── Вычисляемые ─────────────────────────────────────────────────────

  const currentPlayers = computed<RoomParticipant[]>(() =>
    currentRoom.value?.participants.filter(p => p.role === 'PLAYER') ?? [],
  )

  const currentSpectators = computed<RoomParticipant[]>(() =>
    currentRoom.value?.participants.filter(p => p.role === 'SPECTATOR') ?? [],
  )

  const isCurrentUserPlayer = computed(() =>
    currentRoom.value?.participants.some(
      p => p.username === auth.username && p.role === 'PLAYER',
    ) ?? false,
  )

  const isCurrentUserReady = computed(() =>
    currentRoom.value?.participants.some(
      p => p.username === auth.username && p.ready,
    ) ?? false,
  )

  // ── Входящие WebSocket-сообщения ────────────────────────────────────

  /**
   * Мы обновляем список комнат при получении /topic/lobby или ответа @SubscribeMapping.
   */
  function handleLobbyState(roomList: RoomInfo[]) {
    rooms.value = roomList
    // Мы снимаем флаг загрузки когда первые данные пришли
    isLoadingRooms.value = false
  }

  /**
   * Мы обновляем состав текущей комнаты при получении /topic/room/{roomId}.
   */
  function handleRoomState(msg: RoomStateMessage) {
    if (currentRoom.value?.roomId === msg.roomId || !currentRoom.value) {
      const wasLobby = currentRoom.value?.phase
      currentRoom.value = msg
      if (wasLobby === 'LOBBY' && msg.phase === 'PLAYING') {
        toast.info('Игра началась!', `Комната: ${msg.roomName}`)
      }
    }
  }

  /**
   * Мы обрабатываем персональное подтверждение входа в комнату.
   *
   * Этот метод вызывается из websocket.ts когда приходит /user/queue/room-joined.
   * Мы сохраняем комнату и навигируем пользователя на экран игры.
   * Это ключевое звено, которое раньше отсутствовало — пользователь создавал комнату
   * но ничего не происходило, потому что клиент не знал roomId для навигации.
   */
  function handleRoomJoined(msg: RoomStateMessage) {
    currentRoom.value = msg
    console.info('[Lobby] Вошли в комнату', msg.roomId, '→ навигация в /game')
    // Мы переходим на экран игры — передаём roomId как параметр маршрута
    router.push({ name: 'game', params: { roomId: msg.roomId } })
  }

  // ── Действия пользователя ───────────────────────────────────────────

  /**
   * Мы создаём новую комнату.
   * Ответ придёт асинхронно через /user/queue/room-joined → handleRoomJoined().
   */
  async function createRoom(name: string): Promise<void> {
    if (!name.trim()) {
      toast.warning('Введите название комнаты')
      return
    }

    // Мы проверяем подключение до попытки публикации
    const { useWebSocketService } = await import('@/services/websocket')
    const ws = useWebSocketService()

    if (!ws.isConnected) {
      toast.error('Нет соединения', 'WebSocket не подключён, попробуйте перезагрузить страницу')
      return
    }

    isLoadingAction.value = true
    try {
      console.info('[Lobby] Создаём комнату:', name.trim())
      ws.createRoom(name.trim())
      // Мы не снимаем флаг сразу — ждём room-joined от сервера
      // isLoadingAction будет сброшен в handleRoomJoined через 1-2 секунды
    } catch (err) {
      // Мы показываем ошибку пользователю — функция больше не падает молча
      console.error('[Lobby] Ошибка при создании комнаты:', err)
      toast.error('Ошибка создания комнаты', err instanceof Error ? err.message : 'Неизвестная ошибка')
      isLoadingAction.value = false
    }
  }

  /**
   * Мы присоединяемся к существующей комнате.
   * Ответ придёт через /user/queue/room-joined.
   */
  async function joinRoom(roomId: string): Promise<void> {
    const { useWebSocketService } = await import('@/services/websocket')
    const ws = useWebSocketService()

    if (!ws.isConnected) {
      toast.error('Нет соединения', 'WebSocket не подключён')
      return
    }

    isLoadingAction.value = true
    try {
      console.info('[Lobby] Входим в комнату:', roomId)
      ws.joinRoom(roomId)
    } catch (err) {
      console.error('[Lobby] Ошибка при входе в комнату:', err)
      toast.error('Ошибка входа в комнату', err instanceof Error ? err.message : 'Неизвестная ошибка')
      isLoadingAction.value = false
    }
  }

  /**
   * Мы запрашиваем быстрый матч.
   * Сервер сам найдёт или создаст комнату и отправит room-joined.
   */
  async function quickMatch(): Promise<void> {
    const { useWebSocketService } = await import('@/services/websocket')
    const ws = useWebSocketService()

    if (!ws.isConnected) {
      toast.error('Нет соединения', 'WebSocket не подключён')
      return
    }

    isLoadingAction.value = true
    try {
      console.info('[Lobby] Запрашиваем быстрый матч')
      ws.quickMatch()
    } catch (err) {
      console.error('[Lobby] Ошибка быстрого матча:', err)
      toast.error('Ошибка быстрого матча', err instanceof Error ? err.message : 'Неизвестная ошибка')
      isLoadingAction.value = false
    }
  }

  /** Мы покидаем текущую комнату и возвращаемся в лобби */
  function leaveRoom(): void {
    // Мы сбрасываем флаг действия при явном выходе
    isLoadingAction.value = false
    currentRoom.value = null
    router.push({ name: 'lobby' })
  }

  return {
    isLoadingRooms, isLoadingAction,
    rooms, currentRoom,
    currentPlayers, currentSpectators,
    isCurrentUserPlayer, isCurrentUserReady,
    handleRoomState, handleLobbyState, handleRoomJoined,
    createRoom, joinRoom, quickMatch, leaveRoom,
  }
})
