package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Управляет голосованием за паузу.
 *
 * @param pausing {@code true} — запросить или подтвердить паузу;
 *                {@code false} — снять голос или отменить запрос
 */
public record PauseRequestEvent(boolean pausing) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}