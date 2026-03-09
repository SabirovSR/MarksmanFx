package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Запрос на выход из текущей комнаты и возврат в лобби.
 */
public record LeaveRoomEvent() implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}