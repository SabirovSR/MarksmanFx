package org.example.marksmanfx.server.game;

import org.example.marksmanfx.common.model.PlayerStateDto;

/**
 * Authoritative per-player state maintained by the server.
 * Input flags are set by ClientHandler threads and consumed by the game loop thread.
 */
public final class ServerPlayerState {

    private static final double FIELD_WIDTH   = 960.0;
    private static final double FIELD_HEIGHT  = 560.0;
    private static final double ARCHER_MIN_X  = 46.0;
    private static final double ARCHER_MAX_X  = 260.0;
    private static final double ARCHER_MIN_Y  = 165.0;
    private static final double ARCHER_MAX_Y  = 500.0;
    private static final double AIM_MIN       = -45.0;
    private static final double AIM_MAX       =  45.0;
    private static final double MOVE_SPEED    = 237.5;
    private static final double AIM_SPEED     = 84.375;

    public final String playerId;
    public final String nickname;

    // Position / orientation — written by game loop only
    private double archerX;
    private double archerY;
    private double aimAngleDegrees;
    private boolean crouched;
    private int score;

    // Input flags — written by ClientHandler threads, read by game loop
    public volatile boolean moveUp;
    public volatile boolean moveDown;
    public volatile boolean moveLeft;
    public volatile boolean moveRight;
    public volatile boolean aimUp;
    public volatile boolean aimDown;

    public ServerPlayerState(String playerId, String nickname) {
        this.playerId = playerId;
        this.nickname = nickname;
        reset();
    }

    public void reset() {
        archerX        = 70.0;
        archerY        = FIELD_HEIGHT * 0.5 + 28.0;
        aimAngleDegrees = 0.0;
        crouched       = false;
        score          = 0;
        moveUp = moveDown = moveLeft = moveRight = aimUp = aimDown = false;
    }

    /** Called once per server tick by the game-loop thread. */
    public void applyInput(double dt) {
        double dx = (moveRight ? 1 : 0) - (moveLeft  ? 1 : 0);
        double dy = (moveDown  ? 1 : 0) - (moveUp    ? 1 : 0);
        double da = (aimUp     ? 1 : 0) - (aimDown   ? 1 : 0);

        archerX        = clamp(archerX + dx * MOVE_SPEED * dt, ARCHER_MIN_X, ARCHER_MAX_X);
        archerY        = clamp(archerY + dy * MOVE_SPEED * dt, ARCHER_MIN_Y, ARCHER_MAX_Y);
        aimAngleDegrees = clamp(aimAngleDegrees + da * AIM_SPEED * dt, AIM_MIN, AIM_MAX);
    }

    public void toggleCrouch() {
        crouched = !crouched;
    }

    public void addScore(int delta) {
        score += delta;
    }

    /** X coordinate where the arrow originates (bow hand position). */
    public double arrowStartX() {
        return archerX + 58.0;
    }

    /** Y coordinate where the arrow originates, adjusted for crouch. */
    public double arrowStartY() {
        double shoulderY = archerY - (crouched ? 8.0 : 42.0);
        return shoulderY + 2.0;
    }

    public double getArcherX()         { return archerX; }
    public double getArcherY()         { return archerY; }
    public double getAimAngleDegrees() { return aimAngleDegrees; }
    public boolean isCrouched()        { return crouched; }
    public int getScore()              { return score; }

    public PlayerStateDto toDto() {
        return new PlayerStateDto(playerId, nickname, archerX, archerY,
                aimAngleDegrees, crouched, score);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
