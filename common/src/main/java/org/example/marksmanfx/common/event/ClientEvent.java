package org.example.marksmanfx.common.event;

import java.io.Serializable;

/**
 * Запечатанный базовый тип для всех сообщений, которые клиент отправляет серверу.
 * Исчерпывающий pattern-matching на сервере гарантирует, что ни один ивент
 * не останется без обработки.
 */
public sealed interface ClientEvent extends Serializable
        permits JoinLobbyEvent,
                CreateRoomEvent,
                JoinRoomEvent,
                QuickMatchEvent,
                PlayerReadyEvent,
                MoveEvent,
                AimEvent,
                CrouchEvent,
                FireArrowEvent,
                PauseRequestEvent,
                RematchRequestEvent,
                LeaveRoomEvent {
}
