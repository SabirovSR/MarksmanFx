package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Состояние движущейся мишени в игровом кадре.
 *
 * @param x      координата X левого верхнего угла мишени
 * @param y      координата Y левого верхнего угла мишени
 * @param size   диаметр мишени
 * @param points количество очков за попадание
 */
public record TargetDto(
        double x,
        double y,
        double size,
        int points
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}