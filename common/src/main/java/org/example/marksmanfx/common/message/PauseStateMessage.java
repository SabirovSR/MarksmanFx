package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.GamePhase;

import java.io.Serial;

/**
 * Carries the current pause phase and who triggered it.
 * requesterId is null when the phase is PLAYING (pause lifted).
 */
public record PauseStateMessage(
        GamePhase phase,
        String requesterId,
        String requesterNickname
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
