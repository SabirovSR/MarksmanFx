package org.example.marksmanfx.common.message;

import java.io.Serial;

/**
 * Сообщение о технической победе, если соперник покинул матч до штатного завершения.
 *
 * @param winnerId             идентификатор победителя
 * @param winnerNickname       никнейм победителя
 * @param disconnectedNickname никнейм игрока, чьё отключение привело к завершению матча
 */
public record TechnicalWinMessage(
        String winnerId,
        String winnerNickname,
        String disconnectedNickname
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}