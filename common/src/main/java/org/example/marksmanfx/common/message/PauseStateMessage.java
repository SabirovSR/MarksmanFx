package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.GamePhase;

import java.io.Serial;

/**
 * Отдельно описывает состояние паузы и голосования за неё.
 *
 * @param phase             одна из фаз, связанных с паузой или возвратом в игру
 * @param requesterId       идентификатор игрока, инициировавшего паузу; может быть {@code null}
 * @param requesterNickname никнейм инициатора паузы; может быть {@code null}
 */
public record PauseStateMessage(
        GamePhase phase,
        String requesterId,
        String requesterNickname
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}