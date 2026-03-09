package org.example.marksmanfx.common.message;

import java.io.Serial;

/**
 * Сообщает о штатном завершении матча по условию победы.
 *
 * @param winnerId       идентификатор победителя
 * @param winnerNickname никнейм победителя
 */
public record GameOverMessage(String winnerId, String winnerNickname) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}