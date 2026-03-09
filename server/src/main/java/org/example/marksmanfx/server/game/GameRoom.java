package org.example.marksmanfx.server.game;

import org.example.marksmanfx.common.message.GameOverMessage;
import org.example.marksmanfx.common.message.GameStateMessage;
import org.example.marksmanfx.common.message.RoomJoinedMessage;
import org.example.marksmanfx.common.message.RoomUpdatedMessage;
import org.example.marksmanfx.common.message.ServerMessage;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.common.model.PlayerInfo;
import org.example.marksmanfx.common.model.RoomInfo;
import org.example.marksmanfx.server.lobby.LobbyManager;
import org.example.marksmanfx.server.network.ClientHandler;
import org.example.marksmanfx.server.state.FinishedState;
import org.example.marksmanfx.server.state.PlayingState;
import org.example.marksmanfx.server.state.RoomState;
import org.example.marksmanfx.server.state.WaitingState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A multiplayer room that hosts up to MAX_PLAYERS concurrent players.
 *
 * Delegates all lifecycle events to the current {@link RoomState} implementation
 * (State pattern). The state transitions drive the room between
 * WAITING → PLAYING → PAUSED / PAUSE_REQUESTED → FINISHED → WAITING.
 */
public final class GameRoom {

    private static final Logger LOG         = Logger.getLogger(GameRoom.class.getName());
    public  static final int    MAX_PLAYERS = 4;

    private final String       roomId;
    private final String       roomName;
    private final LobbyManager lobbyManager;

    /** Insertion-ordered map keeps stable broadcast order. */
    private final Map<String, ClientHandler> playerMap = new LinkedHashMap<>();

    private RoomState          state   = new WaitingState();
    private ServerGameSession  session;

    public GameRoom(String roomId, String roomName, LobbyManager lobbyManager) {
        this.roomId      = roomId;
        this.roomName    = roomName;
        this.lobbyManager = lobbyManager;
    }

    // ─── Player management ────────────────────────────────────────────────────

    public synchronized boolean addPlayer(ClientHandler player) {
        if (playerMap.size() >= MAX_PLAYERS) return false;
        playerMap.put(player.getPlayerId(), player);
        player.setCurrentRoomId(roomId);

        LOG.info("[Room " + roomId + "] " + player.getNickname() + " joined ("
                + playerMap.size() + "/" + MAX_PLAYERS + ")");

        // Tell the new joiner their local playerId + the full room state
        player.sendMessage(new RoomJoinedMessage(toRoomInfo(), buildPlayerList(), player.getPlayerId()));
        // Tell existing members about the new arrival
        for (ClientHandler p : playerMap.values()) {
            if (!p.getPlayerId().equals(player.getPlayerId())) {
                p.sendMessage(new RoomUpdatedMessage(toRoomInfo(), buildPlayerList()));
            }
        }
        lobbyManager.broadcastLobbyState();
        return true;
    }

    public synchronized void removePlayer(ClientHandler player) {
        if (!playerMap.containsKey(player.getPlayerId())) return;
        playerMap.remove(player.getPlayerId());
        player.setCurrentRoomId(null);
        LOG.info("[Room " + roomId + "] " + player.getNickname() + " left");

        state = state.onPlayerDisconnect(player, this);
        broadcastRoomUpdate();
        lobbyManager.broadcastLobbyState();

        if (playerMap.isEmpty()) {
            lobbyManager.removeRoom(roomId);
        }
    }

    // ─── Event dispatch (all synchronized on the room monitor) ───────────────

    public synchronized void onPlayerReady(ClientHandler player, boolean ready) {
        state = state.onPlayerReady(player, ready, this);
    }

    public synchronized void onPauseRequest(ClientHandler player, boolean pausing) {
        state = state.onPauseRequest(player, pausing, this);
    }

    public synchronized void onFireArrow(ClientHandler player, double chargeRatio) {
        state = state.onFireArrow(player, chargeRatio, this);
    }

    public synchronized void onMove(ClientHandler player, String direction, boolean pressed) {
        state = state.onMove(player, direction, pressed, this);
    }

