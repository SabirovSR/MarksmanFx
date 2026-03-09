package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Запрос на быстрый матч.
 * Сервер либо подбирает подходящую комнату, либо создаёт новую автоматически.
 */
public record QuickMatchEvent() implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}