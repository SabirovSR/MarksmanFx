package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Агрегированная информация о комнате для лобби и экрана ожидания.
 *
 * @param roomId      уникальный идентификатор комнаты
 * @param roomName    отображаемое имя комнаты
 * @param playerCount текущее количество игроков
 * @param maxPlayers  максимальная вместимость комнаты
 * @param phase       текущая фаза комнаты
 */
public record RoomInfo(
        String roomId,
        String roomName,
        int playerCount,
        int maxPlayers,
        GamePhase phase
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}