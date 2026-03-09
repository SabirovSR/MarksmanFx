package org.example.marksmanfx.common.event;

import java.io.Serial;

/** Sent immediately after TCP handshake to register the player's nickname. */
public record JoinLobbyEvent(String nickname) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
