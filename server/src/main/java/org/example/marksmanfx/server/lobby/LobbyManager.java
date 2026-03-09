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
 * Central coordinator for the lobby and all active rooms.
 *
 * Thread-safety: every mutating method is synchronized on {@code this}.
 * The {@link CopyOnWriteArraySet} for lobbyClients allows safe iteration
 * while broadcasting without holding the monitor.
 */
public final class LobbyManager {

    private static final Logger LOG = Logger.getLogger(LobbyManager.class.getName());

    /** Clients that are in the lobby (not yet in any room). */
    private final Set<ClientHandler> lobbyClients = new CopyOnWriteArraySet<>();

    /** Active rooms keyed by roomId. Insertion order preserved for consistent listing. */
    private final Map<String, GameRoom> rooms = new LinkedHashMap<>();

    // ─── Lobby registration ──────────────────────────────────────────────────

    public void addLobbyClient(ClientHandler client) {
        lobbyClients.add(client);
        LOG.info("[Lobby] " + client.getNickname() + " entered the lobby ("
                + lobbyClients.size() + " in lobby)");
        client.sendMessage(new LobbyStateMessage(getRoomList()));
    }

    public void removeLobbyClient(ClientHandler client) {
        lobbyClients.remove(client);
    }

    // ─── Room operations ──────────────────────────────────────────────────────

    public synchronized void createRoom(ClientHandler client, String roomName) {
        if (client.getCurrentRoomId() != null) {
            client.sendMessage(new ErrorMessage("Already in a room."));
            return;
        }
        String roomId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        GameRoom room = new GameRoom(roomId, roomName, this);
        rooms.put(roomId, room);
        LOG.info("[Lobby] Room '" + roomName + "' (" + roomId + ") created by " + client.getNickname());

        moveTORoom(client, room);
    }

    public synchronized void joinRoom(ClientHandler client, String roomId) {
        if (client.getCurrentRoomId() != null) {
            client.sendMessage(new ErrorMessage("Already in a room. Leave first."));
            return;
        }
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            client.sendMessage(new ErrorMessage("Room not found: " + roomId));
            return;
        }
        if (room.isFull()) {
            client.sendMessage(new ErrorMessage("Room is full."));
            return;
        }
        moveTORoom(client, room);
    }

    public synchronized void quickMatch(ClientHandler client) {
        if (client.getCurrentRoomId() != null) {
            client.sendMessage(new ErrorMessage("Already in a room."));
            return;
        }
        // Find first non-full, non-finished room
        GameRoom target = rooms.values().stream()
                .filter(r -> !r.isFull())
                .findFirst()
                .orElse(null);

        if (target == null) {
            // No open rooms — create one automatically
            String roomId   = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String roomName = client.getNickname() + "'s Room";
            target = new GameRoom(roomId, roomName, this);
            rooms.put(roomId, target);
            LOG.info("[Lobby] Quick-match: created new room '" + roomName + "' (" + roomId + ")");
        } else {
            LOG.info("[Lobby] Quick-match: " + client.getNickname()
                    + " → existing room " + target.getRoomId());
        }
        moveTORoom(client, target);
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
        LOG.info("[Lobby] " + client.getNickname() + " returned to lobby");
    }

    /** Called when a client's socket closes unexpectedly. */
    public synchronized void handleDisconnect(ClientHandler client) {
        LOG.info("[Lobby] Disconnect: " + client.getNickname());
        lobbyClients.remove(client);
        String roomId = client.getCurrentRoomId();
        if (roomId != null) {
            GameRoom room = rooms.get(roomId);
            if (room != null) {
                room.removePlayer(client);
            }
        }
    }

    /** Remove an empty room (called by GameRoom when last player leaves). */
    public synchronized void removeRoom(String roomId) {
        GameRoom removed = rooms.remove(roomId);
        if (removed != null) {
            LOG.info("[Lobby] Room " + roomId + " (" + removed.getRoomName() + ") disbanded");
            broadcastLobbyState();
        }
    }

    // ─── Lobby broadcast ──────────────────────────────────────────────────────

    public void broadcastLobbyState() {
        LobbyStateMessage msg = new LobbyStateMessage(getRoomList());
        for (ClientHandler c : lobbyClients) {
            c.sendMessage(msg);
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

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

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void moveTORoom(ClientHandler client, GameRoom room) {
        lobbyClients.remove(client);
        room.addPlayer(client);
        broadcastLobbyState();
    }
}
