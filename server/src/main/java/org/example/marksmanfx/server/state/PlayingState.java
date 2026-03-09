package org.example.marksmanfx.server.state;

import org.example.marksmanfx.common.message.PauseStateMessage;
import org.example.marksmanfx.common.message.TechnicalWinMessage;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.server.game.GameRoom;
import org.example.marksmanfx.server.network.ClientHandler;

import java.util.logging.Logger;

/**
 * Матч идёт. Перенаправляем ввод игроков в {@code ServerGameSession}
 * и реагируем на запросы паузы и дисконнекты.
 */
public final class PlayingState implements RoomState {

    private static final Logger LOG = Logger.getLogger(PlayingState.class.getName());

    @Override
    public RoomState onPauseRequest(ClientHandler player, boolean pausing, GameRoom room) {
        if (!pausing) return this;

        LOG.info("[Комната " + room.getRoomId() + "] Пауза запрошена игроком: " + player.getNickname());

        // Уведомляем всех о запросе паузы и переходим в промежуточное состояние
        room.broadcast(new PauseStateMessage(GamePhase.PAUSE_REQUESTED,
                player.getPlayerId(), player.getNickname()));
        return new PauseRequestedState(player.getPlayerId(), player.getNickname());
    }

    @Override
    public RoomState onPlayerDisconnect(ClientHandler player, GameRoom room) {
        // Удаляем стрелу и позицию отключившегося игрока из сессии
        room.getSession().removePlayer(player.getPlayerId());

        LOG.info("[Комната " + room.getRoomId() + "] " + player.getNickname()
                + " отключился во время игры. Осталось игроков: " + room.getPlayerCount());

        if (room.getPlayerCount() <= 1) {
            room.getSession().stop();

            // Fix 2: Если остался один игрок — отправляем TechnicalWinMessage
            // вместо обычного GameOverMessage, чтобы клиент понял причину победы
            room.getPlayerIds().stream().findFirst().ifPresent(winnerId -> {
                String winnerNick = room.getNickname(winnerId);
                LOG.info("[Комната " + room.getRoomId() + "] Техническая победа у: " + winnerNick
                        + " (соперник " + player.getNickname() + " ушёл)");
                room.broadcast(new TechnicalWinMessage(winnerId, winnerNick, player.getNickname()));
                room.transitionToState(new FinishedState(winnerId));
            });
            return new FinishedState(null);
        }
        return this;
    }

    // Передаём игровые команды напрямую в сессию
    @Override
    public RoomState onFireArrow(ClientHandler player, double chargeRatio, GameRoom room) {
        room.getSession().playerFireArrow(player.getPlayerId(), chargeRatio);
        return this;
    }

    @Override
    public RoomState onMove(ClientHandler player, String direction, boolean pressed, GameRoom room) {
        room.getSession().playerMove(player.getPlayerId(), direction, pressed);
        return this;
    }

    @Override
    public RoomState onAim(ClientHandler player, String direction, boolean pressed, GameRoom room) {
        room.getSession().playerAim(player.getPlayerId(), direction, pressed);
        return this;
    }

    @Override
    public RoomState onCrouch(ClientHandler player, boolean crouching, GameRoom room) {
        room.getSession().playerCrouch(player.getPlayerId(), crouching);
        return this;
    }

    @Override
    public String name() { return "PLAYING"; }
}
