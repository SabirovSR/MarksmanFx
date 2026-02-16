package org.example.marksmanfx.Models;

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

    boolean containsPoint(double pointX, double pointY) {
        return pointX >= x && pointX <= x + size && pointY >= y && pointY <= y + size;
    }

    TargetSnapshot snapshot() {
        return new TargetSnapshot(type, x, y, size);
    }
}
