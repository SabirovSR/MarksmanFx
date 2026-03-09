package org.example.marksmanfx.common.event;

import java.io.Serializable;

/**
 * Базовый тип для всех событий, которые клиент отправляет серверу.
 *
 * <p>Используется как единая точка входа для pattern matching на сервере
 * и задаёт замкнутый набор допустимых клиентских команд.</p>
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