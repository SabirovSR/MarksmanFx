package org.example.marksmanfx.server.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Персональное событие ошибки: в комнате нет свободных мест для игроков.
 *
 * Мы отправляем его ТОЛЬКО конкретному зрителю, который пытался стать игроком,
 * через личный канал /user/queue/errors. Остальные участники комнаты не получают это сообщение.
 *
 * Клиент (JavaFX / Vue.js) должен показать модальное окно с текстом из поля message.
 *
 * Пример JSON:
 * {
 *   "type": "ROOM_FULL",
 *   "message": "К сожалению, свободных мест для игроков нет"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomFullEvent {

    private String type = "ROOM_FULL";
    private String message;

    public RoomFullEvent(String message) {
        this.message = message;
    }
}
