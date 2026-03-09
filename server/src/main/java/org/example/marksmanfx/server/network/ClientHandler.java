package org.example.marksmanfx.server.network;

import org.example.marksmanfx.common.event.AimEvent;
import org.example.marksmanfx.common.event.ClientEvent;
import org.example.marksmanfx.common.event.CreateRoomEvent;
import org.example.marksmanfx.common.event.CrouchEvent;
import org.example.marksmanfx.common.event.FireArrowEvent;
import org.example.marksmanfx.common.event.JoinLobbyEvent;
import org.example.marksmanfx.common.event.JoinRoomEvent;
import org.example.marksmanfx.common.event.LeaveRoomEvent;
import org.example.marksmanfx.common.event.MoveEvent;
import org.example.marksmanfx.common.event.PauseRequestEvent;
import org.example.marksmanfx.common.event.PlayerReadyEvent;
import org.example.marksmanfx.common.event.QuickMatchEvent;
import org.example.marksmanfx.common.event.RematchRequestEvent;
import org.example.marksmanfx.common.message.ConnectedMessage;
import org.example.marksmanfx.common.message.ErrorMessage;
import org.example.marksmanfx.common.message.ServerMessage;
import org.example.marksmanfx.server.game.GameRoom;
import org.example.marksmanfx.server.lobby.LobbyManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * One instance per connected client.
 * Runs on a dedicated thread: reads ClientEvents from the socket and dispatches
 * them to LobbyManager / GameRoom.
 * Writes are serialized via a {@code synchronized} block on the output stream.
 */
public final class ClientHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    private final String         playerId   = UUID.randomUUID().toString();
    private final Socket         socket;
    private final LobbyManager   lobbyManager;
    private       ObjectOutputStream out;
    private       ObjectInputStream  in;

    private volatile String nickname;
    private volatile String currentRoomId;

    public ClientHandler(Socket socket, LobbyManager lobbyManager) {
        this.socket      = socket;
        this.lobbyManager = lobbyManager;
    }

    @Override
    public void run() {
        try {
            // ObjectOutputStream MUST be created before ObjectInputStream to avoid deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());

            LOG.info("[Handler] New connection: " + socket.getRemoteSocketAddress()
                    + " (id=" + playerId + ")");

            // First event must be JoinLobbyEvent
            Object first = in.readObject();
            if (!(first instanceof JoinLobbyEvent join)) {
                sendMessage(new ErrorMessage("First message must be JoinLobbyEvent."));
                return;
            }

            nickname = join.nickname().trim();
            if (nickname.isEmpty()) {
                sendMessage(new ErrorMessage("Nickname cannot be empty."));
                return;
            }

            LOG.info("[Handler] Registered: " + nickname + " (id=" + playerId + ")");
            sendMessage(new ConnectedMessage(playerId, nickname));
            lobbyManager.addLobbyClient(this);

            // Main read loop
            while (!socket.isClosed()) {
                Object obj = in.readObject();
                if (obj instanceof ClientEvent event) {
                    dispatch(event);
                }
            }

        } catch (IOException e) {
            LOG.info("[Handler] " + nicknameOrId() + " disconnected: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            LOG.warning("[Handler] Unknown class from " + nicknameOrId() + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ─── Event dispatch ───────────────────────────────────────────────────────

    private void dispatch(ClientEvent event) {
        switch (event) {
            case JoinLobbyEvent    e -> sendMessage(new ErrorMessage("Вы уже зарегистрированы."));
            case CreateRoomEvent   e -> lobbyManager.createRoom(this, e.roomName());
            case JoinRoomEvent     e -> lobbyManager.joinRoom(this, e.roomId());
            case QuickMatchEvent   e -> lobbyManager.quickMatch(this);
            case LeaveRoomEvent    e -> lobbyManager.leaveRoom(this);
            case PlayerReadyEvent  e -> withRoom(r -> r.onPlayerReady(this, e.ready()));
            case PauseRequestEvent e -> withRoom(r -> r.onPauseRequest(this, e.pausing()));
            case FireArrowEvent    e -> withRoom(r -> r.onFireArrow(this, e.chargeRatio()));
            case MoveEvent         e -> withRoom(r -> r.onMove(this, e.direction(), e.pressed()));
            case AimEvent          e -> withRoom(r -> r.onAim(this, e.direction(), e.pressed()));
            case CrouchEvent       e -> withRoom(r -> r.onCrouch(this, e.crouching()));
            // Запрос реванша после окончания матча
            case RematchRequestEvent e -> withRoom(r -> r.onRematchRequest(this));
        }
    }

    private void withRoom(java.util.function.Consumer<GameRoom> action) {
        String rid = currentRoomId;
        if (rid == null) return;
        GameRoom room = lobbyManager.getRoomById(rid);
        if (room != null) action.accept(room);
    }

    // ─── Thread-safe write ────────────────────────────────────────────────────

    public void sendMessage(ServerMessage message) {
        if (out == null) return;
        try {
            synchronized (out) {
                out.writeObject(message);
                out.flush();
                out.reset();   // prevent OutOfMemoryError from object cache growth
            }
        } catch (IOException e) {
            LOG.fine("[Handler] Send failed to " + nicknameOrId() + ": " + e.getMessage());
        }
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    private void cleanup() {
        lobbyManager.handleDisconnect(this);
        try { socket.close(); } catch (IOException ignored) {}
        LOG.info("[Handler] Cleaned up: " + nicknameOrId());
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public String getPlayerId()      { return playerId; }
    public String getNickname()      { return nickname != null ? nickname : "?"; }
    public String getCurrentRoomId() { return currentRoomId; }

    public void setCurrentRoomId(String roomId) { this.currentRoomId = roomId; }

    private String nicknameOrId() {
        return nickname != null ? nickname : playerId;
    }
}
