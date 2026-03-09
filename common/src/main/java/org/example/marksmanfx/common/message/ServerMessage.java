package org.example.marksmanfx.common.message;

import java.io.Serializable;

/**
 * Базовый тип для всех сообщений, которые сервер отправляет клиенту.
 *
 * <p>Определяет полный набор входящих сообщений для клиентского UI и сетевого слоя.</p>
 */
public sealed interface ServerMessage extends Serializable
        permits ConnectedMessage,
                LobbyStateMessage,
                RoomJoinedMessage,
                RoomUpdatedMessage,
                GameStartMessage,
                GameStateMessage,
                GameOverMessage,
                TechnicalWinMessage,
                RematchOfferMessage,
                PauseStateMessage,
                PlayerDisconnectedMessage,
                ErrorMessage {
}