package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Событие начала или окончания изменения угла прицеливания.
 *
 * @param direction направление изменения угла: {@code "UP"} или {@code "DOWN"}
 * @param pressed   {@code true}, если клавиша нажата, иначе отпущена
 */
public record AimEvent(String direction, boolean pressed) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}