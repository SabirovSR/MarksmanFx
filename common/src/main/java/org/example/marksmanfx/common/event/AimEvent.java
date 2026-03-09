package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Key-press / key-release for aim rotation.
 * direction: "UP" | "DOWN"
 */
public record AimEvent(String direction, boolean pressed) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
