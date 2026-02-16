package org.example.marksmanfx.Models;

public class GameModel {
    private static final double FIELD_WIDTH = 960;
    private static final double FIELD_HEIGHT = 560;

    private static final double ARCHER_MIN_X = 46;
    private static final double ARCHER_MAX_X = 260;
    private static final double ARCHER_MIN_Y = 165;
    private static final double ARCHER_MAX_Y = 500;

    private static final double AIM_MIN_ANGLE = -45;
    private static final double AIM_MAX_ANGLE = 45;
    private static final int MAX_LEVEL = 5;

    private final Object lock = new Object();

    private final TargetModel nearTarget = new TargetModel(TargetType.NEAR, 640, 110, 90, 36, FIELD_HEIGHT - 36);
    private final TargetModel farTarget = new TargetModel(TargetType.FAR, 770, 55, 180, 36, FIELD_HEIGHT - 36);
    private final ArrowModel arrow = new ArrowModel(760, -80, FIELD_WIDTH + 80, -40, FIELD_HEIGHT + 40, 54, 12);

    private boolean running;
    private boolean paused;
    private int score;
    private int shots;
    private double archerX;
    private double archerY;
    private double aimAngleDegrees;
    private boolean crouched;

    public GameModel() {
        resetEntities();
    }

    public void startNewGame() {
        synchronized (lock) {
            running = true;
            paused = false;
            score = 0;
            shots = 0;
            resetEntities();
        }
    }

    public void stopGame() {
        synchronized (lock) {
            running = false;
            paused = false;
            resetEntities();
        }
    }

    public void togglePause() {
        synchronized (lock) {
            if (!running) {
                return;
            }
            paused = !paused;
        }
    }

    public boolean fireArrow() {
        synchronized (lock) {
            if (!running || paused || arrow.isActive()) {
                return false;
            }

            shots++;
            arrow.activate(arrowStartX(), arrowStartY(), aimAngleDegrees);
            return true;
        }
    }

    public void moveArcher(double deltaX, double deltaY) {
        synchronized (lock) {
            archerX = clamp(archerX + deltaX, ARCHER_MIN_X, ARCHER_MAX_X);
            archerY = clamp(archerY + deltaY, ARCHER_MIN_Y, ARCHER_MAX_Y);
        }
    }

    public void aim(double deltaAngleDegrees) {
        synchronized (lock) {
            aimAngleDegrees = clamp(aimAngleDegrees + deltaAngleDegrees, AIM_MIN_ANGLE, AIM_MAX_ANGLE);
        }
    }

    public void moveArcherUp() {
        moveArcher(0, -14);
    }

    public void moveArcherDown() {
        moveArcher(0, 14);
    }

    public void moveArcherLeft() {
        moveArcher(-14, 0);
    }

    public void moveArcherRight() {
        moveArcher(14, 0);
    }

    public void aimUp() {
        aim(4);
    }

    public void aimDown() {
        aim(-4);
    }

    public void toggleCrouch() {
        synchronized (lock) {
            crouched = !crouched;
        }
    }

    public void updateTargets(double deltaSeconds) {
        synchronized (lock) {
            if (!running || paused) {
                return;
            }

            double speedMultiplier = 1.0 + (currentLevel() - 1) * 0.20;
            nearTarget.advance(deltaSeconds, speedMultiplier);
            farTarget.advance(deltaSeconds, speedMultiplier);
        }
    }

    public boolean updateArrow(double deltaSeconds) {
        synchronized (lock) {
            if (!running) {
                return false;
            }
            if (paused || !arrow.isActive()) {
                return arrow.isActive();
            }

            arrow.advance(deltaSeconds);
            if (!arrow.isActive()) {
                return false;
            }

            double tipX = arrow.tipX();
            double tipY = arrow.tipY();

            if (nearTarget.containsPoint(tipX, tipY)) {
                score += 1;
                arrow.deactivate();
                return false;
            }

            if (farTarget.containsPoint(tipX, tipY)) {
                score += 2;
                arrow.deactivate();
                return false;
            }

            return true;
        }
    }

    public GameSnapshot snapshot() {
        synchronized (lock) {
            return new GameSnapshot(
                    running,
                    paused,
                    score,
                    shots,
                    currentLevel(),
                    archerX,
                    archerY,
                    aimAngleDegrees,
                    crouched,
                    nearTarget.snapshot(),
                    farTarget.snapshot(),
                    arrow.snapshot()
            );
        }
    }

    private void resetEntities() {
        nearTarget.resetToCenter();
        farTarget.resetToCenter();
        arrow.deactivate();
        archerX = 70;
        archerY = FIELD_HEIGHT * 0.5 + 28;
        aimAngleDegrees = 0;
        crouched = false;
    }

    private double arrowStartX() {
        return archerX + 58;
    }

    private double arrowStartY() {
        double shoulderY = archerY - (crouched ? 8 : 42);
        return shoulderY + 2;
    }

    private static double clamp(double value, double minValue, double maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private int currentLevel() {
        return Math.min(MAX_LEVEL, score / 10 + 1);
    }
}
