package org.example.marksmanfx.server.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Исходящее сообщение всем участникам комнаты: кто выстрелил и куда.
 *
 * Мы рассылаем этот объект всем подписчикам топика /topic/game/{roomId}.
 * Пример JSON, который получают клиенты:
 * {
 *   "shooterUsername": "ArcherKing",
 *   "aimX": 450.0,
 *   "aimY": 300.0,
 *   "chargeRatio": 0.85,
 *   "timestamp": 1710000000000
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShotBroadcastMessage {

    /** Тип события — клиент использует его для маршрутизации обработки */
    private String type = "SHOT";

    /** Имя пользователя, который произвёл выстрел */
    private String shooterUsername;

    private double aimX;
    private double aimY;
    private double chargeRatio;

    /** Серверная временная метка для синхронизации клиентов */
    private long timestamp;

    public ShotBroadcastMessage(String shooterUsername, double aimX, double aimY, double chargeRatio) {
        this.shooterUsername = shooterUsername;
        this.aimX = aimX;
        this.aimY = aimY;
        this.chargeRatio = chargeRatio;
        this.timestamp = System.currentTimeMillis();
    }
}
