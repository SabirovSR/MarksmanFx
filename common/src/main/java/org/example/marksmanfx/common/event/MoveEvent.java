package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Событие начала или окончания перемещения лучника.
 *
 * @param direction направление движения: {@code "UP"}, {@code "DOWN"}, {@code "LEFT"}, {@code "RIGHT"}
 * @param pressed   {@code true}, если клавиша нажата, иначе отпущена
 */
public record MoveEvent(String direction, boolean pressed) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}