package org.example.marksmanfx.common.message;

import java.io.Serial;

public record GameOverMessage(String winnerId, String winnerNickname) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
