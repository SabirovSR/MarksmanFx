package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Изменяет состояние приседания локального игрока.
 *
 * @param crouching {@code true}, если игрок должен присесть
 */
public record CrouchEvent(boolean crouching) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}