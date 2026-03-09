package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.PlayerInfo;
import org.example.marksmanfx.common.model.RoomInfo;

import java.io.Serial;
import java.util.List;

/**
 * Подтверждает успешный вход игрока в комнату.
 *
 * @param roomInfo      агрегированная информация о комнате
 * @param players       текущий список игроков в комнате
 * @param localPlayerId идентификатор локального игрока для клиента-получателя
 */
public record RoomJoinedMessage(
        RoomInfo roomInfo,
        List<PlayerInfo> players,
        String localPlayerId
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}