    public synchronized void onAim(ClientHandler player, String direction, boolean pressed) {
        state = state.onAim(player, direction, pressed, this);
    }

    public synchronized void onCrouch(ClientHandler player, boolean crouching) {
        state = state.onCrouch(player, crouching, this);
    }

    /** Обрабатываем запрос реванша — делегируем в текущее состояние (FinishedState). */
    public synchronized void onRematchRequest(ClientHandler player) {
        state = state.onRematchRequest(player, this);
    }

    /** Called by ServerGameSession when WIN_SCORE is reached. */
    public synchronized void onGameOver(String winnerId, String winnerNickname) {
        LOG.info("[Room " + roomId + "] Game over — winner: " + winnerNickname);
        broadcast(new GameOverMessage(winnerId, winnerNickname));
        state = new FinishedState(winnerId);
        lobbyManager.broadcastLobbyState();
    }

    // ─── Session management ──────────────────────────────────────────────────

    /** Called by WaitingState when all players are ready. */
    public void startGameSession() {
        List<ServerPlayerState> playerStates = new ArrayList<>();
        for (ClientHandler ch : playerMap.values()) {
            ServerPlayerState ps = new ServerPlayerState(ch.getPlayerId(), ch.getNickname());
            playerStates.add(ps);
        }

        session = new ServerGameSession(
                playerStates,
                this::onGameOver,
                this::broadcastGameState
        );
        session.start();
        LOG.info("[Room " + roomId + "] ServerGameSession started with " + playerStates.size() + " players");
    }

    /** Stops the current session and clears it (used by FinishedState before new game). */
    public void resetGameSession() {
        if (session != null) {
            session.stop();
            session = null;
        }
    }

    // ─── State transition helper (used by PlayingState on last-man-standing) ─

    public synchronized void transitionToState(RoomState newState) {
        this.state = newState;
    }

    // ─── Broadcasting ─────────────────────────────────────────────────────────

    public synchronized void broadcast(ServerMessage message) {
        for (ClientHandler p : playerMap.values()) {
            p.sendMessage(message);
        }
    }

    private void broadcastGameState(GameStateMessage msg) {
        broadcast(msg);
    }

    public void broadcastRoomUpdate() {
        broadcast(new RoomUpdatedMessage(toRoomInfo(), buildPlayerList()));
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    public String getRoomId()   { return roomId; }
    public String getRoomName() { return roomName; }

    public synchronized int getPlayerCount() { return playerMap.size(); }
    public synchronized boolean isFull()     { return playerMap.size() >= MAX_PLAYERS; }
    public synchronized boolean isEmpty()    { return playerMap.isEmpty(); }

    public synchronized List<String> getPlayerIds() {
        return new ArrayList<>(playerMap.keySet());
    }

    public synchronized String getNickname(String playerId) {
        ClientHandler ch = playerMap.get(playerId);
        return ch != null ? ch.getNickname() : "?";
    }

    public ServerGameSession getSession() { return session; }

    public synchronized RoomInfo toRoomInfo() {
        GamePhase phase = toGamePhase();
        return new RoomInfo(roomId, roomName, playerMap.size(), MAX_PLAYERS, phase);
    }

    private GamePhase toGamePhase() {
        return switch (state.name()) {
            case "PLAYING"         -> GamePhase.PLAYING;
            case "PAUSE_REQUESTED" -> GamePhase.PAUSE_REQUESTED;
            case "PAUSED"          -> GamePhase.PAUSED;
            case "FINISHED"        -> GamePhase.FINISHED;
            default                -> GamePhase.WAITING;
        };
    }

    private List<PlayerInfo> buildPlayerList() {
        List<PlayerInfo> list = new ArrayList<>();
        Set<String> readyIds = (state instanceof WaitingState ws) ? ws.getReadyIds() : Set.of();
        for (ClientHandler ch : playerMap.values()) {
            list.add(new PlayerInfo(ch.getPlayerId(), ch.getNickname(),
                    readyIds.contains(ch.getPlayerId())));
        }
        return list;
    }
}
