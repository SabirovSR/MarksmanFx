package org.example.marksmanfx.common.event;

import java.io.Serial;

/** Server finds the first non-full room (or creates one) and places the player there. */
public record QuickMatchEvent() implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
