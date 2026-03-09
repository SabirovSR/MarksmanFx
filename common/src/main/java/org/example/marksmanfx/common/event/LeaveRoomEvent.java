package org.example.marksmanfx.common.event;

import java.io.Serial;

public record LeaveRoomEvent() implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
