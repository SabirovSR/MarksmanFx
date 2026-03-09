package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Toggling ready state in WAITING/FINISHED phase,
 * or confirming resume in PAUSED phase.
 */
public record PlayerReadyEvent(boolean ready) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
