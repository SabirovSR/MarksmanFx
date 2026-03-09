package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Key-press / key-release for archer movement.
 * direction: "UP" | "DOWN" | "LEFT" | "RIGHT"
 */
public record MoveEvent(String direction, boolean pressed) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
