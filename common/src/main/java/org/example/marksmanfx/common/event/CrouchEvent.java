package org.example.marksmanfx.common.event;

import java.io.Serial;

public record CrouchEvent(boolean crouching) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
