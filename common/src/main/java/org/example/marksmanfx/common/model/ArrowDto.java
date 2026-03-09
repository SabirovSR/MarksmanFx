package org.example.marksmanfx.common.model;

import java.io.Serial;
import java.io.Serializable;

public record ArrowDto(
        String ownerId,
        boolean active,
        double x,
        double y,
        double angleDegrees,
        double width,
        double height
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
