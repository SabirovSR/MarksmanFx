package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Client computes charge ratio locally for UI feedback, then sends it.
 * Server uses the authoritative archer position + this ratio to spawn the arrow.
 */
public record FireArrowEvent(double chargeRatio) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
