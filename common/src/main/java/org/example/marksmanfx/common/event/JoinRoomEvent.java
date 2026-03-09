package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Запрос на вход в существующую комнату.
 *
 * @param roomId идентификатор комнаты
 */
public record JoinRoomEvent(String roomId) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}