import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore }  from '@/stores/auth'
import { useLobbyStore } from '@/stores/lobby'
import { useGameStore }  from '@/stores/game'
import { useToastStore } from '@/stores/toast'
import type { RoomStateMessage, GameStateMessage, CountdownEvent, RoomFullEvent } from '@/types'

/**
 * Синглтон WebSocket-клиента.
 *
 * Мы используем @stomp/stompjs с SockJS-транспортом для совместимости
 * с Spring Boot SockJS эндпоинтом (/ws).
 *
 * Архитектура подписок:
 *   /topic/lobby          → список комнат (всем в лобби)
 *   /topic/room/{roomId}  → состав комнаты (всем в комнате)
 *   /topic/game/{roomId}  → игровые события (всем в комнате)
 *   /user/queue/errors    → персональные ошибки (только этому клиенту)
 */

let _client: Client | null = null
let _roomId: string | null = null

export function useWebSocketService() {

  // ── Подключение ────────────────────────────────────────────────────

  /**
   * Мы создаём STOMP-клиент и подключаемся к Spring Boot серверу.
   * JWT-токен передаётся в заголовке STOMP CONNECT.
   */
  function connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const auth = useAuthStore()
      if (!auth.token) { reject(new Error('Нет JWT токена')); return }

      // Мы отключаем существующее соединение перед созданием нового
      if (_client?.connected) return resolve()

      _client = new Client({
        // Мы используем SockJS как транспорт для совместимости с Spring Boot
        webSocketFactory: () => new SockJS(`${auth.serverUrl}/ws`),

        // Мы передаём JWT в заголовках STOMP CONNECT — JwtChannelInterceptor прочитает его
        connectHeaders: {
          Authorization: `Bearer ${auth.token}`,
        },

        // Мы настраиваем heartbeat для обнаружения разрыва соединения
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,

        // Мы автоматически переподключаемся при потере связи
        reconnectDelay: 3000,

        onConnect: () => {
          console.info('[WS] STOMP CONNECTED')
          _subscribe()
          resolve()
        },

        onStompError: (frame) => {
          const toast = useToastStore()
          toast.error('Ошибка WebSocket', frame.headers?.message ?? 'Нет соединения с сервером')
          reject(new Error(frame.headers?.message))
        },

        onDisconnect: () => {
          console.info('[WS] STOMP DISCONNECTED')
        },
      })

      _client.activate()
    })
  }

  /** Мы закрываем STOMP-соединение (при логауте или переходе на другой экран) */
  function disconnect() {
    _client?.deactivate()
    _client  = null
    _roomId  = null
  }

  // ── Подписки ──────────────────────────────────────────────────────

  /**
   * Мы подписываемся на все нужные каналы после установки соединения.
   */
  function _subscribe() {
    if (!_client?.connected) return

    // Мы слушаем обновления списка комнат в лобби
    _client.subscribe('/topic/lobby', (msg: IMessage) => {
      const lobby = useLobbyStore()
      lobby.handleLobbyState(JSON.parse(msg.body))
    })

    // Мы слушаем персональные ошибки (RoomFullEvent и другие)
    _client.subscribe('/user/queue/errors', (msg: IMessage) => {
      const event: RoomFullEvent = JSON.parse(msg.body)
      const toast = useToastStore()
      toast.warning('Нет свободных мест', event.message)
    })
  }

  /**
   * Мы подписываемся на события конкретной комнаты после входа в неё.
   * Вызывается из LobbyView когда сервер подтвердил вход в комнату.
   */
  function subscribeToRoom(roomId: string) {
    if (!_client?.connected || _roomId === roomId) return
    _roomId = roomId

    // Мы слушаем обновления состава комнаты (вход/выход, смена ролей, готовность)
    _client.subscribe(`/topic/room/${roomId}`, (msg: IMessage) => {
      const payload: RoomStateMessage = JSON.parse(msg.body)
      useLobbyStore().handleRoomState(payload)
    })

    // Мы слушаем игровые события (выстрелы, снимки мира, победа, пауза)
    _client.subscribe(`/topic/game/${roomId}`, (msg: IMessage) => {
      const payload = JSON.parse(msg.body)

      switch (payload.type) {
        case 'GAME_STATE':
          useGameStore().handleGameState(payload as GameStateMessage)
          break
        case 'COUNTDOWN':
          useGameStore().handleCountdown(payload as CountdownEvent)
          break
        case 'GAME_OVER':
          useGameStore().handleGameOver(payload.winnerUsername)
          break
      }
    })
  }

  // ── Исходящие сообщения (клиент → сервер) ─────────────────────────

  /** Мы отправляем выстрел на сервер */
  function sendShot(roomId: string, aimX: number, aimY: number, chargeRatio: number) {
    _publish(`/app/game/${roomId}/shot`, { aimX, aimY, chargeRatio })
  }

  /** Мы отправляем запрос на создание комнаты */
  function createRoom(name: string) {
    _publish('/app/lobby/create', { roomName: name })
  }

  /** Мы отправляем запрос на вход в существующую комнату */
  function joinRoom(roomId: string) {
    _publish('/app/lobby/join', { roomId })
  }

  /** Мы запрашиваем быстрый матч — сервер подберёт или создаст комнату */
  function quickMatch() {
    _publish('/app/lobby/quickmatch', {})
  }

  /** Мы отправляем запрос зрителя на получение статуса игрока */
  function requestUpgradeToPlayer(roomId: string) {
    _publish(`/app/room/${roomId}/upgrade`, { roomId })
  }

  /** Мы устанавливаем флаг "готов" перед стартом матча */
  function setReady(roomId: string, ready: boolean) {
    _publish(`/app/room/${roomId}/ready`, { ready })
  }

  // ── Приватные вспомогательные ─────────────────────────────────────

  function _publish(destination: string, body: object) {
    if (!_client?.connected) {
      useToastStore().error('Нет соединения', 'Попробуйте переподключиться')
      return
    }
    _client.publish({
      destination,
      body:    JSON.stringify(body),
      headers: { 'content-type': 'application/json' },
    })
  }

  return {
    connect, disconnect, subscribeToRoom,
    sendShot, createRoom, joinRoom, quickMatch,
    requestUpgradeToPlayer, setReady,
    get isConnected() { return _client?.connected ?? false },
  }
}
