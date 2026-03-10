package org.example.marksmanfx.server.game.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.marksmanfx.server.game.dto.RoomFullEvent;
import org.example.marksmanfx.server.game.dto.RoomInfoDto;
import org.example.marksmanfx.server.game.dto.RoomStateMessage;
import org.example.marksmanfx.server.game.model.GamePhase;
import org.example.marksmanfx.server.game.model.GameRoom;
import org.example.marksmanfx.server.game.model.RoomParticipant;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Сервис управления игровыми комнатами.
 *
 * Мы используем ConcurrentHashMap для хранения комнат — потокобезопасный
 * доступ без глобальных блокировок важен при одновременных STOMP-сообщениях.
 *
 * После каждого изменения состава мы рассылаем два типа сообщений:
 *   broadcastRoomState()  → /topic/room/{roomId}     — обновление участникам комнаты
 *   broadcastLobbyState() → /topic/lobby             — обновление списка для всего лобби
 *   sendRoomJoinedToUser()→ /user/queue/room-joined  — персональное подтверждение входа
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    // ──────────────────────────────────────────────────────────────────────
    //  Управление комнатами
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы создаём новую комнату и добавляем создателя первым игроком.
     * После создания уведомляем создателя и обновляем список лобби.
     */
    public GameRoom createRoom(String roomName, String creatorUsername, String sessionId) {
        String roomId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        GameRoom room = new GameRoom(roomId, roomName);

        room.addParticipant(creatorUsername, sessionId);
        rooms.put(roomId, room);

        log.info("[Комната {}] Создана игроком '{}', sessionId={}", roomId, creatorUsername, sessionId);

        // Мы уведомляем создателя — он получит roomId и перейдёт в /game/{roomId}
        sendRoomJoinedToUser(sessionId, room);
        // Мы рассылаем обновлённый список всем, кто смотрит лобби
        broadcastLobbyState();
        return room;
    }

    /**
     * Мы добавляем пользователя в существующую комнату.
     * Если слотов нет — он становится зрителем автоматически.
     */
    public RoomParticipant joinRoom(String roomId, String username, String sessionId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Комната не найдена: " + roomId);
        }

        RoomParticipant participant = room.addParticipant(username, sessionId);
        log.info("[Комната {}] '{}' вошёл как {} (sessionId={})",
                roomId, username, participant.getRole(), sessionId);

        // Мы уведомляем всех участников комнаты об обновлении состава
        broadcastRoomState(room);
        // Мы уведомляем вошедшего лично — он перейдёт в /game/{roomId}
        sendRoomJoinedToUser(sessionId, room);
        // Мы обновляем счётчик игроков в списке лобби для наблюдателей
        broadcastLobbyState();
        return participant;
    }

    /**
     * Мы удаляем участника из комнаты при выходе или разрыве соединения.
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
            broadcastRoomState(room);
        }
        // Мы обновляем список лобби (счётчик игроков уменьшился или комната исчезла)
        broadcastLobbyState();
    }

    /**
     * Мы ищем первую незаполненную комнату или создаём новую.
     * Это позволяет сыграть без выбора комнаты вручную.
     */
    public void quickMatch(String username, String sessionId) {
        // Мы ищем первую подходящую комнату: в фазе LOBBY и с наличием слотов
        GameRoom target = rooms.values().stream()
                .filter(r -> r.getPlayerCount() < GameRoom.MAX_PLAYERS
                             && r.getPhase() == GamePhase.LOBBY)
                .findFirst()
                .orElse(null);

        if (target == null) {
            // Мы создаём новую комнату, если свободных нет
            String roomId   = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String roomName = "Быстрая игра #" + roomId.substring(0, 4);
            target = new GameRoom(roomId, roomName);
            rooms.put(roomId, target);
            log.info("[Лобби] QuickMatch: создана комната '{}' для '{}'", roomName, username);
        } else {
            log.info("[Лобби] QuickMatch: '{}' → существующая комната '{}'",
                    username, target.getRoomId());
        }

        target.addParticipant(username, sessionId);
        broadcastRoomState(target);
        // Мы сообщаем пользователю его комнату — он перейдёт в /game/{roomId}
        sendRoomJoinedToUser(sessionId, target);
        broadcastLobbyState();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Повышение зрителя до игрока
    // ──────────────────────────────────────────────────────────────────────

    public void requestUpgradeToPlayer(String roomId, String sessionId, String username) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            log.warn("[UpgradeToPlayer] Комната {} не найдена для '{}'", roomId, username);
            return;
        }

        if (room.getPhase() != GamePhase.LOBBY) {
            sendPersonalError(username, sessionId, "Смена роли возможна только в фазе лобби");
            return;
        }

        boolean upgraded = room.upgradeSpectatorToPlayer(sessionId);

        if (upgraded) {
            log.info("[Комната {}] '{}' повышен до игрока ({}/{})",
                    roomId, username, room.getPlayerCount(), GameRoom.MAX_PLAYERS);
            broadcastRoomState(room);
            broadcastLobbyState();
        } else {
            log.info("[Комната {}] '{}' отклонён: комната заполнена ({}/{})",
                    roomId, username, room.getPlayerCount(), GameRoom.MAX_PLAYERS);
            sendPersonalError(username, sessionId, "К сожалению, свободных мест для игроков нет");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Рассылка
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы рассылаем снимок состава комнаты всем её участникам.
     * Подписчики /topic/room/{roomId} получат актуальный список.
     */
    public void broadcastRoomState(GameRoom room) {
        RoomStateMessage message = new RoomStateMessage(
                "ROOM_STATE",
                room.getRoomId(),
                room.getRoomName(),
                room.getPhase(),
                room.getParticipants()
        );
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), message);
    }

    /**
     * Мы рассылаем обновлённый список комнат всем подписчикам лобби.
     * Вызывается при каждом изменении: создание/удаление комнаты, вход/выход игрока.
     */
    public void broadcastLobbyState() {
        messagingTemplate.convertAndSend("/topic/lobby", getAllRoomsInfo());
    }

    /**
     * Мы отправляем персональное подтверждение входа в комнату.
     *
     * Клиент подписывается на /user/queue/room-joined.
     * При получении этого сообщения он:
     *   1. Сохраняет currentRoom в Pinia
     *   2. Подписывается на /topic/room/{roomId} и /topic/game/{roomId}
     *   3. Переходит на страницу /game/{roomId}
     *
     * Мы используем тип "ROOM_JOINED" чтобы клиент отличил его от "ROOM_STATE".
     */
    private void sendRoomJoinedToUser(String sessionId, GameRoom room) {
        RoomStateMessage msg = new RoomStateMessage(
                "ROOM_JOINED",
                room.getRoomId(),
                room.getRoomName(),
                room.getPhase(),
                room.getParticipants()
        );
        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/room-joined",
                msg,
                Map.of("simpSessionId", sessionId)
        );
    }

    /**
     * Мы отправляем персональное сообщение об ошибке конкретному клиенту.
     */
    private void sendPersonalError(String username, String sessionId, String errorText) {
        RoomFullEvent event = new RoomFullEvent(errorText);
        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/errors",
                event,
                Map.of("simpSessionId", sessionId)
        );
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Геттеры / утилиты
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы преобразуем внутреннее состояние всех комнат в плоские DTO для клиента.
     * Этот список используется как начальное состояние лобби (@SubscribeMapping)
     * и как тело каждого broadcastLobbyState().
     */
    public List<RoomInfoDto> getAllRoomsInfo() {
        return rooms.values().stream()
                .map(r -> new RoomInfoDto(
                        r.getRoomId(),
                        r.getRoomName(),
                        (int) r.getPlayerCount(),
                        GameRoom.MAX_PLAYERS,
                        r.getPhase().name()
                ))
                .collect(Collectors.toList());
    }

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public Map<String, GameRoom> getAllRooms() {
        return rooms;
    }
}
