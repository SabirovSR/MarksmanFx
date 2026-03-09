package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Запрос на создание новой комнаты в лобби.
 *
 * @param roomName отображаемое имя новой комнаты
 */
public record CreateRoomEvent(String roomName) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}