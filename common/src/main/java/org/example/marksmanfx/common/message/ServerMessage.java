package org.example.marksmanfx.common.message;

import java.io.Serializable;

/**
 * Запечатанный базовый тип для всех сообщений, которые сервер рассылает клиентам.
 * Клиент делает исчерпывающий switch по этому типу, чтобы управлять UI.
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
