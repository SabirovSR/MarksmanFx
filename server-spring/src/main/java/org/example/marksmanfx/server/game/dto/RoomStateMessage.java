package org.example.marksmanfx.server.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.marksmanfx.server.game.model.GamePhase;
import org.example.marksmanfx.server.game.model.RoomParticipant;

import java.util.List;

/**
 * Полный снимок состояния комнаты, который рассылается всем участникам.
 *
 * Мы отправляем его при любом изменении состава комнаты: вход/выход игрока,
 * смена роли зрителя на игрока, готовность игрока и т.д.
 *
 * Пример JSON:
 * {
 *   "type": "ROOM_STATE",
 *   "roomId": "A1B2C3D4",
 *   "roomName": "Арена Льва",
 *   "phase": "LOBBY",
 *   "participants": [
 *     { "username": "ArcherKing", "role": "PLAYER", "ready": true },
 *     { "username": "Spectator99", "role": "SPECTATOR", "ready": false }
 *   ]
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomStateMessage {

    private String type = "ROOM_STATE";
    private String roomId;
    private String roomName;
    private GamePhase phase;
    private List<RoomParticipant> participants;
}
