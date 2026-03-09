package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.PlayerInfo;
import org.example.marksmanfx.common.model.RoomInfo;

import java.io.Serial;
import java.util.List;

/** Sent to every player inside a room when its roster or ready-state changes. */
public record RoomUpdatedMessage(
        RoomInfo roomInfo,
        List<PlayerInfo> players
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
