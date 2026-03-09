package org.example.marksmanfx.common.message;

import java.io.Serial;

/** First message after a successful JoinLobbyEvent. Contains the server-assigned player UUID. */
public record ConnectedMessage(String playerId, String nickname) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
