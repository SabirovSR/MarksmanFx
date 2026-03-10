package org.example.marksmanfx.server.game.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.marksmanfx.server.game.dto.ShotBroadcastMessage;
import org.example.marksmanfx.server.game.dto.ShotMessage;
import org.example.marksmanfx.server.game.dto.SpectatorUpgradeRequest;
import org.example.marksmanfx.server.game.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP WebSocket контроллер для игровых событий.
 *
 * Архитектурный паттерн: Command + Observer.
 *   — Клиент отправляет команду (выстрел, запрос смены роли).
 *   — Сервер обрабатывает и оповещает всех наблюдателей через топик.
 *
 * Маршруты входящих сообщений от клиента:
 *   /app/game/{roomId}/shot            → handleShot()
 *   /app/game/{roomId}/join            → onSubscribeToRoom() (при подписке)
 *   /app/room/{roomId}/upgrade         → handleSpectatorUpgrade()
 *
 * Маршруты исходящих сообщений к клиентам:
 *   /topic/game/{roomId}               → координаты выстрела (все в комнате)
 *   /topic/room/{roomId}               → обновление состава комнаты
 *   /user/queue/errors                 → персональные ошибки (только клиенту)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final RoomService roomService;

    // ──────────────────────────────────────────────────────────────────────
    //  Блок 1: Обработка выстрела и рассылка координат
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы принимаем координаты выстрела от игрока и рассылаем их всем участникам комнаты.
     *
     * @MessageMapping("/game/{roomId}/shot") — клиент шлёт на /app/game/A1B2C3D4/shot
     * @SendTo("/topic/game/{roomId}")        — ответ рассылается всем подписчикам топика
     *
     * Важно: @SendTo автоматически подставляет {roomId} из пути.
     * Principal автоматически извлекается из JWT через наш JwtChannelInterceptor.
     *
     * @param roomId    ID комнаты из URL
     * @param shot      координаты выстрела из JSON payload
     * @param principal аутентифицированный пользователь (имя из JWT)
     * @return ShotBroadcastMessage — рассылается всем в /topic/game/{roomId}
     */
    @MessageMapping("/game/{roomId}/shot")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/game/{roomId}")
    public ShotBroadcastMessage handleShot(
            @DestinationVariable String roomId,
            @Payload @Valid ShotMessage shot,
            Principal principal) {

        String shooterName = principal.getName();
        log.debug("[Комната {}] Выстрел от '{}': aim=({}, {}), charge={}",
                roomId, shooterName, shot.getAimX(), shot.getAimY(), shot.getChargeRatio());

        // Формируем broadcast-сообщение с именем стрелявшего и координатами
        return new ShotBroadcastMessage(
                shooterName,
                shot.getAimX(),
                shot.getAimY(),
                shot.getChargeRatio()
        );
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Блок 3: Управление ролями (зритель → игрок)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы обрабатываем запрос зрителя на получение роли игрока.
     *
     * Логика делегирована в RoomService, который:
     *   — Проверяет наличие свободного слота (< 4 игроков)
     *   — При успехе → рассылает обновлённый RoomStateMessage ВСЕМ через /topic/room/{roomId}
     *   — При неудаче → отправляет RoomFullEvent ТОЛЬКО этому клиенту через /user/queue/errors
     *
     * @param roomId         ID комнаты из URL
     * @param request        тело запроса (содержит roomId для валидации)
     * @param principal      аутентифицированный пользователь
     * @param headerAccessor для получения sessionId текущей WebSocket-сессии
     */
    @MessageMapping("/room/{roomId}/upgrade")
    public void handleSpectatorUpgrade(
            @DestinationVariable String roomId,
            @Payload SpectatorUpgradeRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        String username  = principal.getName();
        // Получаем sessionId WebSocket-сессии для адресной отправки ошибки
        String sessionId = headerAccessor.getSessionId();

        log.info("[Комната {}] Зритель '{}' запрашивает статус игрока (session={})",
                roomId, username, sessionId);

        // Делегируем всю логику проверки в сервис
        roomService.requestUpgradeToPlayer(roomId, sessionId, username);
    }

    /**
     * Мы отправляем текущее состояние комнаты клиенту в момент подписки на топик.
     *
     * @SubscribeMapping срабатывает, когда клиент подписывается на /app/room/{roomId}/state.
     * Ответ идёт напрямую подписчику (не через брокер), поэтому @SendTo не нужен.
     */
    @SubscribeMapping("/room/{roomId}/state")
    public ShotBroadcastMessage onSubscribeToRoom(
            @DestinationVariable String roomId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        String username  = principal.getName();
        String sessionId = headerAccessor.getSessionId();
        log.info("[Комната {}] '{}' подключился (session={})", roomId, username, sessionId);

        // Добавляем участника и рассылаем обновлённое состояние
        roomService.joinRoom(roomId, username, sessionId);
        return null;
    }
}
