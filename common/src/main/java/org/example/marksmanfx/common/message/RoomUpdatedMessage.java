package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.PlayerInfo;
import org.example.marksmanfx.common.model.RoomInfo;

import java.io.Serial;
import java.util.List;

/**
 * Сообщает об изменении состава комнаты или статусов готовности игроков.
 *
 * @param roomInfo обновлённое агрегированное состояние комнаты
 * @param players  обновлённый список игроков
 */
public record RoomUpdatedMessage(
        RoomInfo roomInfo,
        List<PlayerInfo> players
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}