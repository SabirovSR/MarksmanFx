package org.example.marksmanfx.server.game;

import org.example.marksmanfx.common.model.TargetDto;

/**
 * Server-side mutable target — exact physics port from TargetModel.
 * x and size are fixed; y bounces between topY and bottomY − size.
 */
public final class ServerTargetState {

    private final double x;
    private final double size;
    private final double baseSpeed;
    private final double topY;
    private final double bottomY;
    private final int points;

    private double y;
    private int direction = 1;

    public ServerTargetState(double x, double size, double baseSpeed,
                             double topY, double bottomY, int points) {
        this.x         = x;
        this.size      = size;
        this.baseSpeed = baseSpeed;
        this.topY      = topY;
        this.bottomY   = bottomY;
        this.points    = points;
        resetToCenter();
    }

    public void resetToCenter() {
        y         = (topY + bottomY - size) * 0.5;
        direction = 1;
    }

    public void advance(double dt, double speedMultiplier) {
        y += direction * baseSpeed * speedMultiplier * dt;
        if (y > bottomY - size) {
            y         = bottomY - size;
            direction = -1;
        } else if (y < topY) {
            y         = topY;
            direction = 1;
        }
    }

    public boolean containsPoint(double px, double py) {
        double radius  = size * 0.5;
        double centerX = x + radius;
        double centerY = y + radius;
        double dx      = px - centerX;
        double dy      = py - centerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    public TargetDto toDto() {
        return new TargetDto(x, y, size, points);
    }

    public double getX()    { return x; }
    public double getY()    { return y; }
    public double getSize() { return size; }
}
