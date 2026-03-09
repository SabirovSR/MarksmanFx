package org.example.marksmanfx.common.message;

import java.io.Serial;

/** Signals that all players are ready and the match has begun. */
public record GameStartMessage() implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
