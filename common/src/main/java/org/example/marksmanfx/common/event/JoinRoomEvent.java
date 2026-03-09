package org.example.marksmanfx.common.event;

import java.io.Serial;

public record JoinRoomEvent(String roomId) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
