package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

public record RoomInfo(
        String roomId,
        String roomName,
        int playerCount,
        int maxPlayers,
        GamePhase phase
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
