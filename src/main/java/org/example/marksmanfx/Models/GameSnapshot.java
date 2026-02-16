package org.example.marksmanfx.Models;

public record GameSnapshot(
        boolean running,
        boolean paused,
        int score,
        int shots,
        int level,
        double archerX,
        double archerY,
        double aimAngleDegrees,
        boolean crouched,
        TargetSnapshot nearTarget,
        TargetSnapshot farTarget,
        ArrowSnapshot arrow
) {
}
