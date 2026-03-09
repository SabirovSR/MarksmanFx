package org.example.marksmanfx.Models;

/// Модель одной мишени
final class TargetModel {
    private final TargetType type;
    private final double x;
    private final double size;
    private final double speed;
    private final double topY;
    private final double bottomY;

    private double y;
    private int direction = 1;

    TargetModel(TargetType type, double x, double size, double speed, double topY, double bottomY) {
        this.type = type;
        this.x = x;
        this.size = size;
        this.speed = speed;
        this.topY = topY;
        this.bottomY = bottomY;
    }

    void resetToCenter() {
        this.y = (topY + bottomY - size) * 0.5;
        this.direction = 1;
    }

    /// Двигаем мишень по вертикали
    void advance(double deltaSeconds, double speedMultiplier) {
        y += direction * speed * speedMultiplier * deltaSeconds;
        if (y > bottomY - size) {
            y = bottomY - size;
            direction = -1;
        } else if (y < topY) {
            y = topY;
            direction = 1;
        }
    }

    /// Проверяем, попала ли стрела внутрь окружности мишени
    boolean containsPoint(double pointX, double pointY) {
        double radius = size * 0.5;
        double centerX = x + radius;
        double centerY = y + radius;
        double deltaX = pointX - centerX;
        double deltaY = pointY - centerY;

        return deltaX * deltaX + deltaY * deltaY <= radius * radius;
    }

    /// Возвращаем неизменяемый снимок мишени
    TargetSnapshot snapshot() {
        return new TargetSnapshot(type, x, y, size);
    }
}
