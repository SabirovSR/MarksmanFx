package org.example.marksmanfx.server.state;

import org.example.marksmanfx.common.message.GameStartMessage;
import org.example.marksmanfx.server.game.GameRoom;
import org.example.marksmanfx.server.network.ClientHandler;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Room is waiting for players. Transitions to PlayingState when every
 * connected player has pressed "Ready" (minimum 1 player).
 */
public final class WaitingState implements RoomState {

    private static final Logger LOG = Logger.getLogger(WaitingState.class.getName());

    private final Set<String> readyIds = new HashSet<>();

    @Override
    public RoomState onPlayerReady(ClientHandler player, boolean ready, GameRoom room) {
        String id = player.getPlayerId();
        if (ready) {
            readyIds.add(id);
        } else {
            readyIds.remove(id);
        }

        room.broadcastRoomUpdate();

        if (!room.getPlayerIds().isEmpty() && readyIds.containsAll(room.getPlayerIds())) {
            LOG.info("[Room " + room.getRoomId() + "] All players ready — starting game");
            room.startGameSession();
            room.broadcast(new GameStartMessage());
            return new PlayingState();
        }
        return this;
    }

    @Override
    public RoomState onPlayerDisconnect(ClientHandler player, GameRoom room) {
        readyIds.remove(player.getPlayerId());
        room.broadcastRoomUpdate();
        return this;
    }

    public Set<String> getReadyIds() { return readyIds; }

    @Override
    public String name() { return "WAITING"; }
}
