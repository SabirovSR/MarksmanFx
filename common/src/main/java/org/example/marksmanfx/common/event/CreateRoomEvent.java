package org.example.marksmanfx.common.event;

import java.io.Serial;

public record CreateRoomEvent(String roomName) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
