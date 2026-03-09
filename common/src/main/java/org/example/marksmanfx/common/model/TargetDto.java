package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

public record TargetDto(
        double x,
        double y,
        double size,
        int points
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
