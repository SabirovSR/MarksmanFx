package org.example.marksmanfx.server.game.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Участник игровой комнаты: игрок или зритель.
 *
 * Мы храним эту запись в GameRoom и сериализуем её в JSON при рассылке RoomStateMessage.
 * sessionId — это WebSocket session ID, который Spring присваивает при подключении.
 * Он нужен нам для адресной рассылки через SimpMessagingTemplate.convertAndSendToUser().
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomParticipant {

    /** Имя пользователя из JWT-токена (Principal.getName()) */
    private String username;

    /** WebSocket session ID для персональной рассылки */
    private String sessionId;

    /** Роль: PLAYER или SPECTATOR */
    private PlayerRole role;

    /** Флаг готовности (актуален только для PLAYER в фазе LOBBY) */
    private boolean ready;

    public boolean isPlayer() {
        return role == PlayerRole.PLAYER;
    }

    public boolean isSpectator() {
        return role == PlayerRole.SPECTATOR;
    }
}
