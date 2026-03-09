package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.RoomInfo;

import java.io.Serial;
import java.util.List;

/**
 * Текущее состояние лобби.
 * Отправляется всем клиентам в лобби при любом изменении списка комнат.
 *
 * @param rooms список видимых комнат
 */
public record LobbyStateMessage(List<RoomInfo> rooms) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}