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
 * Один экземпляр на каждого подключённого клиента.
 * Работает в отдельном потоке: читает ClientEvent из сокета и
 * передаёт их в LobbyManager или GameRoom.
 * Отправка сериализуется через {@code synchronized} по выходному потоку.
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
        this.socket       = socket;
        this.lobbyManager = lobbyManager;
    }

    @Override
    public void run() {
        try {
            // Сначала создаём ObjectOutputStream, чтобы избежать взаимной блокировки.
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());

            LOG.info("[Обработчик] Новое подключение: " + socket.getRemoteSocketAddress()
                    + " (id=" + playerId + ")");

            // Первым сообщением должен быть JoinLobbyEvent.
            Object first = in.readObject();
            if (!(first instanceof JoinLobbyEvent join)) {
                sendMessage(new ErrorMessage("Первым сообщением должен быть JoinLobbyEvent."));
                return;
            }

            nickname = join.nickname().trim();
            if (nickname.isEmpty()) {
                sendMessage(new ErrorMessage("Никнейм не может быть пустым."));
                return;
            }

            LOG.info("[Обработчик] Зарегистрирован игрок " + nickname + " (id=" + playerId + ")");
            sendMessage(new ConnectedMessage(playerId, nickname));
            lobbyManager.addLobbyClient(this);

            // Основной цикл чтения сообщений.
            while (!socket.isClosed()) {
                Object obj = in.readObject();
                if (obj instanceof ClientEvent event) {
                    dispatch(event);
                }
            }

        } catch (IOException e) {
            LOG.info("[Обработчик] " + nicknameOrId() + " отключился: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            LOG.warning("[Обработчик] От " + nicknameOrId() + " получен объект неизвестного класса: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // Разбор входящих событий.
    private void dispatch(ClientEvent event) {
        switch (event) {
            case JoinLobbyEvent e      -> sendMessage(new ErrorMessage("Вы уже зарегистрированы."));
            case CreateRoomEvent e     -> lobbyManager.createRoom(this, e.roomName());
            case JoinRoomEvent e       -> lobbyManager.joinRoom(this, e.roomId());
            case QuickMatchEvent e     -> lobbyManager.quickMatch(this);
            case LeaveRoomEvent e      -> lobbyManager.leaveRoom(this);
            case PlayerReadyEvent e    -> withRoom(r -> r.onPlayerReady(this, e.ready()));
            case PauseRequestEvent e   -> withRoom(r -> r.onPauseRequest(this, e.pausing()));
            case FireArrowEvent e      -> withRoom(r -> r.onFireArrow(this, e.chargeRatio()));
            case MoveEvent e           -> withRoom(r -> r.onMove(this, e.direction(), e.pressed()));
            case AimEvent e            -> withRoom(r -> r.onAim(this, e.direction(), e.pressed()));
            case CrouchEvent e         -> withRoom(r -> r.onCrouch(this, e.crouching()));
            case RematchRequestEvent e -> withRoom(r -> r.onRematchRequest(this));
        }
    }

    private void withRoom(java.util.function.Consumer<GameRoom> action) {
        String rid = currentRoomId;
        if (rid == null) return;
        GameRoom room = lobbyManager.getRoomById(rid);
        if (room != null) action.accept(room);
    }

    // Потокобезопасная отправка.
    public void sendMessage(ServerMessage message) {
        if (out == null) return;
        try {
            synchronized (out) {
                out.writeObject(message);
                out.flush();
                out.reset();   // Не даём кэшу ObjectOutputStream бесконечно расти.
            }
        } catch (IOException e) {
            LOG.fine("[Обработчик] Не удалось отправить сообщение игроку " + nicknameOrId() + ": " + e.getMessage());
        }
    }

    // Очистка ресурсов.
    private void cleanup() {
        lobbyManager.handleDisconnect(this);
        try { socket.close(); } catch (IOException ignored) {}
        LOG.info("[Обработчик] Очистка завершена: " + nicknameOrId());
    }

    // Методы доступа.
    public String getPlayerId()      { return playerId; }
    public String getNickname()      { return nickname != null ? nickname : "?"; }
    public String getCurrentRoomId() { return currentRoomId; }

    public void setCurrentRoomId(String roomId) { this.currentRoomId = roomId; }

    private String nicknameOrId() {
        return nickname != null ? nickname : playerId;
    }
}
