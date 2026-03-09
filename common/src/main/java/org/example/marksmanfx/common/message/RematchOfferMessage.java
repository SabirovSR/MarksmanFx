package org.example.marksmanfx.common.message;

import java.io.Serial;
import java.util.List;

/**
 * Текущее состояние голосования за реванш.
 *
 * @param voterNicknames никнеймы игроков, уже проголосовавших за реванш
 * @param totalPlayers   общее число игроков в комнате, участвующих в голосовании
 */
public record RematchOfferMessage(
        List<String> voterNicknames,
        int totalPlayers
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}