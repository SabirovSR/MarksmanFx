package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Универсальное событие подтверждения готовности.
 *
 * <p>Используется в нескольких фазах: перед стартом матча,
 * при голосовании за продолжение после паузы и как подтверждение действия после завершения игры.</p>
 *
 * @param ready текущее состояние готовности игрока
 */
public record PlayerReadyEvent(boolean ready) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}