package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Сериализуемое состояние стрелы в конкретный момент времени.
 *
 * @param ownerId      идентификатор игрока-владельца стрелы
 * @param active       находится ли стрела в полёте
 * @param x            координата X хвоста стрелы
 * @param y            координата Y хвоста стрелы
 * @param angleDegrees угол полёта стрелы в градусах
 * @param width        визуальная длина стрелы
 * @param height       визуальная толщина стрелы
 */
public record ArrowDto(
        String ownerId,
        boolean active,
        double x,
        double y,
        double angleDegrees,
        double width,
        double height
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}