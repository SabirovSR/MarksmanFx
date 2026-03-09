package org.example.marksmanfx.common.message;

import java.io.Serial;

public record PlayerDisconnectedMessage(String playerId, String nickname) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
