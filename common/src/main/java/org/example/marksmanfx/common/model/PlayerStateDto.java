package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Полное клиентское представление игрока внутри игрового кадра.
 *
 * @param playerId        идентификатор игрока
 * @param nickname        никнейм игрока
 * @param archerX         координата X лучника
 * @param archerY         координата Y лучника
 * @param aimAngleDegrees угол прицеливания в градусах
 * @param crouched        находится ли игрок в приседе
 * @param score           текущий счёт игрока
 */
public record PlayerStateDto(
        String playerId,
        String nickname,
        double archerX,
        double archerY,
        double aimAngleDegrees,
        boolean crouched,
        int score
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}