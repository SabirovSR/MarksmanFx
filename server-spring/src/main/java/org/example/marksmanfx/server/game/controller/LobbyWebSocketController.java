package org.example.marksmanfx.server.game.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.marksmanfx.server.game.dto.CreateRoomRequest;
import org.example.marksmanfx.server.game.dto.JoinRoomRequest;
import org.example.marksmanfx.server.game.dto.RoomInfoDto;
import org.example.marksmanfx.server.game.dto.RoomStateMessage;
import org.example.marksmanfx.server.game.model.GameRoom;
import org.example.marksmanfx.server.game.service.RoomService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

/**
 * STOMP-контроллер лобби.
 *
 * Почему использован @SendToUser вместо convertAndSendToUser(sessionId, ...)?
 * ──────────────────────────────────────────────────────────────────────────
 * Старый подход: roomService вызывал convertAndSendToUser(sessionId, "/queue/room-joined", ...)
 * Проблема: клиент подписался на /user/queue/room-joined, который Spring
 * регистрирует по PRINCIPAL NAME (имя из JWT-токена, установленное JwtChannelInterceptor).
 * Сервис передавал SESSION ID как "user" — это другой идентификатор.
 * Spring не находил нужную подписку → сообщение уходило в пустоту → навигации не было.
 *
 * Новый подход: @SendToUser("/queue/room-joined") на @MessageMapping методе.
 * Spring автоматически берёт Principal из текущего STOMP-сообщения (установленный
 * JwtChannelInterceptor) и маршрутизирует ответ на правильную клиентскую сессию.
 * Это стандартный Spring STOMP паттерн, гарантированно работающий.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LobbyWebSocketController {

    private final RoomService roomService;

    // ──────────────────────────────────────────────────────────────────────
    //  Первоначальная загрузка списка комнат
    // ──────────────────────────────────────────────────────────────────────

    @SubscribeMapping("/lobby")
    public List<RoomInfoDto> onLobbySubscribe(Principal principal) {
        if (principal == null) return List.of();
        log.info("[Лобби] '{}' подписался → {} комнат",
                principal.getName(), roomService.getAllRooms().size());
        return roomService.getAllRoomsInfo();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Создание комнаты
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы создаём комнату и возвращаем RoomStateMessage создателю через @SendToUser.
     *
     * @SendToUser("/queue/room-joined") отправляет возвращаемое значение напрямую
     * пользователю, который послал это сообщение, используя его Principal name.
     * Клиент подписан на /user/queue/room-joined → сообщение гарантированно дойдёт.
     */
    @MessageMapping("/lobby/create")
    @SendToUser("/queue/room-joined")
    public RoomStateMessage handleCreateRoom(
            @Payload CreateRoomRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        if (principal == null) {
            log.warn("[Лобби] Запрос createRoom без аутентификации — игнорируем");
            return null;
        }

        String username  = principal.getName();
        String sessionId = headerAccessor.getSessionId();

        log.info("[Лобби] '{}' создаёт комнату '{}' (session={})",
                username, request.roomName(), sessionId);

        if (request.roomName() == null || request.roomName().isBlank()) {
            log.warn("[Лобби] '{}' прислал пустое название комнаты", username);
            return null;
        }

        // Мы создаём комнату (без вызова sendRoomJoinedToUser — @SendToUser делает это)
        GameRoom room = roomService.createRoom(request.roomName().trim(), username, sessionId);
        // Мы возвращаем состояние комнаты — Spring направит его в /user/queue/room-joined
        return roomService.buildRoomJoinedMessage(room);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Вход в существующую комнату
    // ──────────────────────────────────────────────────────────────────────

    @MessageMapping("/lobby/join")
    @SendToUser("/queue/room-joined")
    public RoomStateMessage handleJoinRoom(
            @Payload JoinRoomRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        if (principal == null) {
            log.warn("[Лобби] Запрос joinRoom без аутентификации — игнорируем");
            return null;
        }

        String username  = principal.getName();
        String sessionId = headerAccessor.getSessionId();

        log.info("[Лобби] '{}' входит в комнату '{}' (session={})",
                username, request.roomId(), sessionId);

        try {
            GameRoom room = roomService.joinRoom(request.roomId(), username, sessionId);
            return roomService.buildRoomJoinedMessage(room);
        } catch (IllegalArgumentException e) {
            log.warn("[Лобби] Ошибка входа '{}' в '{}': {}", username, request.roomId(), e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Быстрый матч
    // ──────────────────────────────────────────────────────────────────────

    @MessageMapping("/lobby/quickmatch")
    @SendToUser("/queue/room-joined")
    public RoomStateMessage handleQuickMatch(
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        if (principal == null) {
            log.warn("[Лобби] Запрос quickMatch без аутентификации — игнорируем");
            return null;
        }

        String username  = principal.getName();
        String sessionId = headerAccessor.getSessionId();

        log.info("[Лобби] '{}' запрашивает быстрый матч (session={})", username, sessionId);
        GameRoom room = roomService.quickMatch(username, sessionId);
        return roomService.buildRoomJoinedMessage(room);
    }
}
