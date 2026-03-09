package org.example.marksmanfx.server.state;

import org.example.marksmanfx.common.message.PauseStateMessage;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.server.game.GameRoom;
import org.example.marksmanfx.server.network.ClientHandler;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * One player has requested a pause; waiting for all others to confirm.
 * If the requester cancels (pausing=false), the vote is cancelled.
 * When everyone has confirmed, the session is paused → PausedState.
 */
public final class PauseRequestedState implements RoomState {

    private static final Logger LOG = Logger.getLogger(PauseRequestedState.class.getName());

    private final String       requesterId;
    private final String       requesterNickname;
    private final Set<String>  confirmedIds = new HashSet<>();

    public PauseRequestedState(String requesterId, String requesterNickname) {
        this.requesterId       = requesterId;
        this.requesterNickname = requesterNickname;
        confirmedIds.add(requesterId);
    }

    @Override
    public RoomState onPauseRequest(ClientHandler player, boolean pausing, GameRoom room) {
        String id = player.getPlayerId();

        if (!pausing) {
            // Requester cancels — or any player withdraws confirmation
            confirmedIds.remove(id);
            if (id.equals(requesterId)) {
                LOG.info("[Room " + room.getRoomId() + "] Pause cancelled by requester " + player.getNickname());
                room.broadcast(new PauseStateMessage(GamePhase.PLAYING, null, null));
                return new PlayingState();
            }
            return this;
        }

        confirmedIds.add(id);
        LOG.info("[Room " + room.getRoomId() + "] Pause confirmed by " + player.getNickname()
                + " (" + confirmedIds.size() + "/" + room.getPlayerCount() + ")");

        if (confirmedIds.containsAll(room.getPlayerIds())) {
            LOG.info("[Room " + room.getRoomId() + "] All confirmed — game paused");
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

        // Re-check: maybe the disconnect was the last unconfirmed player
        if (confirmedIds.containsAll(room.getPlayerIds())) {
            LOG.info("[Room " + room.getRoomId() + "] Pause confirmed after disconnect");
            room.getSession().pause();
            room.broadcast(new PauseStateMessage(GamePhase.PAUSED, requesterId, requesterNickname));
            return new PausedState(requesterId, requesterNickname);
        }
        return this;
    }

    @Override
    public String name() { return "PAUSE_REQUESTED"; }
}
