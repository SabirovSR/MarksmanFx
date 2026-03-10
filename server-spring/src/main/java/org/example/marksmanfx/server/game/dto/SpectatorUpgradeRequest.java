package org.example.marksmanfx.server.game.dto;

import lombok.Data;

/**
 * Запрос от зрителя на смену роли: зритель хочет стать игроком.
 *
 * Мы получаем этот DTO, когда зритель нажимает кнопку "Стать игроком" в лобби.
 * Сервер проверяет наличие свободного слота и либо повышает роль,
 * либо отправляет RoomFullEvent обратно этому конкретному клиенту.
 */
@Data
public class SpectatorUpgradeRequest {

    /** ID комнаты, в которой зритель хочет стать игроком */
    private String roomId;
}
