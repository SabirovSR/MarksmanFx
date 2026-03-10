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
 *   isLoadingAction  — действие (кнопка "Создать" / "Войти")
 * Это позволяет блокировать нужные кнопки точечно, не замораживая весь экран.
 */
export const useLobbyStore = defineStore('lobby', () => {
  const router = useRouter()
  const toast  = useToastStore()
  const auth   = useAuthStore()

  // ── Состояние ────────────────────────────────────────────────────────

  /** Мы отслеживаем загрузку начального списка комнат */
  const isLoadingRooms  = ref(false)

  /** Мы отслеживаем выполнение действия (создать, войти, быстрый матч) */
  const isLoadingAction = ref(false)

  /** Список всех доступных комнат (рассылается сервером через WebSocket) */
  const rooms = ref<RoomInfo[]>([])

  /** Текущая комната, в которой находится пользователь */
  const currentRoom = ref<RoomStateMessage | null>(null)

  // ── Вычисляемые ─────────────────────────────────────────────────────

  /** Мы отдельно считаем игроков и зрителей для отображения в шапке комнаты */
  const currentPlayers = computed<RoomParticipant[]>(() =>
    currentRoom.value?.participants.filter(p => p.role === 'PLAYER') ?? [],
  )

  const currentSpectators = computed<RoomParticipant[]>(() =>
    currentRoom.value?.participants.filter(p => p.role === 'SPECTATOR') ?? [],
  )

  /** Мы вычисляем, является ли текущий пользователь игроком (не зрителем) */
  const isCurrentUserPlayer = computed(() =>
    currentRoom.value?.participants.some(
      p => p.username === auth.username && p.role === 'PLAYER',
    ) ?? false,
  )

  /** Мы вычисляем, готов ли текущий пользователь к игре */
  const isCurrentUserReady = computed(() =>
    currentRoom.value?.participants.some(
      p => p.username === auth.username && p.ready,
    ) ?? false,
  )

  // ── Методы ──────────────────────────────────────────────────────────

  /**
   * Мы обрабатываем входящее RoomStateMessage от WebSocket.
   * Этот метод вызывается из websocket-сервиса при каждом обновлении комнаты.
   */
  function handleRoomState(msg: RoomStateMessage) {
    if (currentRoom.value?.roomId === msg.roomId || !currentRoom.value) {
      const wasLobby = currentRoom.value?.phase
      currentRoom.value = msg

      // Мы уведомляем пользователя когда фаза переходит в игру
      if (wasLobby === 'LOBBY' && msg.phase === 'PLAYING') {
        toast.info('Игра началась!', `Комната: ${msg.roomName}`)
      }
    }
  }

  /** Мы обновляем список комнат когда приходит LobbyStateMessage */
  function handleLobbyState(roomList: RoomInfo[]) {
    rooms.value = roomList
  }

  /** Мы создаём новую комнату через WebSocket */
  async function createRoom(name: string): Promise<void> {
    if (!name.trim()) {
      toast.warning('Введите название комнаты')
      return
    }
    isLoadingAction.value = true
    try {
      // Мы импортируем сервис статически в реальном проекте — здесь используем lazy для избежания circular-dep
      const { useWebSocketService } = await import('@/services/websocket')
      useWebSocketService().createRoom(name.trim())
    } finally {
      isLoadingAction.value = false
    }
  }

  /** Мы присоединяемся к существующей комнате */
  async function joinRoom(roomId: string): Promise<void> {
    isLoadingAction.value = true
    try {
      const { useWebSocketService } = await import('@/services/websocket')
      useWebSocketService().joinRoom(roomId)
    } finally {
      isLoadingAction.value = false
    }
  }

  /** Мы запрашиваем быстрый матч — сервер найдёт или создаст комнату */
  async function quickMatch(): Promise<void> {
    isLoadingAction.value = true
    try {
      const { useWebSocketService } = await import('@/services/websocket')
      useWebSocketService().quickMatch()
    } finally {
      isLoadingAction.value = false
    }
  }

  /** Мы покидаем текущую комнату и возвращаемся в лобби */
  function leaveRoom(): void {
    currentRoom.value = null
    router.push({ name: 'lobby' })
  }

  return {
    isLoadingRooms, isLoadingAction,
    rooms, currentRoom,
    currentPlayers, currentSpectators,
    isCurrentUserPlayer, isCurrentUserReady,
    handleRoomState, handleLobbyState,
    createRoom, joinRoom, quickMatch, leaveRoom,
  }
})
