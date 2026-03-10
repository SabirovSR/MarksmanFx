package org.example.marksmanfx.server.game.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.marksmanfx.server.game.dto.CreateRoomRequest;
import org.example.marksmanfx.server.game.dto.JoinRoomRequest;
import org.example.marksmanfx.server.game.dto.RoomInfoDto;
import org.example.marksmanfx.server.game.service.RoomService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

/**
 * STOMP-контроллер лобби: создание/вход в комнату и начальная загрузка списка.
 *
 * Почему раньше «ничего не происходило»?
 * ─────────────────────────────────────
 * Клиент отправлял сообщения на /app/lobby/create, /app/lobby/join,
 * /app/lobby/quickmatch — но ни одного @MessageMapping с такими путями
 * на сервере не существовало. Spring STOMP молча дропает сообщения,
 * на которые нет обработчика: никакой ошибки в логах, никакого ответа клиенту.
 * Этот класс добавляет все три обработчика и устраняет «тихое» падение.
 *
 * Маршруты:
 *   SUBSCRIBE /app/lobby           → @SubscribeMapping  — отдаём текущий список комнат
 *   SEND /app/lobby/create         → @MessageMapping    — создать комнату
 *   SEND /app/lobby/join           → @MessageMapping    — войти в комнату
 *   SEND /app/lobby/quickmatch     → @MessageMapping    — быстрый матч
 *
 * После любого из действий клиент получает два сообщения:
 *   1. /user/queue/room-joined    — персонально: roomId + состав → клиент навигирует в /game
 *   2. /topic/lobby               — широковещательно: обновлённый список всем в лобби
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LobbyWebSocketController {

    private final RoomService roomService;

    // ──────────────────────────────────────────────────────────────────────
    //  Первоначальная загрузка списка комнат
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы отдаём текущий список комнат в момент подписки клиента на лобби.
     *
     * @SubscribeMapping срабатывает когда клиент подписывается на /app/lobby.
     * Возвращаемый список идёт напрямую подписчику (минуя брокер) —
     * это гарантирует что клиент увидит комнаты сразу, не ожидая изменений.
     */
    @SubscribeMapping("/lobby")
    public List<RoomInfoDto> onLobbySubscribe(Principal principal) {
        log.info("[Лобби] '{}' подписался → отправляем текущий список ({} комнат)",
                principal.getName(), roomService.getAllRooms().size());
        return roomService.getAllRoomsInfo();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Создание комнаты
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы создаём новую комнату по запросу аутентифицированного пользователя.
     *
     * После создания:
     *   — Создателю отправляется /user/queue/room-joined с roomId → навигация
     *   — Всем в лобби рассылается /topic/lobby с обновлённым списком
     */
    @MessageMapping("/lobby/create")
    public void handleCreateRoom(
            @Payload CreateRoomRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        String username  = principal.getName();
        String sessionId = headerAccessor.getSessionId();

        // Мы логируем каждый запрос — это первое, что нужно увидеть в логах сервера
        log.info("[Лобби] '{}' создаёт комнату '{}' (session={})",
                username, request.roomName(), sessionId);

        if (request.roomName() == null || request.roomName().isBlank()) {
            log.warn("[Лобби] '{}' прислал пустое название комнаты", username);
            return;
        }

        // Мы делегируем создание сервису — он оповещает нужных участников
        roomService.createRoom(request.roomName().trim(), username, sessionId);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Вход в существующую комнату
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы добавляем пользователя в указанную комнату.
     * Если слотов нет — он автоматически становится зрителем (логика в GameRoom).
     */
    @MessageMapping("/lobby/join")
    public void handleJoinRoom(
            @Payload JoinRoomRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        String username  = principal.getName();
        String sessionId = headerAccessor.getSessionId();

        log.info("[Лобби] '{}' входит в комнату '{}' (session={})",
                username, request.roomId(), sessionId);

        try {
            roomService.joinRoom(request.roomId(), username, sessionId);
        } catch (IllegalArgumentException e) {
            // Мы логируем ошибку и не крашим всё соединение — STOMP-сессия остаётся живой
            log.warn("[Лобби] Ошибка входа '{}' в комнату '{}': {}",
                    username, request.roomId(), e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Быстрый матч
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы находим первую незаполненную комнату или создаём новую.
     * Это позволяет новичкам начать игру одной кнопкой без выбора комнаты.
     */
    @MessageMapping("/lobby/quickmatch")
    public void handleQuickMatch(
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        String username  = principal.getName();
        String sessionId = headerAccessor.getSessionId();

        log.info("[Лобби] '{}' запрашивает быстрый матч (session={})", username, sessionId);
        roomService.quickMatch(username, sessionId);
    }
}
