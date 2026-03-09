package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

public record PlayerStateDto(
        String playerId,
        String nickname,
        double archerX,
        double archerY,
        double aimAngleDegrees,
        boolean crouched,
        int score
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
