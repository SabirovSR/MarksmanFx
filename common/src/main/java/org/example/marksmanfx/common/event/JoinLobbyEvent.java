package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Первое клиентское событие после установки TCP-соединения.
 * Регистрирует никнейм игрока и переводит соединение в состояние лобби.
 *
 * @param nickname никнейм игрока
 */
public record JoinLobbyEvent(String nickname) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}