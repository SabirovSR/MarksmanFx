package org.example.marksmanfx.server.game.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

/**
 * Входящее сообщение от клиента: координаты прицела и сила выстрела.
 *
 * Мы получаем этот DTO из JSON payload WebSocket-сообщения.
 * Jackson автоматически десериализует JSON → ShotMessage.
 * Пример JSON от клиента:
 * {
 *   "aimX": 450.0,
 *   "aimY": 300.0,
 *   "chargeRatio": 0.85
 * }
 */
@Data
public class ShotMessage {

    /** X-координата точки прицеливания в игровом пространстве */
    private double aimX;

    /** Y-координата точки прицеливания в игровом пространстве */
    private double aimY;

    /** Сила выстрела от 0.0 (минимум) до 1.0 (полный заряд) */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double chargeRatio;
}
