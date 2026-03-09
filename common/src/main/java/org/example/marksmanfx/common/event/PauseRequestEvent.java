package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * pausing=true  → request / confirm pause
 * pausing=false → cancel pause vote (requester only)
 */
public record PauseRequestEvent(boolean pausing) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
