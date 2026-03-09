package org.example.marksmanfx.server.state;

import org.example.marksmanfx.common.message.PauseStateMessage;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.server.game.GameRoom;
import org.example.marksmanfx.server.network.ClientHandler;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Один из игроков запросил паузу, и теперь состояние ждёт подтверждения от остальных.
 * Если инициатор отменяет запрос ({@code pausing=false}), голосование отменяется.
 * Когда подтверждают все участники, сессия ставится на паузу и выполняется переход в {@link PausedState}.
 */
public final class PauseRequestedState implements RoomState {

    private static final Logger LOG = Logger.getLogger(PauseRequestedState.class.getName());

    private final String      requesterId;
    private final String      requesterNickname;
    private final Set<String> confirmedIds = new HashSet<>();

    public PauseRequestedState(String requesterId, String requesterNickname) {
        this.requesterId       = requesterId;
        this.requesterNickname = requesterNickname;
        confirmedIds.add(requesterId);
    }

    @Override
    public RoomState onPauseRequest(ClientHandler player, boolean pausing, GameRoom room) {
        String id = player.getPlayerId();

        if (!pausing) {
            // Инициатор отменил запрос или игрок снял своё подтверждение.
            confirmedIds.remove(id);
            if (id.equals(requesterId)) {
                LOG.info("[Комната " + room.getRoomId() + "] Инициатор " + player.getNickname() + " отменил запрос паузы");
                room.broadcast(new PauseStateMessage(GamePhase.PLAYING, null, null));
                return new PlayingState();
            }
            return this;
        }

        confirmedIds.add(id);
        LOG.info("[Комната " + room.getRoomId() + "] Пауза подтверждена игроком " + player.getNickname()
                + " (" + confirmedIds.size() + "/" + room.getPlayerCount() + ")");

        if (confirmedIds.containsAll(room.getPlayerIds())) {
            LOG.info("[Комната " + room.getRoomId() + "] Все подтвердили паузу, игра остановлена");
            room.getSession().pause();
            room.broadcast(new PauseStateMessage(GamePhase.PAUSED, requesterId, requesterNickname));
            return new PausedState(requesterId, requesterNickname);
        }
        return this;
    }

    @Override
    public RoomState onPlayerDisconnect(ClientHandler player, GameRoom room) {
        confirmedIds.remove(player.getPlayerId());
        room.getSession().removePlayer(player.getPlayerId());

        if (room.getPlayerCount() == 0) return new WaitingState();

        // Повторно проверяем: возможно, отключившийся был последним неподтвердившим игроком.
        if (confirmedIds.containsAll(room.getPlayerIds())) {
            LOG.info("[Комната " + room.getRoomId() + "] Пауза подтверждена после отключения игрока");
            room.getSession().pause();
            room.broadcast(new PauseStateMessage(GamePhase.PAUSED, requesterId, requesterNickname));
            return new PausedState(requesterId, requesterNickname);
        }
        return this;
    }

    @Override
    public String name() { return "PAUSE_REQUESTED"; }
}
