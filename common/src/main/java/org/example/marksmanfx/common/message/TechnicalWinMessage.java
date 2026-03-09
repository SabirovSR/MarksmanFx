package org.example.marksmanfx.common.message;

import java.io.Serial;

/**
 * Отправляем оставшимся игрокам, когда соперник внезапно покидает матч
 * (обрыв соединения или закрытие окна).
 * Отличается от {@link GameOverMessage}: клиент показывает специальный
 * баннер «Игрок X сбежал — вы побеждаете технически».
 */
public record TechnicalWinMessage(
        String winnerId,
        String winnerNickname,
        String disconnectedNickname  // имя того, кто ушёл
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
