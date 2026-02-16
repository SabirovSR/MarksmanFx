package org.example.marksmanfx.Models;

final class ArrowModel {
    private final double speed;
    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double width;
    private final double height;

    private boolean active;
    private double x;
    private double y;
    private double angleDegrees;
    private double velocityX;
    private double velocityY;

    ArrowModel(double speed, double minX, double maxX, double minY, double maxY, double width, double height) {
        this.speed = speed;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.width = width;
        this.height = height;
    }

    void activate(double startX, double startY, double angleDegrees) {
        active = true;
        x = startX;
        y = startY;
        this.angleDegrees = angleDegrees;

        double angleRadians = Math.toRadians(angleDegrees);
        velocityX = speed * Math.cos(angleRadians);
        velocityY = -speed * Math.sin(angleRadians);
    }

    void deactivate() {
        active = false;
    }

    boolean isActive() {
        return active;
    }

    void advance(double deltaSeconds) {
        if (!active) {
            return;
        }

        x += velocityX * deltaSeconds;
        y += velocityY * deltaSeconds;
        if (x > maxX || x < minX || y < minY || y > maxY) {
            deactivate();
        }
    }

    double tipX() {
        return x + Math.cos(Math.toRadians(angleDegrees)) * width;
    }

    double tipY() {
        return y - Math.sin(Math.toRadians(angleDegrees)) * width;
    }

    ArrowSnapshot snapshot() {
        return new ArrowSnapshot(active, x, y, width, height, angleDegrees);
    }
}
