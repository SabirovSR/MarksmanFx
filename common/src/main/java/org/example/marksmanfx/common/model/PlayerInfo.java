package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Краткая информация об игроке в составе комнаты.
 *
 * @param playerId идентификатор игрока
 * @param nickname никнейм игрока
 * @param ready    признак готовности к старту или продолжению
 */
public record PlayerInfo(
        String playerId,
        String nickname,
        boolean ready
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}