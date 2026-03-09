package org.example.marksmanfx.common.event;

import java.io.Serial;

/**
 * Команда на выстрел с рассчитанной на клиенте силой натяжения.
 *
 * <p>Сервер использует этот коэффициент как входной параметр,
 * но сам остаётся авторитетным источником позиции лучника и момента спавна стрелы.</p>
 *
 * @param chargeRatio нормализованная сила выстрела в диапазоне от {@code 0.0} до {@code 1.0}
 */
public record FireArrowEvent(double chargeRatio) implements ClientEvent {
    @Serial private static final long serialVersionUID = 1L;
}