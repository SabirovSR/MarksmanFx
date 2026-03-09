package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

public record PlayerInfo(
        String playerId,
        String nickname,
        boolean ready
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
