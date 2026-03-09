package org.example.marksmanfx.server.state;

import org.example.marksmanfx.common.message.GameStartMessage;
import org.example.marksmanfx.common.message.RematchOfferMessage;
import org.example.marksmanfx.server.game.GameRoom;
import org.example.marksmanfx.server.network.ClientHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Матч окончен. Ждём, пока все проголосуют за реванш или покинут комнату.
 *
 * Логика реванша:
 *   — Игрок нажимает «Предложить реванш» → клиент шлёт {@link org.example.marksmanfx.common.event.RematchRequestEvent}
 *   — Сервер добавляет голос и рассылает {@link RematchOfferMessage} с актуальным списком
 *   — Когда все в комнате проголосовали → немедленно запускаем новый матч
 *   — Если кто-то уходит через {@link org.example.marksmanfx.common.event.LeaveRoomEvent}
 *     — обрабатывается в {@link GameRoom#removePlayer}
 */
public final class FinishedState implements RoomState {

    private static final Logger LOG = Logger.getLogger(FinishedState.class.getName());

    private final String winnerId;
    // LinkedHashMap сохраняет порядок голосования для отображения на клиенте
    private final Map<String, String> rematchVotes = new LinkedHashMap<>(); // playerId → nickname

    public FinishedState(String winnerId) {
        this.winnerId = winnerId;
    }

    @Override
    public RoomState onRematchRequest(ClientHandler player, GameRoom room) {
        rematchVotes.put(player.getPlayerId(), player.getNickname());

        LOG.info("[Комната " + room.getRoomId() + "] Голос за реванш: "
                + player.getNickname() + " (" + rematchVotes.size() + "/" + room.getPlayerCount() + ")");

        // Рассылаем всем актуальный список желающих реванша
        room.broadcast(new RematchOfferMessage(
                new ArrayList<>(rematchVotes.values()),
                room.getPlayerCount()));

        // Все проголосовали — немедленно перезапускаем матч
        if (rematchVotes.keySet().containsAll(room.getPlayerIds())) {
            LOG.info("[Комната " + room.getRoomId() + "] Все хотят реванш — перезапускаем матч");
            room.resetGameSession();   // останавливаем старую сессию
            room.startGameSession();   // стартуем новую с нулевыми очками
            room.broadcast(new GameStartMessage());
            return new PlayingState();
        }
        return this;
    }

    @Override
    public RoomState onPlayerDisconnect(ClientHandler player, GameRoom room) {
        // Убираем ушедшего из голосования — вдруг остальные уже все проголосовали
        rematchVotes.remove(player.getPlayerId());
        room.broadcastRoomUpdate();

        if (!room.getPlayerIds().isEmpty()
                && rematchVotes.keySet().containsAll(room.getPlayerIds())) {
            LOG.info("[Комната " + room.getRoomId() + "] Реванш подтверждён после ухода игрока");
            room.resetGameSession();
            room.startGameSession();
            room.broadcast(new GameStartMessage());
            return new PlayingState();
        }
        return this;
    }

    @Override
    public String name() { return "FINISHED"; }
}
