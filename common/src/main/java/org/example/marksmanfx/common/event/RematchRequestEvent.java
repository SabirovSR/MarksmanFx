package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Голос игрока за реванш после завершения матча.
 * Когда все участники комнаты подтверждают реванш, сервер запускает новую игру.
 */
public record RematchRequestEvent() implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}