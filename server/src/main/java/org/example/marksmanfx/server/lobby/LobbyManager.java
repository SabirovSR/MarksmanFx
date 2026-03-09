package org.example.marksmanfx.server.lobby;

import org.example.marksmanfx.common.message.ErrorMessage;
import org.example.marksmanfx.common.message.LobbyStateMessage;
import org.example.marksmanfx.common.model.RoomInfo;
import org.example.marksmanfx.server.game.GameRoom;
import org.example.marksmanfx.server.network.ClientHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

/**
 * Центральный координатор лобби и всех активных комнат.
 * Все изменяющие методы синхронизированы по {@code this}.
 * {@link CopyOnWriteArraySet} для lobbyClients позволяет безопасно итерироваться
 * по клиентам во время рассылки без удержания монитора.
 */
public final class LobbyManager {

    private static final Logger LOG = Logger.getLogger(LobbyManager.class.getName());

    /** Клиенты, которые находятся в лобби, а не в комнате. */
    private final Set<ClientHandler> lobbyClients = new CopyOnWriteArraySet<>();

    /** Активные комнаты по roomId. Порядок вставки сохраняется для стабильного списка. */
    private final Map<String, GameRoom> rooms = new LinkedHashMap<>();

    // Регистрация в лобби.
    public void addLobbyClient(ClientHandler client) {
        lobbyClients.add(client);
        LOG.info("[Лобби] " + client.getNickname() + " вошёл в лобби ("
                + lobbyClients.size() + " в лобби)");
        client.sendMessage(new LobbyStateMessage(getRoomList()));
    }

    public void removeLobbyClient(ClientHandler client) {
        lobbyClients.remove(client);
    }

    // Операции с комнатами.
    public synchronized void createRoom(ClientHandler client, String roomName) {
        if (client.getCurrentRoomId() != null) {
            client.sendMessage(new ErrorMessage("Вы уже находитесь в комнате."));
            return;
        }
        String roomId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        GameRoom room = new GameRoom(roomId, roomName, this);
        rooms.put(roomId, room);
        LOG.info("[Лобби] Комната '" + roomName + "' (" + roomId + ") создана игроком " + client.getNickname());

        moveToRoom(client, room);
    }

    public synchronized void joinRoom(ClientHandler client, String roomId) {
        if (client.getCurrentRoomId() != null) {
            client.sendMessage(new ErrorMessage("Вы уже находитесь в комнате. Сначала выйдите из неё."));
            return;
        }
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            client.sendMessage(new ErrorMessage("Комната не найдена: " + roomId));
            return;
        }
        if (room.isFull()) {
            client.sendMessage(new ErrorMessage("Комната заполнена."));
            return;
        }
        moveToRoom(client, room);
    }

    public synchronized void quickMatch(ClientHandler client) {
        if (client.getCurrentRoomId() != null) {
            client.sendMessage(new ErrorMessage("Вы уже находитесь в комнате."));
            return;
        }

        // Ищем первую комнату, в которой ещё есть место.
        GameRoom target = rooms.values().stream()
                .filter(r -> !r.isFull())
                .findFirst()
                .orElse(null);

        if (target == null) {
            // Если свободных комнат нет, создаём новую автоматически.
            String roomId   = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String roomName = "Комната игрока " + client.getNickname();
            target = new GameRoom(roomId, roomName, this);
            rooms.put(roomId, target);
            LOG.info("[Лобби] Быстрый матч: создана новая комната '" + roomName + "' (" + roomId + ")");
        } else {
            LOG.info("[Лобби] Быстрый матч: " + client.getNickname()
                    + " направлен в существующую комнату " + target.getRoomId());
        }
        moveToRoom(client, target);
    }

    public synchronized void leaveRoom(ClientHandler client) {
        String roomId = client.getCurrentRoomId();
        if (roomId == null) return;
        GameRoom room = rooms.get(roomId);
        if (room != null) {
            room.removePlayer(client);
        }
        lobbyClients.add(client);
        client.setCurrentRoomId(null);
        client.sendMessage(new LobbyStateMessage(getRoomList()));
        LOG.info("[Лобби] " + client.getNickname() + " вернулся в лобби");
    }

    /** Вызывается, когда сокет клиента неожиданно закрывается. */
    public synchronized void handleDisconnect(ClientHandler client) {
        LOG.info("[Лобби] Отключение: " + client.getNickname());
        lobbyClients.remove(client);
        String roomId = client.getCurrentRoomId();
        if (roomId != null) {
            GameRoom room = rooms.get(roomId);
            if (room != null) {
                room.removePlayer(client);
            }
        }
    }

    /** Удаляет пустую комнату, когда из неё выходит последний игрок. */
    public synchronized void removeRoom(String roomId) {
        GameRoom removed = rooms.remove(roomId);
        if (removed != null) {
            LOG.info("[Лобби] Комната " + roomId + " (" + removed.getRoomName() + ") расформирована");
            broadcastLobbyState();
        }
    }

    // Рассылка состояния лобби.
    public void broadcastLobbyState() {
        LobbyStateMessage msg = new LobbyStateMessage(getRoomList());
        for (ClientHandler c : lobbyClients) {
            c.sendMessage(msg);
        }
    }

    // Запросы состояния.
    public synchronized List<RoomInfo> getRoomList() {
        List<RoomInfo> list = new ArrayList<>();
        for (GameRoom r : rooms.values()) {
            list.add(r.toRoomInfo());
        }
        return list;
    }

    public synchronized GameRoom getRoomById(String roomId) {
        return rooms.get(roomId);
    }

    // Вспомогательные методы.
    private void moveToRoom(ClientHandler client, GameRoom room) {
        lobbyClients.remove(client);
        room.addPlayer(client);
        broadcastLobbyState();
    }
}

