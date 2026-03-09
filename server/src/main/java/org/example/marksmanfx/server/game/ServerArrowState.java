package org.example.marksmanfx.server.game;

import org.example.marksmanfx.common.model.ArrowDto;

/**
 * Серверное состояние стрелы: точный перенос физики из ArrowModel.
 * На каждого игрока создаётся один экземпляр, который переактивируется при выстреле.
 */
public final class ServerArrowState {

    private static final double BASE_SPEED         = 760.0;
    private static final double MIN_SPEED_MULT     = 1.0;
    private static final double MAX_SPEED_MULT     = 2.4;
    private static final double ARROW_WIDTH        = 54.0;
    private static final double ARROW_HEIGHT       = 12.0;
    private static final double BOUND_MIN_X        = -80.0;
    private static final double BOUND_MAX_X        = 1040.0;
    private static final double BOUND_MIN_Y        = -40.0;
    private static final double BOUND_MAX_Y        = 600.0;

    private final String ownerId;

    private boolean active;
    private double  x;
    private double  y;
    private double  angleDegrees;
    private double  velocityX;
    private double  velocityY;

    public ServerArrowState(String ownerId) {
        this.ownerId = ownerId;
    }

    public void activate(double startX, double startY, double angleDeg, double chargeRatio) {
        double clampedCharge = Math.max(0.0, Math.min(1.0, chargeRatio));
        double speedMult     = MIN_SPEED_MULT + (MAX_SPEED_MULT - MIN_SPEED_MULT) * clampedCharge;
        double finalSpeed    = BASE_SPEED * speedMult;
        double rad           = Math.toRadians(angleDeg);

        active       = true;
        x            = startX;
        y            = startY;
        angleDegrees = angleDeg;
        velocityX    = finalSpeed * Math.cos(rad);
        velocityY    = -finalSpeed * Math.sin(rad);
    }

    public void deactivate() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public void advance(double dt) {
        if (!active) return;
        x += velocityX * dt;
        y += velocityY * dt;
        if (x > BOUND_MAX_X || x < BOUND_MIN_X || y < BOUND_MIN_Y || y > BOUND_MAX_Y) {
            deactivate();
        }
    }

    /** Координата острия стрелы, используется для проверки столкновений. */
    public double tipX() {
        return x + Math.cos(Math.toRadians(angleDegrees)) * ARROW_WIDTH;
    }

    public double tipY() {
        return y - Math.sin(Math.toRadians(angleDegrees)) * ARROW_WIDTH;
    }

    public ArrowDto toDto() {
        return new ArrowDto(ownerId, active, x, y, angleDegrees, ARROW_WIDTH, ARROW_HEIGHT);
    }

    public String getOwnerId() { return ownerId; }
}
