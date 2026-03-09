package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Клиент отправляет этот ивент, когда нажимает «Предложить реванш»
 * после окончания матча.
 * Сервер копит голоса; как только все подтверждают — запускает матч заново.
 */
public record RematchRequestEvent() implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}
