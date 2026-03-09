package org.example.marksmanfx.common.message;

import java.io.Serial;

/**
 * Подтверждает успешную регистрацию игрока на сервере.
 *
 * @param playerId уникальный идентификатор игрока, назначенный сервером
 * @param nickname подтверждённый никнейм игрока
 */
public record ConnectedMessage(String playerId, String nickname) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}