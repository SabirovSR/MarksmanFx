import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore }  from '@/stores/auth'
import { useLobbyStore } from '@/stores/lobby'
import { useGameStore }  from '@/stores/game'
import { useToastStore } from '@/stores/toast'
import type { RoomStateMessage, GameStateMessage, CountdownEvent, RoomFullEvent } from '@/types'

/**
 * Синглтон WebSocket / STOMP клиента.
 *
 * Маршруты подписок:
 *   /app/lobby             @SubscribeMapping → начальный список комнат
 *   /topic/lobby           broadcast        → обновление списка при изменениях
 *   /topic/room/{roomId}   broadcast        → обновление состава комнаты
 *   /topic/game/{roomId}   broadcast        → игровые события (выстрелы, счёт)
 *   /user/queue/room-joined personal        → подтверждение входа → навигация
 *   /user/queue/errors      personal        → персональные ошибки (RoomFullEvent)
 */

let _client: Client | null = null
let _roomId: string | null = null

export function useWebSocketService() {

  // ── Подключение ────────────────────────────────────────────────────

  /**
   * Мы создаём STOMP-клиент и подключаемся к серверу.
   * JWT передаётся в заголовках STOMP CONNECT — JwtChannelInterceptor его читает.
   * Промис резолвится когда STOMP CONNECTED получен и все базовые подписки установлены.
   */
  function connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const auth = useAuthStore()

      // Мы проверяем наличие токена до попытки соединения
      if (!auth.token) {
        const msg = 'Нет JWT токена — нельзя подключиться к WebSocket'
        console.error('[WS]', msg)
        reject(new Error(msg))
        return
      }

      // Мы не создаём второй клиент если соединение уже активно
      if (_client?.connected) {
        console.debug('[WS] Уже подключены, пропускаем')
        resolve()
        return
      }

      console.info('[WS] Подключаемся к', auth.serverUrl)

      _client = new Client({
        // Мы используем SockJS для совместимости с Spring Boot SockJS endpoint /ws
        webSocketFactory: () => new SockJS(`${auth.serverUrl}/ws`),

        connectHeaders: {
          // Мы передаём JWT в STOMP CONNECT — JwtChannelInterceptor проверит его
          Authorization: `Bearer ${auth.token}`,
        },

        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,

        // Мы автоматически переподключаемся через 3 секунды при обрыве
        reconnectDelay: 3000,

        onConnect: () => {
          console.info('[WS] STOMP CONNECTED ✓')
          _subscribeBase()
          resolve()
        },

        onStompError: (frame) => {
          const errMsg = frame.headers?.message ?? 'STOMP ошибка'
          console.error('[WS] STOMP ERROR:', errMsg, frame)
          useToastStore().error('Ошибка WebSocket', errMsg)
          reject(new Error(errMsg))
        },

        onWebSocketError: (event) => {
          // Мы ловим ошибку нативного WebSocket (сервер недоступен, CORS и т.д.)
          const errMsg = 'WebSocket соединение не установлено'
          console.error('[WS] WebSocket error:', event)
          useToastStore().error('Ошибка сети', `${errMsg}: ${auth.serverUrl}`)
        },

        onDisconnect: () => {
          console.info('[WS] STOMP DISCONNECTED')
        },

        // Мы выводим дебаг-логи STOMP в консоль в dev-режиме
        debug: (str) => {
          if (import.meta.env.DEV) {
            console.debug('[STOMP debug]', str)
          }
        },
      })

      _client.activate()
    })
  }

  function disconnect() {
    _client?.deactivate()
    _client  = null
    _roomId  = null
    console.info('[WS] Отключились')
  }

  // ── Базовые подписки (устанавливаются один раз при connect) ────────

  /**
   * Мы устанавливаем все подписки сразу после STOMP CONNECTED.
   */
  function _subscribeBase() {
    if (!_client?.connected) return

    // Мы запрашиваем начальный список комнат через @SubscribeMapping
    // Ответ придёт напрямую нам (не через topic), поэтому отдельный handler
    _client.subscribe('/app/lobby', (msg: IMessage) => {
      console.info('[WS] Начальный список комнат получен, строк:', msg.body)
      useLobbyStore().handleLobbyState(JSON.parse(msg.body))
    })

    // Мы подписываемся на обновления списка лобби (broadcast при изменениях)
    _client.subscribe('/topic/lobby', (msg: IMessage) => {
      console.debug('[WS] /topic/lobby обновление')
      useLobbyStore().handleLobbyState(JSON.parse(msg.body))
    })

    // Мы подписываемся на персональное подтверждение входа в комнату
    _client.subscribe('/user/queue/room-joined', (msg: IMessage) => {
      const roomState: RoomStateMessage = JSON.parse(msg.body)
      console.info('[WS] room-joined → roomId=', roomState.roomId)
      // Мы делегируем обработку в lobby store — он сохранит комнату и выполнит навигацию
      useLobbyStore().handleRoomJoined(roomState)
      // Мы сразу подписываемся на топики новой комнаты
      subscribeToRoom(roomState.roomId)
    })

    // Мы подписываемся на персональные ошибки (RoomFullEvent и другие)
    _client.subscribe('/user/queue/errors', (msg: IMessage) => {
      const event: RoomFullEvent = JSON.parse(msg.body)
      console.warn('[WS] Персональная ошибка:', event)
      useToastStore().warning('Нет свободных мест', event.message)
    })
  }

  // ── Подписки на конкретную комнату ────────────────────────────────

  /**
   * Мы подписываемся на игровые топики конкретной комнаты.
   * Вызывается при получении room-joined сообщения.
   */
  function subscribeToRoom(roomId: string) {
    if (!_client?.connected) {
      console.warn('[WS] subscribeToRoom: нет соединения')
      return
    }
    if (_roomId === roomId) {
      console.debug('[WS] Уже подписаны на комнату', roomId)
      return
    }
    _roomId = roomId
    console.info('[WS] Подписываемся на комнату', roomId)

    _client.subscribe(`/topic/room/${roomId}`, (msg: IMessage) => {
      const payload: RoomStateMessage = JSON.parse(msg.body)
      console.debug('[WS] /topic/room/', roomId, payload.participants.length, 'участников')
      useLobbyStore().handleRoomState(payload)
    })

    _client.subscribe(`/topic/game/${roomId}`, (msg: IMessage) => {
      const payload = JSON.parse(msg.body)
      console.debug('[WS] /topic/game/', roomId, 'type=', payload.type)

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

  // ── Исходящие сообщения ───────────────────────────────────────────

  function sendShot(roomId: string, aimX: number, aimY: number, chargeRatio: number) {
    _publish(`/app/game/${roomId}/shot`, { aimX, aimY, chargeRatio })
  }

  function createRoom(name: string) {
    _publish('/app/lobby/create', { roomName: name })
  }

  function joinRoom(roomId: string) {
    _publish('/app/lobby/join', { roomId })
  }

  function quickMatch() {
    _publish('/app/lobby/quickmatch', {})
  }

  function requestUpgradeToPlayer(roomId: string) {
    _publish(`/app/room/${roomId}/upgrade`, { roomId })
  }

  function setReady(roomId: string, ready: boolean) {
    _publish(`/app/room/${roomId}/ready`, { ready })
  }

  // ── Внутренний publish с диагностикой ────────────────────────────

  /**
   * Мы публикуем STOMP-сообщение с явным логированием.
   *
   * Раньше этот метод молча завершался если !_client?.connected.
   * Теперь мы:
   *   1. Логируем каждую отправку — видно в консоли браузера
   *   2. Показываем toast если соединение не готово — пользователь знает, почему не работает
   *   3. Логируем ошибки через console.error — попадают в sourcemap и в Sentry
   */
  function _publish(destination: string, body: object) {
    // Мы логируем каждое исходящее сообщение — это первое, что нужно проверить при отладке
    console.debug('[WS] ▶ publish', destination, body)

    if (!_client?.connected) {
      const msg = `Нет WS-соединения для публикации в ${destination}`
      console.error('[WS]', msg)
      useToastStore().error('Нет соединения с сервером', 'Попробуйте переподключиться')
      return
    }

    try {
      _client.publish({
        destination,
        body:    JSON.stringify(body),
        headers: { 'content-type': 'application/json' },
      })
    } catch (err) {
      // Мы ловим ошибки публикации — например, если соединение упало прямо сейчас
      console.error('[WS] Ошибка публикации в', destination, ':', err)
      useToastStore().error('Ошибка отправки', `Не удалось отправить сообщение: ${destination}`)
    }
  }

  return {
    connect, disconnect, subscribeToRoom,
    sendShot, createRoom, joinRoom, quickMatch,
    requestUpgradeToPlayer, setReady,
    get isConnected() { return _client?.connected ?? false },
  }
}
