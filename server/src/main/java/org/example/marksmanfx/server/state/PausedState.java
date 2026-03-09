package org.example.marksmanfx.server.state;

import org.example.marksmanfx.common.message.PauseStateMessage;
import org.example.marksmanfx.common.message.TechnicalWinMessage;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.server.game.GameRoom;
import org.example.marksmanfx.server.network.ClientHandler;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Игра полностью на паузе. Все игроки должны нажать «Готов продолжить»,
 * чтобы матч возобновился.
 */
public final class PausedState implements RoomState {

    private static final Logger LOG = Logger.getLogger(PausedState.class.getName());

    private final String      requesterId;
    private final String      requesterNickname;
    // Собираем голоса тех, кто готов продолжать
    private final Set<String> resumeVotes = new HashSet<>();

    public PausedState(String requesterId, String requesterNickname) {
        this.requesterId       = requesterId;
        this.requesterNickname = requesterNickname;
    }

    @Override
    public RoomState onPlayerReady(ClientHandler player, boolean ready, GameRoom room) {
        String id = player.getPlayerId();
        if (ready) resumeVotes.add(id);
        else       resumeVotes.remove(id);

        LOG.info("[Комната " + room.getRoomId() + "] Голос за продолжение от "
                + player.getNickname() + " (" + resumeVotes.size() + "/" + room.getPlayerCount() + ")");

        // Когда все проголосовали — снимаем паузу и возобновляем игру
        if (resumeVotes.containsAll(room.getPlayerIds())) {
            LOG.info("[Комната " + room.getRoomId() + "] Все готовы — игра возобновляется");
            room.getSession().resume();
            room.broadcast(new PauseStateMessage(GamePhase.PLAYING, null, null));
            return new PlayingState();
        }
        return this;
    }

    @Override
    public RoomState onPlayerDisconnect(ClientHandler player, GameRoom room) {
        resumeVotes.remove(player.getPlayerId());
        room.getSession().removePlayer(player.getPlayerId());

        if (room.getPlayerCount() == 0) return new WaitingState();

        // Fix 2: Если остался один — техническая победа
        if (room.getPlayerCount() == 1) {
            room.getSession().stop();
            String winnerId   = room.getPlayerIds().get(0);
            String winnerNick = room.getNickname(winnerId);
            LOG.info("[Комната " + room.getRoomId() + "] Техническая победа у: " + winnerNick
                    + " (соперник " + player.getNickname() + " ушёл во время паузы)");
            room.broadcast(new TechnicalWinMessage(winnerId, winnerNick, player.getNickname()));
            return new FinishedState(winnerId);
        }

        // Если ушёл последний неголосовавший — все оставшиеся уже готовы
        if (resumeVotes.containsAll(room.getPlayerIds())) {
            room.getSession().resume();
            room.broadcast(new PauseStateMessage(GamePhase.PLAYING, null, null));
            return new PlayingState();
        }
        return this;
    }

    @Override
    public String name() { return "PAUSED"; }
}
