package org.example.marksmanfx.common.message;

import java.io.Serial;

/**
 * Сигнализирует о старте матча после того, как все игроки в комнате готовы.
 */
public record GameStartMessage() implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}