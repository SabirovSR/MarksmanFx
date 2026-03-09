package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.PlayerInfo;
import org.example.marksmanfx.common.model.RoomInfo;

import java.io.Serial;
import java.util.List;

/** Sent to the joining client (and re-sent to existing members) after a successful room join. */
public record RoomJoinedMessage(
        RoomInfo roomInfo,
        List<PlayerInfo> players,
        String localPlayerId
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
