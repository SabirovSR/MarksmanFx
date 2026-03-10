package org.example.marksmanfx.server.game.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.marksmanfx.server.game.dto.RoomFullEvent;
import org.example.marksmanfx.server.game.dto.RoomStateMessage;
import org.example.marksmanfx.server.game.model.GamePhase;
import org.example.marksmanfx.server.game.model.GameRoom;
import org.example.marksmanfx.server.game.model.RoomParticipant;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис управления игровыми комнатами.
 *
 * Мы выносим всю логику работы с комнатами в отдельный сервис,
 * следуя принципу Single Responsibility. GameWebSocketController
 * только принимает сообщения и делегирует их сюда.
 *
 * ConcurrentHashMap обеспечивает потокобезопасный доступ к реестру комнат
 * без глобальных блокировок — важно при 60-TPS игровом цикле.
 *
 * SimpMessagingTemplate — Spring-абстракция над брокером STOMP.
 * convertAndSend()       — широковещательная рассылка в топик.
 * convertAndSendToUser() — персональное сообщение конкретному пользователю.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    /** Реестр всех активных комнат: roomId → GameRoom */
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    /** Spring-шаблон для отправки сообщений через STOMP-брокер */
    private final SimpMessagingTemplate messagingTemplate;

    // ──────────────────────────────────────────────────────────────────────
    //  Управление комнатами
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы создаём новую комнату и сразу добавляем создателя как первого участника.
     */
    public GameRoom createRoom(String roomName, String creatorUsername, String sessionId) {
        String roomId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        GameRoom room = new GameRoom(roomId, roomName);

        // Добавляем создателя — он первый, слотов достаточно, будет PLAYER
        room.addParticipant(creatorUsername, sessionId);
        rooms.put(roomId, room);

        log.info("[Комната {}] Создана игроком '{}', sessionId={}", roomId, creatorUsername, sessionId);
        return room;
    }

    /**
     * Мы добавляем пользователя в существующую комнату.
     * Если слотов для игроков нет — он становится зрителем автоматически.
     */
    public RoomParticipant joinRoom(String roomId, String username, String sessionId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Комната не найдена: " + roomId);
        }

        RoomParticipant participant = room.addParticipant(username, sessionId);
        log.info("[Комната {}] '{}' вошёл как {} (sessionId={})",
                roomId, username, participant.getRole(), sessionId);

        // Рассылаем обновлённое состояние комнаты всем участникам
        broadcastRoomState(room);
        return participant;
    }

    /**
     * Мы удаляем участника из комнаты при дисконнекте или явном выходе.
     * Если комната опустела — удаляем её из реестра.
     */
    public void leaveRoom(String roomId, String sessionId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;

        room.removeParticipant(sessionId);
        log.info("[Комната {}] sessionId={} покинул комнату", roomId, sessionId);

        if (room.isEmpty()) {
            rooms.remove(roomId);
            log.info("[Комната {}] Пустая комната удалена из реестра", roomId);
        } else {
            // Рассылаем актуальный список оставшимся участникам
            broadcastRoomState(room);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Блок 3: Повышение зрителя до игрока
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы обрабатываем запрос зрителя на получение роли игрока.
     *
     * Алгоритм:
     *   1. Находим комнату по roomId.
     *   2. Проверяем, что фаза LOBBY (нельзя присоединиться к идущей игре).
     *   3. Делегируем проверку слотов в GameRoom.upgradeSpectatorToPlayer().
     *   4а. Если слот есть → рассылаем обновлённый RoomStateMessage ВСЕМ в комнате.
     *   4б. Если слотов нет → отправляем RoomFullEvent ТОЛЬКО этому клиенту.
     *
     * @param roomId    ID комнаты
     * @param sessionId WebSocket-сессия зрителя (для персональной ошибки)
     * @param username  Имя пользователя (Principal.getName())
     */
    public void requestUpgradeToPlayer(String roomId, String sessionId, String username) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            log.warn("[UpgradeToPlayer] Комната {} не найдена для пользователя '{}'", roomId, username);
            return;
        }

        // Проверяем, что сейчас фаза лобби — только тогда разрешаем смену роли
        if (room.getPhase() != GamePhase.LOBBY) {
            sendPersonalError(username, sessionId,
                    "Смена роли возможна только в фазе лобби между матчами");
            return;
        }

        // Проверяем наличие свободных слотов для игроков
        boolean upgraded = room.upgradeSpectatorToPlayer(sessionId);

        if (upgraded) {
            log.info("[Комната {}] '{}' повышен из зрителя до игрока (слотов: {}/{})",
                    roomId, username, room.getPlayerCount(), GameRoom.MAX_PLAYERS);
            // Рассылаем обновлённый список лобби всем в комнате
            broadcastRoomState(room);
        } else {
            // Слотов нет — отправляем ошибку конкретно этому клиенту
            log.info("[Комната {}] Запрос '{}' на вступление отклонён: комната заполнена ({}/{})",
                    roomId, username, room.getPlayerCount(), GameRoom.MAX_PLAYERS);
            sendPersonalError(username, sessionId,
                    "К сожалению, свободных мест для игроков нет");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Вспомогательные методы рассылки
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы рассылаем актуальное состояние комнаты всем подписчикам топика.
     * Подписчики слушают /topic/room/{roomId}.
     */
    public void broadcastRoomState(GameRoom room) {
        RoomStateMessage message = new RoomStateMessage(
                "ROOM_STATE",
                room.getRoomId(),
                room.getRoomName(),
                room.getPhase(),
                room.getParticipants()
        );
        messagingTemplate.convertAndSend(
                "/topic/room/" + room.getRoomId(),
                message
        );
    }

    /**
     * Мы отправляем персональное сообщение об ошибке конкретному клиенту.
     *
     * convertAndSendToUser() использует комбинацию username + sessionId,
     * чтобы найти правильный WebSocket-канал даже если пользователь
     * подключён с нескольких устройств.
     *
     * Клиент слушает /user/queue/errors для получения персональных ошибок.
     */
    private void sendPersonalError(String username, String sessionId, String errorText) {
        RoomFullEvent event = new RoomFullEvent(errorText);
        // Мы используем sessionId как user для адресации конкретной WebSocket-сессии
        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/errors",
                event,
                // Флаг broadcast=false гарантирует, что сообщение идёт только в эту сессию
                Map.of("simpSessionId", sessionId)
        );
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Геттеры
    // ──────────────────────────────────────────────────────────────────────

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public Map<String, GameRoom> getAllRooms() {
        return rooms;
    }
}
