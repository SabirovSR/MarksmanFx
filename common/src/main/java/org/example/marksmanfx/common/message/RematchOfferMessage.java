package org.example.marksmanfx.common.message;

import java.io.Serial;
import java.util.List;

/**
 * Рассылаем всем в комнате каждый раз, когда кто-то голосует за реванш.
 * Клиент отображает актуальный список проголосовавших и ждёт остальных.
 */
public record RematchOfferMessage(
        List<String> voterNicknames,  // никнеймы тех, кто уже проголосовал
        int totalPlayers              // сколько всего игроков в комнате
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
