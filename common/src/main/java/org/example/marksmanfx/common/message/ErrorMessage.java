package org.example.marksmanfx.common.message;

import java.io.Serial;

/**
 * Текст ошибки или отказа в выполнении клиентского запроса.
 *
 * @param text человекочитаемое описание проблемы
 */
public record ErrorMessage(String text) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}