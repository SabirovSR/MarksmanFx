package org.example.marksmanfx.common.message;

import java.io.Serial;

/**
 * Уведомляет клиентов комнаты об отключении конкретного игрока.
 *
 * @param playerId идентификатор отключившегося игрока
 * @param nickname его никнейм
 */
public record PlayerDisconnectedMessage(String playerId, String nickname) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}